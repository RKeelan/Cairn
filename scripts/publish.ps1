# Builds the prod flavour's release bundles of the phone app and the watch app and publishes them to Google Play's
# internal testing tracks, the phone's and Wear OS's, from which the phone and the watch update them. See
# "Publishing" in the README.
#
# Play accepts each version code only once, so each upload needs a greater cairn.version than the last. Every
# version published is tagged play/<version> and the tag pushed to origin, and the script fetches origin's tags
# and refuses, before building anything, a cairn.version that is not above every such tag. It also refuses
# uncommitted changes, so the tag names the commit uploaded; missing properties for Cairn's key, without which
# the builds would be signed with the debug key; and missing cairn.* properties for the hosted Supabase project,
# without which the phone app could not sign in. Untracked files the build never reads are allowed: Markdown
# files at the repository's root, such as TODO.md or HANDOFF.md, and .claude/. Any other untracked file is
# refused, since the build might pick it up.
#
# The service account's JSON key comes from the first of these that is present:
#
# - the file given with -Credentials;
# - the ANDROID_PUBLISHER_CREDENTIALS environment variable;
# - %LOCALAPPDATA%\CairnPublish\play-service-account.dpapi, a copy of the key encrypted with Windows DPAPI for
#   the current user, which the script decrypts into ANDROID_PUBLISHER_CREDENTIALS for the Gradle run alone;
# - 1Password, through `op run`, which puts the key in that variable for Gradle alone.
#
# The DPAPI copy lets the script publish with nobody at the PC to approve a 1Password prompt. -SaveCredentials
# creates or refreshes it from 1Password and publishes nothing. Gradle runs without a daemon, so the key does
# not outlive the build, and the script never writes the decrypted key to disk.

param(
    # A file holding the service account's JSON key.
    [string]$Credentials,
    # Fetch the key from 1Password and save its DPAPI-encrypted copy, instead of publishing.
    [switch]$SaveCredentials
)

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$CredentialsReference = 'op://Private/Google Play Publisher/play-service-account/json'
$ProtectedCredentials = Join-Path $env:LOCALAPPDATA 'CairnPublish\play-service-account.dpapi'

# Resolved before moving to the repository's root, so a relative path is taken from where the script was run.
if ($Credentials) {
    $Credentials = (Resolve-Path -LiteralPath $Credentials).Path
}

function Stop-Publishing([string]$Message) {
    Write-Host "$Message Publishing aborted." -ForegroundColor Red
    exit 1
}

# The version code the build gives a version, major * 1,000,000 + minor * 1,000 + revision, or $null if the
# version is not Major.Minor.Revision.
function Get-VersionCode([string]$Version) {
    if ($Version -notmatch '^(\d+)\.(\d+)\.(\d+)$') { return $null }
    return [int]$Matches[1] * 1000000 + [int]$Matches[2] * 1000 + [int]$Matches[3]
}

if ($SaveCredentials) {
    if (-not (Get-Command op -ErrorAction SilentlyContinue)) {
        Stop-Publishing "The 1Password CLI, op, is not on PATH."
    }
    # Runs under `op run`, in a child PowerShell, so the key exists only in that process's environment.
    $saveKey = {
        $ErrorActionPreference = 'Stop'
        $key = $env:ANDROID_PUBLISHER_CREDENTIALS
        $email = try { ($key | ConvertFrom-Json).client_email } catch { $null }
        if ($email -notmatch '^[^@\s]+@[^@\s]+\.iam\.gserviceaccount\.com$') {
            Write-Host 'The key from 1Password is not a service account''s JSON key. Nothing was saved.' -ForegroundColor Red
            exit 1
        }
        $path = Join-Path $env:LOCALAPPDATA 'CairnPublish\play-service-account.dpapi'
        New-Item -ItemType Directory -Force (Split-Path -Parent $path) | Out-Null
        $key | ConvertTo-SecureString -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $path -NoNewline
        Write-Host "Saved the key for $email to $path, encrypted for this Windows account." -ForegroundColor Green
    }
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($saveKey.ToString()))
    $powershell = (Get-Process -Id $PID).Path
    $env:ANDROID_PUBLISHER_CREDENTIALS = $CredentialsReference
    try {
        & op run -- $powershell -NoProfile -NonInteractive -OutputFormat Text -EncodedCommand $encoded
    } finally {
        Remove-Item Env:ANDROID_PUBLISHER_CREDENTIALS
    }
    exit $LASTEXITCODE
}

Push-Location $RepoRoot
try {
    $versionLine = Get-Content version.properties | Where-Object { $_ -match '^cairn\.version=' } | Select-Object -First 1
    $version = if ($versionLine) { $versionLine.Substring('cairn.version='.Length).Trim() } else { '' }
    $versionCode = Get-VersionCode $version
    if ($null -eq $versionCode) {
        Stop-Publishing "cairn.version in version.properties must be Major.Minor.Revision, not '$version'."
    }

    # The same two places the build looks for the properties of Cairn's key (see "Signing" in the README).
    $keystoreProperties = @('keystore.properties', (Join-Path $HOME '.android\cairn-keystore.properties'))
    if (-not ($keystoreProperties | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })) {
        Stop-Publishing ("Neither keystore.properties nor ~/.android/cairn-keystore.properties exists, so the builds would be " +
            "signed with the debug key, which Play refuses.")
    }

    # Without these the phone app is built against a placeholder address and cannot sign in (see "A hosted Supabase
    # project" in the README).
    $gradleHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $HOME '.gradle' }
    $gradleProperties = Join-Path $gradleHome 'gradle.properties'
    $set = if (Test-Path -LiteralPath $gradleProperties -PathType Leaf) { Get-Content -LiteralPath $gradleProperties } else { @() }
    $missing = @('cairn.supabaseUrl', 'cairn.supabaseKey', 'cairn.googleClientId' | Where-Object {
            $name = [regex]::Escape($_)
            -not ($set | Where-Object { $_ -match "^\s*$name\s*=\s*\S" })
        })
    if ($missing) {
        Stop-Publishing "$gradleProperties does not set $($missing -join ', '), so the phone app could not sign in."
    }

    # Porcelain lines are "XY path"; untracked ones are "?? path", with a directory ending in "/".
    $uncommitted = @(git status --porcelain | Where-Object {
            -not ($_ -match '^\?\? (.+)$' -and ($Matches[1] -eq '.claude/' -or $Matches[1] -match '^[^/]+\.md$'))
        })
    if ($uncommitted) {
        Stop-Publishing ("The working tree has uncommitted changes:`n$($uncommitted -join "`n")`n" +
            "Commit them first, so that the play/$version tag names what was uploaded.")
    }

    # Fetched, so that a version published from another clone counts.
    git fetch --quiet --tags origin
    if ($LASTEXITCODE -ne 0) {
        Stop-Publishing "Could not fetch origin's tags, which say which versions are already on Play."
    }
    $published = @(git tag --list 'play/*' |
            ForEach-Object { $_.Substring('play/'.Length) } |
            Where-Object { $null -ne (Get-VersionCode $_) } |
            Sort-Object { Get-VersionCode $_ } -Descending)
    if ($published.Count -gt 0) {
        $latest = $published[0]
        $latestCode = Get-VersionCode $latest
        if ($versionCode -le $latestCode) {
            Stop-Publishing ("Cairn $latest (version code $latestCode) is already on Play, and Play accepts only a greater version code than it has seen. " +
                "Raise cairn.version in version.properties above $latest (it is $version, version code $versionCode), and commit it.")
        }
    }

    $gradle = Join-Path $RepoRoot 'gradlew.bat'
    $gradleArgs = @(':phone:publishProdReleaseBundle', ':wear:publishProdReleaseBundle', '--no-daemon')
    Write-Host "Publishing Cairn $version (version code $versionCode, $($versionCode + 1000000000) for the watch) to the internal testing tracks..." -ForegroundColor Yellow
    if ($Credentials) {
        & $gradle @gradleArgs "-Pcairn.playCredentials=$Credentials"
    } elseif ($env:ANDROID_PUBLISHER_CREDENTIALS) {
        & $gradle @gradleArgs
    } elseif (Test-Path -LiteralPath $ProtectedCredentials -PathType Leaf) {
        $secure = Get-Content -LiteralPath $ProtectedCredentials -Raw | ConvertTo-SecureString
        $env:ANDROID_PUBLISHER_CREDENTIALS = [System.Net.NetworkCredential]::new('', $secure).Password
        try {
            & $gradle @gradleArgs
        } finally {
            Remove-Item Env:ANDROID_PUBLISHER_CREDENTIALS
        }
    } else {
        if (-not (Get-Command op -ErrorAction SilentlyContinue)) {
            Stop-Publishing "The 1Password CLI, op, is not on PATH. Give the key's file with -Credentials instead."
        }
        $env:ANDROID_PUBLISHER_CREDENTIALS = $CredentialsReference
        try {
            & op run -- $gradle @gradleArgs
        } finally {
            Remove-Item Env:ANDROID_PUBLISHER_CREDENTIALS
        }
    }
    if ($LASTEXITCODE -ne 0) {
        # Each app is uploaded on its own, so one may have reached Play before the other failed, and Play would then
        # refuse that app's version code again.
        Write-Host ("Building or publishing failed (exit code $LASTEXITCODE). Nothing was tagged. If the output shows that one app " +
            "was published, raise cairn.version before trying again.") -ForegroundColor Red
        exit $LASTEXITCODE
    }

    git tag "play/$version"
    git push --quiet origin "play/$version"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Cairn $version is on the internal testing tracks, and HEAD is tagged play/$version, but pushing the tag failed. Push it with: git push origin play/$version" -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "Cairn $version is on the internal testing tracks, and HEAD is tagged play/$version on origin. Play may take a few minutes to offer it to the phone and the watch." -ForegroundColor Green
} finally {
    Pop-Location
}

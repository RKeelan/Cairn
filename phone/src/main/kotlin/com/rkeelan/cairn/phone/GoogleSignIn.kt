package com.rkeelan.cairn.phone

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.security.SecureRandom

/** An ID token from Google and the nonce it was asked for with, which the server checks the token against. */
class GoogleToken(val idToken: String, val nonce: String)

/** Google gave no token. [cancelled] is true when that was the user's doing, which needs no message. */
class GoogleSignInException(val cancelled: Boolean, cause: Throwable? = null) : Exception("Sign-in with Google did not finish", cause)

/**
 * Asks the user to choose a Google account, through Credential Manager, and returns a token for it. [activity] is the activity on screen, and
 * [clientId] is the Google Cloud project's web client, the one the Supabase project's Google provider is set up with.
 */
suspend fun googleToken(activity: Context, clientId: String): GoogleToken {
    val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes).toHex()
    // Google puts the nonce it is given in the token as it is. The server hashes the one it is given and compares, so Google gets the hash.
    val hashed = MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray()).toHex()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).setNonce(hashed).build())
        .build()
    val credential = try {
        CredentialManager.create(activity).getCredential(activity, request).credential
    } catch (e: GetCredentialCancellationException) {
        throw GoogleSignInException(cancelled = true, e)
    } catch (e: NoCredentialException) {
        // The phone has no Google account to offer.
        throw GoogleSignInException(cancelled = false, e)
    } catch (e: GetCredentialException) {
        throw GoogleSignInException(cancelled = false, e)
    }
    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        throw GoogleSignInException(cancelled = false)
    }
    return try {
        GoogleToken(GoogleIdTokenCredential.createFrom(credential.data).idToken, nonce)
    } catch (e: GoogleIdTokenParsingException) {
        throw GoogleSignInException(cancelled = false, e)
    }
}

private const val NONCE_BYTES = 32

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

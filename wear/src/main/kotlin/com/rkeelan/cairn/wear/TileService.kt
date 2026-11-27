package com.rkeelan.cairn.wear

import android.util.Log
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import com.rkeelan.cairn.link.Link
import com.rkeelan.cairn.link.WatchList
import com.rkeelan.cairn.wear.ui.TileColours
import com.rkeelan.cairn.wear.ui.tileLayout
import com.rkeelan.cairn.wear.ui.tileLines
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Serves Cairn's tile (see ui/Tile.kt) in Cairn's own colours rather than the system's. Each time the watch asks for the tile, it reads the list
 * as the phone last sent it; ListService asks the watch to do so when the list changes.
 */
class TileService : Material3TileService(allowDynamicTheme = false, defaultColorScheme = TileColours) {
    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val content = TileContent.of(readList(), tileLines())
        return Tile.Builder().setTileTimeline(Timeline.fromLayoutElement(tileLayout(content))).build()
    }

    private suspend fun readList(): WatchList? = try {
        withTimeoutOrNull(READ_TIMEOUT_MILLIS) { Link.list(this@TileService).first() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the list for the tile", e)
        null
    }

    private companion object {
        const val TAG = "Cairn"

        // The Data Layer answers from the watch's own store, so a read that takes longer than this has gone wrong.
        const val READ_TIMEOUT_MILLIS = 5_000L
    }
}

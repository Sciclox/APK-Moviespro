package com.animetv.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TvWatchNextManager {

    suspend fun updateWatchNext(
        context: Context,
        animeId: String,
        animeSlug: String,
        animeTitle: String,
        animeImage: String,
        episodeNumber: Int,
        episodeTitle: String,
        lastPositionMs: Long,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        try {
            if (durationMs <= 0 || lastPositionMs < 3_000) return@withContext

            val isCompleted = lastPositionMs >= (durationMs * 0.95f)

            // Query existing WatchNextProgram for this anime/episode
            val existingProgramId = findExistingProgramId(context, animeSlug)

            if (isCompleted) {
                // If episode is 95%+ finished, remove from Watch Next row
                if (existingProgramId != -1L) {
                    context.contentResolver.delete(
                        TvContractCompat.buildWatchNextProgramUri(existingProgramId),
                        null,
                        null
                    )
                }
                return@withContext
            }

            val deepLinkUri = Uri.parse("animetv://play?slug=$animeSlug&ep=$episodeNumber")
            val posterUri = if (animeImage.isNotBlank()) Uri.parse(animeImage) else null

            val builder = WatchNextProgram.Builder()
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setType(TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE)
                .setTitle(animeTitle)
                .setEpisodeTitle(episodeTitle.ifBlank { "Episodio $episodeNumber" })
                .setEpisodeNumber(episodeNumber)
                .setLastPlaybackPositionMillis(lastPositionMs.toInt())
                .setDurationMillis(durationMs.toInt())
                .setIntentUri(deepLinkUri)
                .setInternalProviderId(animeSlug)

            if (posterUri != null) {
                builder.setPosterArtUri(posterUri)
            }

            val program = builder.build()

            if (existingProgramId != -1L) {
                // Update existing Watch Next program entry
                context.contentResolver.update(
                    TvContractCompat.buildWatchNextProgramUri(existingProgramId),
                    program.toContentValues(),
                    null,
                    null
                )
            } else {
                // Insert new Watch Next program entry
                context.contentResolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    program.toContentValues()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findExistingProgramId(context: Context, animeSlug: String): Long {
        return try {
            val cursor = context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(TvContractCompat.WatchNextPrograms._ID, TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
                null,
                null,
                null
            )
            cursor?.use {
                val idIndex = it.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
                val providerIdIndex = it.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
                while (it.moveToNext()) {
                    val pId = if (providerIdIndex != -1) it.getString(providerIdIndex) else null
                    if (pId == animeSlug) {
                        return if (idIndex != -1) it.getLong(idIndex) else -1L
                    }
                }
            }
            -1L
        } catch (e: Exception) {
            -1L
        }
    }
}

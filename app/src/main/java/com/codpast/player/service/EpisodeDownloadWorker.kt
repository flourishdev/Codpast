package com.codpast.player.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.codpast.player.data.local.dao.PodcastDao
import com.codpast.player.data.local.entity.DownloadEntity
import com.codpast.player.data.local.entity.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class EpisodeDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val podcastDao: PodcastDao,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return@withContext Result.failure()
        val podcastId = inputData.getString(KEY_PODCAST_ID) ?: ""
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: return@withContext Result.failure()

        val downloadsDir = File(context.filesDir, "downloads").apply {
            if (!exists()) mkdirs()
        }
        val targetFile = File(downloadsDir, "${episodeId.hashCode()}.mp3")

        val downloadRecord = DownloadEntity(
            episodeId = episodeId,
            podcastId = podcastId,
            localPath = targetFile.absolutePath,
            progress = 0,
            status = DownloadStatus.DOWNLOADING
        )
        podcastDao.insertOrUpdateDownload(downloadRecord)

        try {
            val request = Request.Builder().url(audioUrl).build()
            val response = okHttpClient.newCall(request).execute()

            val responseBody = response.body
            if (!response.isSuccessful || responseBody == null) {
                podcastDao.insertOrUpdateDownload(
                    downloadRecord.copy(status = DownloadStatus.FAILED)
                )
                return@withContext Result.failure()
            }

            val contentLength = responseBody.contentLength()
            var bytesRead = 0L

            responseBody.byteStream().use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var lastReportedProgress = 0

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesRead += read

                        if (contentLength > 0) {
                            val currentProgress = ((bytesRead * 100) / contentLength).toInt()
                            if (currentProgress >= lastReportedProgress + 5) {
                                lastReportedProgress = currentProgress
                                podcastDao.insertOrUpdateDownload(
                                    downloadRecord.copy(progress = currentProgress)
                                )
                            }
                        }
                    }
                }
            }

            podcastDao.insertOrUpdateDownload(
                downloadRecord.copy(
                    progress = 100,
                    status = DownloadStatus.COMPLETED
                )
            )
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            if (targetFile.exists()) {
                targetFile.delete()
            }
            podcastDao.insertOrUpdateDownload(
                downloadRecord.copy(status = DownloadStatus.FAILED)
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "key_episode_id"
        const val KEY_PODCAST_ID = "key_podcast_id"
        const val KEY_AUDIO_URL = "key_audio_url"
    }
}
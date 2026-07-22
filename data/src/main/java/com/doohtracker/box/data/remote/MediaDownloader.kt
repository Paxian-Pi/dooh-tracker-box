package com.doohtracker.box.data.remote

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }

    suspend fun downloadMedia(url: String, id: String): File? = withContext(Dispatchers.IO) {
        val extension = getExtension(url)
        val destinationFile = File(mediaDir, "$id$extension")
        val tempFile = File(mediaDir, "$id$extension.tmp")
        
        if (destinationFile.exists()) {
            return@withContext destinationFile
        }

        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.renameTo(destinationFile)) {
                    destinationFile
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            null
        }
    }

    private fun getExtension(url: String): String {
        return when {
            url.contains(".mp4", true) -> ".mp4"
            url.contains(".jpg", true) -> ".jpg"
            url.contains(".jpeg", true) -> ".jpeg"
            url.contains(".png", true) -> ".png"
            url.contains(".webp", true) -> ".webp"
            else -> ""
        }
    }
}

package com.doohtracker.box.data.remote

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }

    // Dedicated OkHttpClient for media downloads with NO read/write timeouts.
    // Timeouts are disabled so long-running downloads won't be cut off.
    // The downloader implements its own retry/resume logic.
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(0, TimeUnit.MILLISECONDS) // 0 = no timeout
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun downloadMedia(url: String, suggestedFileName: String): File? = withContext(Dispatchers.IO) {
        Log.d("Download", "Starting resilient download: $url")
        val extension = getExtension(url)

        val fileName = if (suggestedFileName.contains(".")) suggestedFileName else "$suggestedFileName$extension"
        val destinationFile = File(mediaDir, fileName)
        val tempFile = File(mediaDir, "$fileName.tmp")

        // If destination already exists and non-empty, return it
        if (destinationFile.exists() && destinationFile.length() > 0) {
            Log.d("Download", "File already exists: ${destinationFile.name} (${destinationFile.length()} bytes)")
            return@withContext destinationFile
        }

        var attempt = 0
        var backoff = 1000L
        var result: File? = null

        outer@ while (true) {
            try {
                // If temp file exists, try to resume using Range header
                val existing = if (tempFile.exists()) tempFile.length() else 0L
                val builder = Request.Builder().url(url)
                if (existing > 0) {
                    builder.header("Range", "bytes=$existing-")
                    Log.d("Download", "Attempting resume from byte $existing for $fileName")
                }
                val request = builder.build()

                val response: Response = downloadClient.newCall(request).execute()

                response.use { resp ->
                    if (!resp.isSuccessful && resp.code != 206) {
                        // If server returned an error (and not partial content), throw
                        throw IOException("Server returned HTTP ${resp.code}")
                    }

                    val contentRange = resp.header("Content-Range") // e.g. bytes START-END/TOTAL
                    val contentLengthHeader = resp.header("Content-Length")
                    val returnedLength = contentLengthHeader?.toLongOrNull() ?: -1L

                    // Determine if server supports range
                    val supportsRange = (resp.code == 206) || (contentRange != null)

                    // If server returned 200 OK but we had existing bytes, server didn't support Range -> delete and restart
                    if (existing > 0 && resp.code == 200) {
                        Log.w("Download", "Server does not support Range. Restarting full download for $fileName")
                        tempFile.delete()
                    }

                    // Open RandomAccessFile so we can append if resuming
                    RandomAccessFile(tempFile, "rw").use { raf ->
                        if (existing > 0 && supportsRange) {
                            raf.seek(existing)
                        } else {
                            raf.setLength(0)
                        }

                        val input = resp.body?.byteStream() ?: throw IOException("Empty response body")
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead = if (existing > 0 && supportsRange) existing else 0L

                        while (true) {
                            bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            raf.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                        }
                        try {
                            raf.fd.sync()
                        } catch (_: Exception) {}
                    }

                    // Calculate expected total size when possible
                    val expectedTotal: Long? = when {
                        contentRange != null -> {
                            // Parse "bytes START-END/TOTAL"
                            try {
                                val parts = contentRange.split("/")
                                val totalPart = parts.getOrNull(1)
                                totalPart?.toLongOrNull()
                            } catch (_: Exception) { null }
                        }
                        returnedLength > 0L -> {
                            // If we resumed, returnedLength is remaining; add existing
                            val existingNow = tempFile.length()
                            if (resp.code == 206) existingNow + returnedLength else returnedLength
                        }
                        else -> null
                    }

                    val downloadedSize = tempFile.length()

                    if (expectedTotal != null) {
                        if (downloadedSize >= expectedTotal) {
                            // Completed
                            if (tempFile.renameTo(destinationFile)) {
                                Log.d("Download", "SUCCESS: ${destinationFile.name} downloaded (${destinationFile.length()} bytes)")
                                result = destinationFile
                                return@use
                            } else {
                                Log.e("Download", "FAILED to rename temp to destination for ${destinationFile.name}")
                                tempFile.delete()
                                result = null
                                return@use
                            }
                        } else {
                            // Not complete yet; continue loop to retry/resume
                            Log.w("Download", "Partial download for ${fileName}: $downloadedSize/${expectedTotal} bytes. Will retry/resume.")
                            attempt = 0
                            backoff = 1000L
                            // Fall through to retry after short delay
                        }
                    } else {
                        // If server didn't provide total size, use heuristic: if last read filled buffer etc. but best to assume success when connection closed
                        // If response is exhausted and we have some bytes, consider it complete
                        val finalSize = tempFile.length()
                        if (finalSize > 0) {
                            if (tempFile.renameTo(destinationFile)) {
                                Log.d("Download", "SUCCESS (no total reported): ${destinationFile.name} downloaded (${destinationFile.length()} bytes)")
                                result = destinationFile
                                return@use
                            } else {
                                Log.e("Download", "FAILED to rename temp to destination (no total reported) for ${destinationFile.name}")
                                tempFile.delete()
                                result = null
                                return@use
                            }
                        } else {
                            Log.w("Download", "No data downloaded yet for $fileName. Will retry.")
                        }
                    }
                }

                // If response.use set result (download completed), break the outer loop
                if (result != null) {
                    break@outer
                }

            } catch (e: IOException) {
                attempt++
                Log.w("Download", "IOException during download attempt #$attempt for $fileName: ${e.message}. Retrying in ${backoff}ms", e)
                // Wait before retrying; allow coroutine cancellation
                delay(backoff)
                if (backoff < 60_000L) backoff = (backoff * 2).coerceAtMost(60_000L)
                continue
            } catch (e: Exception) {
                Log.e("Download", "Unexpected error during download for $fileName: ${e.message}", e)
                if (tempFile.exists()) tempFile.delete()
                result = null
                break@outer
            }

            // If we reach here without returning, loop to retry
        }

        return@withContext result
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

    fun clearMedia() {
        try {
            mediaDir.listFiles()?.forEach { it.delete() }
            Log.d("Download", "Media cache cleared")
        } catch (e: Exception) {
            Log.e("Download", "Error clearing media", e)
        }
    }
}

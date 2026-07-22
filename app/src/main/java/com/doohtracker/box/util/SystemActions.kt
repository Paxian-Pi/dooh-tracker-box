package com.doohtracker.box.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemActions @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    fun saveCsvFile(fileName: String, content: String) {
        try {
            val file = if (publicDownloadDir.exists() || publicDownloadDir.mkdirs()) {
                File(publicDownloadDir, fileName)
            } else {
                File(context.getExternalFilesDir(null), fileName)
            }
            
            FileWriter(file).use { writer ->
                writer.write(content)
            }
            Log.d("SystemActions", "CSV saved to: ${file.absolutePath}")
            showToast("CSV Exported to Downloads")
        } catch (e: Exception) {
            Log.e("SystemActions", "Error saving CSV", e)
            showToast("Failed to export CSV")
        }
    }

    fun savePdfFile(fileName: String, reportTitle: String, sections: List<PdfSection>) {
        val pdfDocument = PdfDocument()
        val titlePaint = Paint().apply {
            textSize = 18f
            isFakeBoldText = true
        }
        val sectionPaint = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
            color = android.graphics.Color.BLUE
        }
        val headerPaint = Paint().apply {
            textSize = 12f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            textSize = 10f
        }

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var y = 40f

        canvas.drawText(reportTitle, 40f, y, titlePaint)
        y += 40f

        sections.forEach { section ->
            if (y > 750f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 40f
            }

            canvas.drawText(section.title, 40f, y, sectionPaint)
            y += 25f

            var x = 40f
            section.headers.forEachIndexed { index, header ->
                canvas.drawText(header, x, y, headerPaint)
                x += if (index == 1) 150f else 90f // Give Title more space
            }
            y += 20f

            section.rows.forEach { row ->
                if (y > 800f) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 40f
                }
                x = 40f
                row.forEachIndexed { index, cell ->
                    canvas.drawText(cell, x, y, textPaint)
                    x += if (index == 1) 150f else 90f
                }
                y += 15f
            }
            y += 20f 
        }

        pdfDocument.finishPage(page)

        try {
            val file = if (publicDownloadDir.exists() || publicDownloadDir.mkdirs()) {
                File(publicDownloadDir, fileName)
            } else {
                File(context.getExternalFilesDir(null), fileName)
            }
            
            pdfDocument.writeTo(FileOutputStream(file))
            Log.d("SystemActions", "PDF saved to: ${file.absolutePath}")
            showToast("PDF Exported to Downloads")
        } catch (e: Exception) {
            Log.e("SystemActions", "Error saving PDF", e)
            showToast("Failed to export PDF")
        } finally {
            pdfDocument.close()
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun rebootDevice() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.reboot(null)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                Runtime.getRuntime().exec("reboot")
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun openLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openDateTimeSettings() {
        try {
            val intent = Intent(Settings.ACTION_DATE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class PdfSection(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

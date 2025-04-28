package com.example.webviewdownloader

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import java.io.File

class DownloadManagerHelper(private val context: Context) {

    private val downloadManager: DownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun downloadFile(url: String, userAgent: String, contentDisposition: String, mimeType: String): Long {
        // Get filename from URL
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        
        // Create download request
        val request = DownloadManager.Request(Uri.parse(url))
        
        // Set description and notification visibility
        request.setDescription("Downloading file...")
        request.setTitle(fileName)
        
        // Set notification visibility
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        
        // Set destination
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        
        // Add cookies if needed
        val cookies = CookieManager.getInstance().getCookie(url)
        cookies?.let {
            request.addRequestHeader("cookie", it)
        }
        request.addRequestHeader("User-Agent", userAgent)
        
        // Enqueue the request and return the download ID
        return downloadManager.enqueue(request)
    }

    fun getDownloadStatus(downloadId: Long): Int {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)
            cursor.close()
            return status
        }
        cursor.close()
        return DownloadManager.STATUS_FAILED
    }

    fun getDownloadProgress(downloadId: Long): Int {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        if (cursor.moveToFirst()) {
            val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            
            val downloaded = cursor.getLong(downloadedIndex)
            val total = cursor.getLong(totalIndex)
            
            cursor.close()
            
            if (total > 0) {
                return ((downloaded * 100) / total).toInt()
            }
        }
        cursor.close()
        return 0
    }

    fun getDownloadedFilePath(downloadId: Long): String? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        if (cursor.moveToFirst()) {
            val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val localUri = cursor.getString(localUriIndex)
            cursor.close()
            
            if (localUri != null) {
                return Uri.parse(localUri).path
            }
        }
        cursor.close()
        return null
    }

    fun cancelDownload(downloadId: Long): Boolean {
        return downloadManager.remove(downloadId) > 0
    }
}

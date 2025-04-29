package com.example.webviewdownloader

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private val STORAGE_PERMISSION_CODE = 1001
    private var downloadID: Long = 0
    private val TAG = "MainActivity"
    
    // Store pending download information
    private var pendingDownload: PendingDownload? = null
    
    // Permission launcher using the new Activity Result API
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Storage permission granted")
            Toast.makeText(this, "Storage Permission Granted", Toast.LENGTH_SHORT).show()
            // Process any pending download
            pendingDownload?.let { download ->
                downloadFile(download.url, download.userAgent, download.contentDisposition, download.mimeType)
                pendingDownload = null
            }
        } else {
            Log.d(TAG, "Storage permission denied")
            Toast.makeText(this, "Storage Permission Denied. Cannot download files.", Toast.LENGTH_LONG).show()
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // User selected "Don't ask again" - show dialog to open settings
                showSettingsDialog()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        setupWebView()
        
        // Register broadcast receiver for download completion
        registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
    
    private fun setupWebView() {
        // Enable JavaScript
        webView.settings.javaScriptEnabled = true
        
        // Enable DOM storage
        webView.settings.domStorageEnabled = true
        
        // Allow mixed content (if your site uses http)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        // Additional WebView settings for better compatibility
        webView.settings.allowFileAccess = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        
        // Set WebViewClient to handle page navigation
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page loaded: $url")
            }
        }
        
        // Set WebChromeClient to handle progress
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                Log.d(TAG, "Loading progress: $newProgress%")
            }
        }
        
        // Handle downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            Log.d(TAG, "Download requested: $url, mimetype: $mimetype")
            
            // Check if the file is a PNG
            if (mimetype == "image/png" || url.endsWith(".png", ignoreCase = true)) {
                // Check and request permission before downloading
                if (checkAndRequestStoragePermission()) {
                    downloadFile(url, userAgent, contentDisposition, mimetype)
                } else {
                    // Save download info for later when permission is granted
                    pendingDownload = PendingDownload(url, userAgent, contentDisposition, mimetype)
                }
            } else {
                Toast.makeText(this@MainActivity, "Only PNG files can be downloaded", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Load the PHP webpage
        webView.loadUrl("https://media.sekol.my.id/login.php")
    }
    
    private fun checkAndRequestStoragePermission(): Boolean {
        Log.d(TAG, "Checking storage permission")
        
        // For Android 10 (API 29) and below, we need WRITE_EXTERNAL_STORAGE permission
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                
                Log.d(TAG, "Permission not granted, requesting...")
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return false
            }
        }
        
        // Permission already granted or not needed (Android 11+)
        Log.d(TAG, "Permission already granted or not needed")
        return true
    }
    
    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Storage permission is required to download files. Please enable it in app settings.")
            .setPositiveButton("Settings") { _, _ ->
                // Open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun downloadFile(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        Log.d(TAG, "Starting download: $url")
        
        try {
            // Get filename from URL
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            
            // Create download request
            val request = DownloadManager.Request(Uri.parse(url))
            
            // Set description and notification visibility
            request.setDescription("Downloading file...")
            request.setTitle(fileName)
            
            // Set notification visibility
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            // Set destination based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10 and above
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            } else {
                // For Android 9 and below
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            
            // Add cookies if needed
            val cookies = CookieManager.getInstance().getCookie(url)
            cookies?.let {
                request.addRequestHeader("cookie", it)
            }
            request.addRequestHeader("User-Agent", userAgent)
            
            // Get download service and enqueue the request
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadID = downloadManager.enqueue(request)
            
            Toast.makeText(this, "Downloading $fileName", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Download enqueued with ID: $downloadID")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting download", e)
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadID) {
                // Check download status
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadID)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)
                    
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            Toast.makeText(context, "Download completed successfully", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Download completed successfully")
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = cursor.getInt(reasonIndex)
                            Toast.makeText(context, "Download failed: $reason", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Download failed with reason: $reason")
                        }
                    }
                }
                cursor.close()
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage Permission Granted", Toast.LENGTH_SHORT).show()
                // Process any pending download
                pendingDownload?.let { download ->
                    downloadFile(download.url, download.userAgent, download.contentDisposition, download.mimeType)
                    pendingDownload = null
                }
            } else {
                Toast.makeText(this, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Unregister broadcast receiver
        unregisterReceiver(onDownloadComplete)
    }
    
    // Data class to store pending download information
    data class PendingDownload(
        val url: String,
        val userAgent: String,
        val contentDisposition: String,
        val mimeType: String
    )
}

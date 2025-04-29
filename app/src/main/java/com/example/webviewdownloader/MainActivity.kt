package com.example.webviewdownloader

import android.Manifest
import android.app.Activity
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
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
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
                showDownloadLocationDialog(download.url, download.userAgent, download.contentDisposition, download.mimeType)
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
    
    // Create document launcher
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                pendingDownload?.let { download ->
                    // Download the file to the selected location
                    downloadToUri(download.url, uri, download.mimeType)
                    pendingDownload = null
                }
            }
        } else {
            Toast.makeText(this, "Folder selection cancelled", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Folder selection launcher
    private val folderSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Save the URI permission for future use
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                
                // Store this URI in preferences as the default download location
                getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
                    .putString("download_location", uri.toString())
                    .apply()
                
                pendingDownload?.let { download ->
                    // Get a suitable filename
                    val fileName = URLUtil.guessFileName(download.url, download.contentDisposition, download.mimeType)
                    
                    // Create the file in the selected directory
                    val documentFile = DocumentFile.fromTreeUri(this, uri)
                    documentFile?.let { dir ->
                        downloadToFolder(download.url, dir, fileName, download.mimeType)
                        pendingDownload = null
                    }
                }
            }
        } else {
            Toast.makeText(this, "Folder selection cancelled", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        
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
        
        // Set WebViewClient to handle page navigation with loading indicator
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                progressBar.visibility = View.VISIBLE
                return false
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                Log.d(TAG, "Page loaded: $url")
            }
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                progressBar.visibility = View.GONE
                Log.e(TAG, "WebView error: ${error?.description}")
            }
        }
        
        // Set WebChromeClient to handle progress
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress < 100 && progressBar.visibility == View.GONE) {
                    progressBar.visibility = View.VISIBLE
                }
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                }
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
                    showDownloadLocationDialog(url, userAgent, contentDisposition, mimetype)
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
    
    private fun showDownloadLocationDialog(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        
        val options = arrayOf("Downloads (default)", "Choose folder", "Specific location")
        
        AlertDialog.Builder(this)
            .setTitle("Save $fileName to:")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> downloadFile(url, userAgent, contentDisposition, mimeType)
                    1 -> openFolderPicker(url, userAgent, contentDisposition, mimeType)
                    2 -> openFilePicker(url, userAgent, contentDisposition, mimeType)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun openFolderPicker(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        pendingDownload = PendingDownload(url, userAgent, contentDisposition, mimeType)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            folderSelectionLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Folder selection not supported on this device", Toast.LENGTH_SHORT).show()
            downloadFile(url, userAgent, contentDisposition, mimeType)
        }
    }
    
    private fun openFilePicker(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        pendingDownload = PendingDownload(url, userAgent, contentDisposition, mimeType)
        
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        
        createDocumentLauncher.launch(intent)
    }
    
    private fun downloadToFolder(url: String, folder: DocumentFile, fileName: String, mimeType: String) {
        // Create a file in the selected folder
        val newFile = folder.createFile(mimeType, fileName)
        
        if (newFile != null) {
            val outputUri = newFile.uri
            downloadToUri(url, outputUri, mimeType)
        } else {
            Toast.makeText(this, "Could not create file in the selected folder", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun downloadToUri(url: String, uri: Uri, mimeType: String) {
        Toast.makeText(this, "Downloading to selected location...", Toast.LENGTH_SHORT).show()
        
        // Download in background
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connect()
                
                val input: InputStream = connection.getInputStream()
                val output: OutputStream = contentResolver.openOutputStream(uri)!!
                
                val buffer = ByteArray(1024)
                var read: Int
                
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                
                output.flush()
                output.close()
                input.close()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Download complete", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading file", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            
            Toast.makeText(this, "Downloading $fileName to Downloads folder", Toast.LENGTH_SHORT).show()
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
                            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val downloadedUriString = cursor.getString(uriIndex)
                            
                            Toast.makeText(context, "Download completed successfully", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Download completed successfully to: $downloadedUriString")
                            
                            // Notify media scanner to add the file to gallery
                            if (downloadedUriString != null) {
                                try {
                                    val downloadedUri = Uri.parse(downloadedUriString)
                                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                                    mediaScanIntent.data = downloadedUri
                                    context?.sendBroadcast(mediaScanIntent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error scanning file: ${e.message}")
                                }
                            }
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
                    showDownloadLocationDialog(download.url, download.userAgent, download.contentDisposition, download.mimeType)
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

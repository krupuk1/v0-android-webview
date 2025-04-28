package com.example.webviewdownloader

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private val STORAGE_PERMISSION_CODE = 1001
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        setupWebView()
        
        // Check for storage permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkStoragePermission()
        }
    }
    
    private fun setupWebView() {
        // Enable JavaScript
        webView.settings.javaScriptEnabled = true
        
        // Set WebViewClient to handle page navigation
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
        
        // Set WebChromeClient to handle progress
        webView.webChromeClient = WebChromeClient()
        
        // Handle downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            // Check if the file is a PNG
            if (mimetype == "image/png" || url.endsWith(".png", ignoreCase = true)) {
                downloadFile(url, userAgent, contentDisposition, mimetype)
            } else {
                Toast.makeText(this@MainActivity, "Only PNG files can be downloaded", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Load the PHP webpage
        webView.loadUrl("https://your-php-website.com")
    }
    
    private fun downloadFile(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        // Check storage permission before downloading
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_CODE
                )
                return
            }
        }
        
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
        request.addRequestHeader("cookie", cookies)
        request.addRequestHeader("User-Agent", userAgent)
        
        // Get download service and enqueue the request
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        
        Toast.makeText(this, "Downloading $fileName", Toast.LENGTH_SHORT).show()
    }
    
    private fun checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
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
}

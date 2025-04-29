package com.example.webviewdownloader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private val TAG = "MainActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        
        setupWebView()
    }
    
    private fun setupWebView() {
        // Enable JavaScript
        webView.settings.javaScriptEnabled = true
        
        // Enable DOM storage
        webView.settings.domStorageEnabled = true
        
        // Allow mixed content (if your site uses http)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
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
        
        // Handle downloads by opening Chrome
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            Log.d(TAG, "Download requested: $url, mimetype: $mimetype")
            
            // Check if the file is a PNG
            if (mimetype == "image/png" || url.endsWith(".png", ignoreCase = true)) {
                // Extract the original filename from the URL or content disposition
                val originalFilename = getOriginalFilename(url, contentDisposition)
                Log.d(TAG, "Original filename: $originalFilename")
                
                openInChrome(url, originalFilename)
            } else {
                Toast.makeText(this@MainActivity, "Only PNG files can be downloaded", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Load the PHP webpage
        webView.loadUrl("https://media.sekol.my.id/login.php")
    }
    
    private fun getOriginalFilename(url: String, contentDisposition: String?): String {
        // First try to get filename from content disposition
        var filename = URLUtil.guessFileName(url, contentDisposition, "image/png")
        
        // If that doesn't work well, try to extract from URL
        if (filename.isNullOrEmpty() || filename == "download.png") {
            // Extract filename from URL path
            try {
                val uri = Uri.parse(url)
                val path = uri.path
                if (path != null) {
                    val lastSlash = path.lastIndexOf('/')
                    if (lastSlash != -1 && lastSlash < path.length - 1) {
                        val filenameFromPath = path.substring(lastSlash + 1)
                        if (filenameFromPath.isNotEmpty()) {
                            filename = filenameFromPath
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting filename from URL", e)
            }
        }
        
        // If we still don't have a good filename, try to parse content disposition manually
        if (filename.isNullOrEmpty() || filename == "download.png") {
            contentDisposition?.let {
                val filenamePattern = "filename=\"([^\"]*)\""
                val regex = Regex(filenamePattern)
                val matchResult = regex.find(contentDisposition)
                matchResult?.groupValues?.getOrNull(1)?.let { extractedName ->
                    if (extractedName.isNotEmpty()) {
                        filename = extractedName
                    }
                }
            }
        }
        
        // Log the extracted filename
        Log.d(TAG, "Extracted filename: $filename from URL: $url")
        
        return filename
    }
    
    private fun openInChrome(url: String, filename: String) {
        try {
            // Create an intent to open the URL in Chrome
            val intent = Intent(Intent.ACTION_VIEW)
            
            // Set the data and type
            intent.setDataAndType(Uri.parse(url), "image/png")
            
            // Add extra to suggest the filename
            intent.putExtra(Intent.EXTRA_TITLE, filename)
            
            // Try to set Chrome as the browser to handle this
            intent.setPackage("com.android.chrome")
            
            // If Chrome is not available, fall back to any browser
            if (intent.resolveActivity(packageManager) == null) {
                intent.setPackage(null)
            }
            
            // Add flags to suggest download rather than viewing
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            startActivity(intent)
            Toast.makeText(this, "Opening download in browser: $filename", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Chrome", e)
            Toast.makeText(this, "Could not open browser: ${e.message}", Toast.LENGTH_LONG).show()
            
            // Fallback to any browser if there was an error
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error opening fallback browser", e2)
                Toast.makeText(this, "Could not open any browser", Toast.LENGTH_LONG).show()
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

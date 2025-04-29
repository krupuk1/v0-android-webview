package com.example.webviewdownloader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

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
                openInChrome(url)
            } else {
                Toast.makeText(this@MainActivity, "Only PNG files can be downloaded", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Load the PHP webpage
        webView.loadUrl("https://media.sekol.my.id/login.php")
    }
    
    private fun openInChrome(url: String) {
        try {
            // Create an intent to open the URL in Chrome
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            
            // Try to set Chrome as the browser to handle this
            intent.setPackage("com.android.chrome")
            
            // If Chrome is not available, fall back to any browser
            if (intent.resolveActivity(packageManager) == null) {
                intent.setPackage(null)
            }
            
            startActivity(intent)
            Toast.makeText(this, "Opening download in browser", Toast.LENGTH_SHORT).show()
            
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

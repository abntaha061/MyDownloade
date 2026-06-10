package com.example.presentation.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.model.VideoFormat
import com.example.data.model.VideoInfo
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val detectedVideo by viewModel.detectedVideo.collectAsStateWithLifecycle()
    val isAdBlockEnabled by viewModel.isAdBlockEnabled.collectAsStateWithLifecycle()

    var urlInput by remember { mutableStateOf(currentUrl) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableStateOf(0L) }
    var showBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(detectedVideo) {
         if (detectedVideo == null) {
              showBottomSheet = false
         }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000) {
                (context as? android.app.Activity)?.finish()
            } else {
                lastBackPressTime = now
                android.widget.Toast.makeText(
                    context,
                    "اضغط مرة أخرى للخروج / Press back again to exit",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Keep urlInput in sync with ViewModel's state when a page loads
    LaunchedEffect(currentUrl) {
        if (currentUrl != urlInput) {
            urlInput = currentUrl
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- URL bar and Browser controls ---
        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        placeholder = { Text("Search or enter web address", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(26.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Web",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { urlInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear Title"
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                val target = sanitizeUrl(urlInput)
                                viewModel.updateUrl(target)
                                webViewInstance?.loadUrl(target)
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            val target = sanitizeUrl(urlInput)
                            viewModel.updateUrl(target)
                            webViewInstance?.loadUrl(target)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Navigate Address",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Navigation Controls Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        enabled = webViewInstance?.canGoBack() == true
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go Back")
                    }

                    IconButton(
                        onClick = { webViewInstance?.goForward() },
                        enabled = webViewInstance?.canGoForward() == true
                    ) {
                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Go Forward")
                    }

                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Page")
                    }

                    IconButton(onClick = {
                        val homeUrl = "https://google.com"
                        viewModel.updateUrl(homeUrl)
                        webViewInstance?.loadUrl(homeUrl)
                    }) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "Go Home")
                    }

                    IconButton(onClick = {
                        viewModel.toggleAdBlock()
                        android.widget.Toast.makeText(
                            context,
                            if (!isAdBlockEnabled) "تم تفعيل مانع الإعلانات 🛡️\nAd block activated" else "تم إيقاف مانع الإعلانات ⚠️\nAd block deactivated",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Toggle Ad Block",
                            tint = if (isAdBlockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // --- Integrated WebView Container ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            WebViewCompose(
                url = currentUrl,
                isAdBlockEnabled = isAdBlockEnabled,
                onPageProgressChanged = { _, progress ->
                    viewModel.setLoading(progress < 100)
                },
                onUrlChanged = { newUrl ->
                    viewModel.updateUrl(newUrl)
                },
                onVideoDetected = { detectedUrl ->
                    viewModel.onVideoLinkDetected(detectedUrl)
                },
                onCreated = { webView ->
                    webViewInstance = webView
                }
            )

            // Floating Video Detection Badge Button (always on the right side of the screen)
            if (detectedVideo != null) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 24.dp, end = 24.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        FloatingActionButton(
                            onClick = { showBottomSheet = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Show Detected Video",
                                    modifier = Modifier.size(28.dp)
                                )
                                // A small pulsing badge count
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(12.dp)
                                        .background(Color.Red, shape = androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Elegant Download Settings Dialog Sheet ---
    if (showBottomSheet && detectedVideo != null) {
        DownloadBottomSheetDialog(
            videoInfo = detectedVideo!!,
            onDismiss = { showBottomSheet = false },
            onConfirmDownload = { format, customTitle ->
                viewModel.startDownload(format, customTitle)
                showBottomSheet = false
            }
        )
    }
}

/**
 * Native WebView Composable with Sniffing Hooks and JS Injection
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewCompose(
    url: String,
    isAdBlockEnabled: Boolean,
    onPageProgressChanged: (WebView, Int) -> Unit,
    onUrlChanged: (String) -> Unit,
    onVideoDetected: (String) -> Unit,
    onCreated: (WebView) -> Unit
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                // Store AdBlock status in tag for dynamic real-time retrieval
                tag = isAdBlockEnabled

                // Configure browser settings
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }

                // Add Javascript interface to intercept elements custom JS
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onVideoFound(videoUrl: String, title: String) {
                            post {
                                onVideoDetected(videoUrl)
                            }
                        }
                    },
                    "IDM_JS_INTERFACE"
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        url?.let { onUrlChanged(it) }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val reqUrl = request?.url?.toString() ?: ""
                        val block = view?.tag as? Boolean ?: true
                        if (block && com.example.util.AdBlocker.isAd(reqUrl)) {
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        url?.let { onUrlChanged(it) }
                        // Inject element detector javascript
                        view?.loadUrl(
                            "javascript:(function() { " +
                                    "var videos = document.getElementsByTagName('video');" +
                                    "for (var i = 0; i < videos.length; i++) {" +
                                    "   var v = videos[i];" +
                                    "   if (v.src && v.src.trim() !== '') {" +
                                      "       window.IDM_JS_INTERFACE.onVideoFound(v.src, document.title);" +
                                    "   }" +
                                    "   var sources = v.getElementsByTagName('source');" +
                                    "   for (var j = 0; j < sources.length; j++) {" +
                                    "       if (sources[j].src && sources[j].src.trim() !== '') {" +
                                    "           window.IDM_JS_INTERFACE.onVideoFound(sources[j].src, document.title);" +
                                    "       }" +
                                    "   }" +
                                    "}" +
                                    "})()"
                        )
                    }

                    // Passive Real-time sniffing via network intercept (Core IDM Feature!)
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: ""
                        val block = view?.tag as? Boolean ?: true
                        
                        // Apply AdBlocker first
                        if (block && com.example.util.AdBlocker.isAd(reqUrl)) {
                            return com.example.util.AdBlocker.createEmptyResponse()
                        }

                        if (isSniffableVideoUrl(reqUrl)) {
                            post { onVideoDetected(reqUrl) }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        onPageProgressChanged(view, newProgress)
                    }
                }

                loadUrl(url)
                onCreated(this)
            }
        },
        update = { webView ->
            // Dynamically update context/state tag with no lag as user switches AdBlock shield toggles
            webView.tag = isAdBlockEnabled
        }
    )
}

/**
 * Evaluates whether a network URL contains video files or stream indicators
 * Excludes individual .ts segment files and encrypted key file extensions to prioritize .m3u8 playlists.
 */
fun isSniffableVideoUrl(url: String): Boolean {
    val lower = url.lowercase()
    val cleanUrl = lower.split("?")[0]
    
    // Ignore common non-video files to prevent false alarms
    if (cleanUrl.endsWith(".js") || cleanUrl.endsWith(".css") || 
        cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg") || 
        cleanUrl.endsWith(".png") || cleanUrl.endsWith(".gif") || 
        cleanUrl.endsWith(".svg") || cleanUrl.endsWith(".webp") ||
        cleanUrl.endsWith(".woff") || cleanUrl.endsWith(".woff2") || 
        cleanUrl.endsWith(".json") || cleanUrl.endsWith(".html") || 
        cleanUrl.endsWith(".htm") || lower.contains("analytics") || 
        lower.contains("google-analytics") || lower.contains("doubleclick")) {
        return false
    }

    // Ignore individual TS files and encryptions keys to prevent segment fragment overwrite
    if (cleanUrl.endsWith(".ts") || cleanUrl.contains(".ts") ||
        cleanUrl.endsWith(".key") || cleanUrl.contains(".key")) {
        return false
    }

    return cleanUrl.endsWith(".mp4") ||
            cleanUrl.contains(".mp4") ||
            cleanUrl.endsWith(".m3u8") ||
            cleanUrl.contains(".m3u8") ||
            cleanUrl.endsWith(".webm") ||
            cleanUrl.contains(".webm") ||
            cleanUrl.endsWith(".mkv") ||
            cleanUrl.contains(".mkv") ||
            lower.contains("video/mp4") ||
            lower.contains("mime=video") ||
            lower.contains("/mp4/") ||
            lower.contains("/video-") ||
            lower.contains("/video/") ||
            lower.contains("googlevideo.com")
}

private fun sanitizeUrl(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
        "https://$trimmed"
    } else {
        "https://www.google.com/search?q=${URLDecoder.decode(trimmed, "UTF-8")}"
    }
}

/**
 * Custom modern BottomSheet styled Dialog for prompt customization and format extraction
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadBottomSheetDialog(
    videoInfo: VideoInfo,
    onDismiss: () -> Unit,
    onConfirmDownload: (VideoFormat, String) -> Unit
) {
    var titleInput by remember { mutableStateOf(videoInfo.title) }
    var selectedFormat by remember { mutableStateOf<VideoFormat?>(null) }

    LaunchedEffect(videoInfo) {
        if (videoInfo.formats.isNotEmpty()) {
            selectedFormat = videoInfo.formats[0]
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false, onClick = {}) // prevent dismiss clicks inside
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .navigationBarsPadding()
                ) {
                    // Pull indicator
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "فيديو مكتشف! - Video Detected!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val context = LocalContext.current
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "الرابط المكتشف (Detected URL):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = videoInfo.sourceUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    try {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Video URL", videoInfo.sourceUrl)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, "تم نسخ الرابط بنجاح! / Link copied!", android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Link",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Title edit box
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Video Title / اسم الفيديو") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Select Quality / اختر الجودة والنوع",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Formats List
                    Box(modifier = Modifier.heightIn(max = 240.dp)) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(videoInfo.formats) { format ->
                                val isSelected = format == selectedFormat
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedFormat = format }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (format.isM3U8) Icons.Default.Stream else Icons.Default.Movie,
                                            contentDescription = "Format Format",
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = format.quality,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (format.isM3U8) "Multi-part HLS stream" else "Direct file Download",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (format.sizeBytes > 0) {
                                        Text(
                                            text = formatSize(format.sizeBytes),
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Bottom Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel / إلغاء")
                        }

                        Button(
                            onClick = {
                                selectedFormat?.let { format ->
                                    onConfirmDownload(format, titleInput)
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            enabled = selectedFormat != null
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = "Download Now")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download / تحميل الآن")
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}

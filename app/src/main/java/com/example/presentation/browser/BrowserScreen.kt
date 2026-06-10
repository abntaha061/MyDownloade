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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    var urlInput by remember { mutableStateOf(currentUrl) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val focusManager = LocalFocusManager.current

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
                        placeholder = { Text("Search or enter web address") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
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
        }
    }

    // --- Elegant Download Settings Dialog Sheet ---
    detectedVideo?.let { videoInfo ->
        DownloadBottomSheetDialog(
            videoInfo = videoInfo,
            onDismiss = { viewModel.clearDetectedVideo() },
            onConfirmDownload = { format, customTitle ->
                viewModel.startDownload(format, customTitle)
                viewModel.clearDetectedVideo()
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
            // Let internal state transitions happen natively, 
            // no need to re-trigger loadUrl here as it would disrupt active browsing
        }
    )
}

/**
 * Evaluates whether a network URL contains video files or stream indicators
 */
fun isSniffableVideoUrl(url: String): Boolean {
    val cleanUrl = url.lowercase().split("?")[0]
    return cleanUrl.endsWith(".mp4") ||
            cleanUrl.endsWith(".m3u8") ||
            cleanUrl.endsWith(".webm") ||
            cleanUrl.endsWith(".ts") ||
            cleanUrl.endsWith(".mkv") ||
            cleanUrl.contains("video/mp4") ||
            (cleanUrl.contains(".m3u8") && !cleanUrl.endsWith(".js"))
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

package com.example.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.presentation.browser.BrowserScreen
import com.example.presentation.browser.BrowserViewModel
import com.example.presentation.downloads.DownloadsScreen
import com.example.presentation.downloads.DownloadsViewModel
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    
    // Self-contained MVVM state resolution
    val homeViewModel = remember { HomeViewModel(context) }
    val browserViewModel = remember { BrowserViewModel(context) }
    val downloadsViewModel = remember { DownloadsViewModel(context) }
    val settingsViewModel = remember { SettingsViewModel(context) }

    val downloads by homeViewModel.downloads.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(0) } // 0 = Browser, 1 = Downloads, 2 = Settings
    var showManualUrlDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = currentTab != 0) {
        currentTab = 0
    }

    // Aggregate real-time statistics
    val totalCount = downloads.size
    val totalBytes = downloads.sumOf { it.totalBytes }
    val statsText = "التحميلات: $totalCount | الحجم: ${formatSize(totalBytes)}"

    Scaffold(
        topBar = {
            if (currentTab == 1) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "مستخرج وسائط IDM",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Internet Download Manager Mobile",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // Statistics Badge Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = statsText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.Language, contentDescription = "Browser") },
                    label = { Text("المتصفح", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.Download, contentDescription = "Downloads") },
                    label = { Text("التحميلات", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1) }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("الإعدادات", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1) }
                )
            }
        },
        floatingActionButton = {
            // Display manual link FAB only on Browser page or Downloads page
            if (currentTab != 2) {
                FloatingActionButton(
                    onClick = { showManualUrlDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Default.AddLink, contentDescription = "Add manual link")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> BrowserScreen(viewModel = browserViewModel)
                1 -> DownloadsScreen(viewModel = downloadsViewModel)
                2 -> SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }

    // --- Manual link submission Dialog ---
    if (showManualUrlDialog) {
        var manualUrl by remember { mutableStateOf("") }
        var manualTitle by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualUrlDialog = false },
            title = {
                Text(
                    text = "إضافة رابط تحميل يدوياً\nAdd Link Manually",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "أدخل رابط تحميل الفيديو المباشر وسيقوم محرك IDM بالتقاطه وتقسيمه فوراً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = manualUrl,
                        onValueChange = { manualUrl = it },
                        label = { Text("Video Stream URL / رابط الفيديو") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://example.com/video.mp4") }
                    )

                    OutlinedTextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        label = { Text("Target Filename / اسم الملف") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("My Video") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualUrl.isNotBlank()) {
                            homeViewModel.addManualDownload(manualUrl, manualTitle)
                            showManualUrlDialog = false
                        }
                    },
                    enabled = manualUrl.isNotBlank()
                ) {
                    Text("تبدأ التحميل - Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualUrlDialog = false }) {
                    Text("إلغاء - Cancel")
                }
            }
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}

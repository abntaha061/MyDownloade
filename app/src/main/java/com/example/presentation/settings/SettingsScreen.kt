package com.example.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val concurrentDownloads by viewModel.concurrentDownloads.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val defaultQuality by viewModel.defaultQuality.collectAsStateWithLifecycle()
    val saveDirectory by viewModel.saveDirectory.collectAsStateWithLifecycle()
    val adblockEnabled by viewModel.adblockEnabled.collectAsStateWithLifecycle()

    var concurrentInput by remember { mutableStateOf(concurrentDownloads.toFloat()) }
    var wifiOnlyInput by remember { mutableStateOf(wifiOnly) }
    var qualityInput by remember { mutableStateOf(defaultQuality) }
    var saveDirInput by remember { mutableStateOf(saveDirectory) }
    var adblockInput by remember { mutableStateOf(adblockEnabled) }
    
    var showQualityDropdown by remember { mutableStateOf(false) }
    val qualityOptions = listOf("1080p", "720p", "480p", "360p", "Audio Only")

    // Sync state if changed externally
    LaunchedEffect(concurrentDownloads, wifiOnly, defaultQuality, saveDirectory, adblockEnabled) {
        concurrentInput = concurrentDownloads.toFloat()
        wifiOnlyInput = wifiOnly
        qualityInput = defaultQuality
        saveDirInput = saveDirectory
        adblockInput = adblockEnabled
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Card 1: Connection & Thread Limits ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Speed, contentDescription = "Speed limit", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("التحميل متعدد الأجزاء (IDM Engine)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Multi-segment Concurrent Download parts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "عدد الأقسام المتزامنة: ${concurrentInput.toInt()}",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = concurrentInput,
                    onValueChange = { concurrentInput = it },
                    valueRange = 1f..16f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        thumbColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "تقسيم التحميل إلى خيوط متعددة يزيد السرعة بنسبة تصل إلى 500% تماماً كبرنامج IDM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        // --- Card 2: Network Conditions ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Wifi, contentDescription = "Network", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("شروط الشبكة والبيانات", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Network & Transfer Constraints", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("التحميل عبر WiFi فقط", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Download only on WiFi Networks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = wifiOnlyInput,
                        onCheckedChange = { wifiOnlyInput = it }
                    )
                }
            }
        }

        // --- Card 3: Storage & Directory Setup ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = "Directories", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("مجلد الحفظ الافتراضي", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Save Folder Destination", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = saveDirInput,
                    onValueChange = { saveDirInput = it },
                    label = { Text("Save Path / مسار ملف التنزيل") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Storage, contentDescription = "Storage path")
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "التطبيق يتمتع بدعم مدمج لذاكرة التخزين Scoped Storage ليعمل بدون مشاكل في أندرويد 11 فما فوق.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        // --- Card 4: Video Quality Defaults ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.HighQuality, contentDescription = "Quality", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("الجودة التلقائية المعتمدة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Default Download Quality preference", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showQualityDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(qualityInput, fontWeight = FontWeight.Bold)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                        }
                    }

                    DropdownMenu(
                        expanded = showQualityDropdown,
                        onDismissRequest = { showQualityDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        qualityOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = {
                                    qualityInput = opt
                                    showQualityDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- Card 5: Real Ad Blocker Toggle ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = "Adblock", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("مانع الإعلانات (Ad Blocker)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("أداة منع النوافذ المنبثقة وإعلانات إعادة التوجيه المزعجة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("حظر النوافذ المنبثقة والإعلانات", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Block ads, banners, pop-unders and redirection tabs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = adblockInput,
                        onCheckedChange = { adblockInput = it }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Save Settings Trigger ---
        Button(
            onClick = {
                viewModel.updateSettings(
                    concurrent = concurrentInput.toInt(),
                    wifiOnly = wifiOnlyInput,
                    quality = qualityInput,
                    saveDirectory = saveDirInput,
                    adblockEnabled = adblockInput
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(27.dp)
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = "Save settings")
            Spacer(modifier = Modifier.width(8.dp))
            Text("حفظ الإعدادات - Save Configuration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

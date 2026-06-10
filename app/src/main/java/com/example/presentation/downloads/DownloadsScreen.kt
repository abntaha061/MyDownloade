package com.example.presentation.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    modifier: Modifier = Modifier
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabTitles = listOf(
        "جاري التحميل" to "Downloading",
        "مكتمل" to "Completed",
        "موقوف / فاشل" to "Paused/Failed"
    )

    // Filter list based on selected tab index
    val filteredDownloads = remember(downloads, selectedTabIndex) {
        when (selectedTabIndex) {
            0 -> downloads.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
            1 -> downloads.filter { it.status == DownloadStatus.COMPLETED }
            else -> downloads.filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Tab Selection Row ---
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabTitles.forEachIndexed { index, (ar, en) ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = ar,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = en,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // --- Downloads List ---
        if (filteredDownloads.isEmpty()) {
            EmptyDownloadsState(selectedTabIndex)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredDownloads, key = { it.id }) { task ->
                    DownloadItemCard(
                        task = task,
                        onPause = { viewModel.pauseDownload(task.id) },
                        onResume = { viewModel.resumeDownload(task) },
                        onDelete = { viewModel.deleteDownload(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (task.status) {
        DownloadStatus.COMPLETED -> AccentTeal
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.PAUSED -> PrimaryGold
        else -> PrimaryNeon
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = statusColor.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min) // Keeps left status bar matched to card height
        ) {
            // High-fidelity vertical status bar
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(statusColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                // First Row: Icon + Title + Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Specific Icon Container
                    val iconContainerColor = when (task.status) {
                        DownloadStatus.COMPLETED -> AccentTeal.copy(alpha = 0.15f)
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        else -> PrimaryNeon.copy(alpha = 0.15f)
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color = iconContainerColor, shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (task.status) {
                                DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                                DownloadStatus.FAILED -> Icons.Default.Error
                                DownloadStatus.PAUSED -> Icons.Default.PauseCircle
                                else -> Icons.Default.PlayCircle
                            },
                            contentDescription = "Status",
                            tint = statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title details
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (task.status == DownloadStatus.COMPLETED) "ملف مكتمل / Offline File" else task.filePath.substringAfterLast("/"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PENDING) {
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Resume",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Bar (if not completed or if downloading/paused)
                if (task.status != DownloadStatus.COMPLETED) {
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stats footer: Speed + Percentage + Remaining Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val percent = (task.progress * 100).toInt()
                        Text(
                            text = "$percent% (${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (task.status == DownloadStatus.DOWNLOADING) {
                                Text(
                                    text = "⚡ ${task.speed}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AccentTeal,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (task.status == DownloadStatus.DOWNLOADING) "⏳ ${task.timeLeft}" else when(task.status) {
                                    DownloadStatus.PAUSED -> "موقوف"
                                    DownloadStatus.FAILED -> "فشل"
                                    DownloadStatus.PENDING -> "بالانتظار"
                                    else -> "موقوف"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (task.status == DownloadStatus.FAILED && !task.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "الخطأ (Reason): ${task.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // Completed layout indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الحجم: ${formatBytes(task.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        AssistChip(
                            onClick = { /* Open file */ },
                            label = { Text("تم التحميل بنجاح", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Success",
                                    tint = AccentTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = AccentTeal.copy(alpha = 0.12f),
                                labelColor = AccentTeal
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentTeal.copy(alpha = 0.25f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyDownloadsState(tabIndex: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (tabIndex) {
                    0 -> Icons.Default.CloudDownload
                    1 -> Icons.Default.CheckCircle
                    else -> Icons.Default.PauseCircleFilled
                },
                contentDescription = "Empty",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (tabIndex) {
                    0 -> "لا توجد ملفات قيد التحميل حالياً"
                    1 -> "لم تكتمل أي عمليات تحميل بعد"
                    else -> "لا توجد ملفات موقوفة أو معطلة"
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (tabIndex) {
                    0 -> "No videos downloading at the moment."
                    1 -> "You haven't completed any downloads yet."
                    else -> "No suspended downloads."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
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

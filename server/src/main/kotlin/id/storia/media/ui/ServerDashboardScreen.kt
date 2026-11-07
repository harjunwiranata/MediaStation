package id.storia.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.storia.media.gui.GuiLogAppender
import id.storia.media.gui.LogMessage
import id.storia.media.gui.ServerManager
import id.storia.media.gui.ServerStatus
import id.storia.media.service.LibraryService
import id.storia.media.service.ScannerService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDashboardScreen(
    onOpenLibrary: () -> Unit
) {
    val status by ServerManager.status.collectAsState()
    val port by ServerManager.port.collectAsState()
    val errorMessage by ServerManager.errorMessage.collectAsState()
    val logs by GuiLogAppender.logs.collectAsState()

    var selectedLogLevel by remember { mutableStateOf("ALL") }
    var logSearchText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var isScanningAll by remember { mutableStateOf(false) }
    val scanStatus = remember { mutableStateOf("Idle") }

    LaunchedEffect(Unit) {
        ServerManager.initializeDatabase()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp).size(28.dp)
                            )
                            Column {
                                Text(
                                    "MediaStation Server Dashboard",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "Embedded Ktor Server & Local Database Manager",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left Column: Server Status, Quick Action & Library Scanners
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Server Control Card
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Server Status & Controls",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val (badgeColor, statusText) = when (status) {
                                    ServerStatus.RUNNING -> Color(0xFF4CAF50) to "RUNNING"
                                    ServerStatus.STOPPED -> Color(0xFFE53935) to "STOPPED"
                                    ServerStatus.STARTING -> Color(0xFFFF9800) to "STARTING..."
                                    ServerStatus.STOPPING -> Color(0xFFFF9800) to "STOPPING..."
                                    ServerStatus.ERROR -> Color(0xFFD32F2F) to "ERROR"
                                }
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(badgeColor, shape = RoundedCornerShape(6.dp))
                                )
                                Text(
                                    statusText,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = badgeColor
                                )
                            }

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (status == ServerStatus.RUNNING) {
                                            ServerManager.stopServer()
                                        } else {
                                            ServerManager.startServer()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (status == ServerStatus.RUNNING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (status == ServerStatus.RUNNING) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (status == ServerStatus.RUNNING) "Stop Server" else "Start Server")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { onOpenLibrary() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Local Library (Direct Access)")
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Listening on: http://localhost:$port/api/v1",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Library Scanners Card
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Library Management",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isScanningAll = true
                                        scanStatus.value = "Scanning all libraries..."
                                        try {
                                            val libraries = LibraryService.getAllLibraries()
                                            for (lib in libraries) {
                                                scanStatus.value = "Scanning ${lib.name}..."
                                                ScannerService.scanLibrary(lib.id)
                                            }
                                            scanStatus.value = "All library scans complete"
                                        } catch (e: Exception) {
                                            scanStatus.value = "Error: ${e.message}"
                                        } finally {
                                            isScanningAll = false
                                        }
                                    }
                                },
                                enabled = !isScanningAll,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isScanningAll) "Scanning in progress..." else "Scan All Libraries")
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Status: ${scanStatus.value}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Right Column: Live Log Console
                ElevatedCard(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Live Server Console",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            TextButton(onClick = { GuiLogAppender.clearLogs() }) {
                                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = logSearchText,
                                onValueChange = { logSearchText = it },
                                label = { Text("Search logs...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )

                            @Composable
                            fun LevelChip(level: String) {
                                FilterChip(
                                    selected = selectedLogLevel == level,
                                    onClick = { selectedLogLevel = level },
                                    label = { Text(level) }
                                )
                            }
                            LevelChip("ALL")
                            LevelChip("INFO")
                            LevelChip("WARN")
                            LevelChip("ERROR")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val filteredLogs = remember(logs, selectedLogLevel, logSearchText) {
                            logs.filter { entry ->
                                val matchesLevel = when (selectedLogLevel) {
                                    "ALL" -> true
                                    else -> entry.level.equals(selectedLogLevel, ignoreCase = true)
                                }
                                val matchesSearch = if (logSearchText.isBlank()) true else {
                                    entry.message.contains(logSearchText, ignoreCase = true) ||
                                            entry.tag.contains(logSearchText, ignoreCase = true)
                                }
                                matchesLevel && matchesSearch
                            }
                        }

                        val listState = rememberLazyListState()

                        LaunchedEffect(filteredLogs.size) {
                            if (filteredLogs.isNotEmpty()) {
                                listState.animateScrollToItem(filteredLogs.size - 1)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            color = Color(0xFF121212),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredLogs) { log ->
                                    LogItemRow(log)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(log: LogMessage) {
    val levelColor = when (log.level.uppercase()) {
        "ERROR" -> Color(0xFFEF5350)
        "WARN" -> Color(0xFFFFCA28)
        "INFO" -> Color(0xFF66BB6A)
        else -> Color(0xFFB0BEC5)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = log.timestamp,
            color = Color(0xFF757575),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            text = "[${log.level}]",
            color = levelColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "[${log.tag}]",
            color = Color(0xFF26C6DA),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            text = log.message,
            color = Color(0xFFE0E0E0),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

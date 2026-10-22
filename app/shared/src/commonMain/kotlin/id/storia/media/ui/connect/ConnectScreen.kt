package id.storia.media.ui.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

data class DiscoveredServer(val name: String, val host: String, val port: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    discoveredServers: List<DiscoveredServer> = emptyList(),
    onConnect: (String) -> Unit
) {
    var manualIp by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Connect to Mediastoria") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Searching for servers on local network...", style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(discoveredServers) { server ->
                    ListItem(
                        headlineContent = { Text(server.name) },
                        supportingContent = { Text("${server.host}:${server.port}") },
                        modifier = Modifier.clickable {
                            onConnect("http://${server.host}:${server.port}/api/v1")
                        }
                    )
                }
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Or enter IP manually:", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                label = { Text("192.168.x.x:9090") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    val trimmedIp = manualIp.trim()
                    val url = if (trimmedIp.startsWith("http")) trimmedIp else "http://$trimmedIp"
                    val finalUrl = if (url.contains("/api/v1")) url else "$url/api/v1"
                    onConnect(finalUrl)
                },
                enabled = manualIp.isNotBlank() && !isConnecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect Manually")
            }
        }
    }
}

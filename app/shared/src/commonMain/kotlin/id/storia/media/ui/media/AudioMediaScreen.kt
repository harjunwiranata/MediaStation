package id.storia.media.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.storia.media.data.model.Media

@Composable
fun AudioMediaContent(media: Media, onAudioClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(media.mainFiles) { file ->
            ListItem(
                headlineContent = { 
                    Text(
                        file.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                supportingContent = {
                    file.streamInfo?.durationMs?.let { ms ->
                        val totalSecs = ms / 1000
                        val mins = totalSecs / 60
                        val secs = totalSecs % 60
                        Text("${mins}:${secs.toString().padStart(2, '0')}")
                    }
                },
                leadingContent = {
                    Icon(Icons.Default.Audiotrack, contentDescription = null)
                },
                modifier = Modifier.clickable { onAudioClick(file.id) }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

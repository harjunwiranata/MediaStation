package id.storia.media.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.storia.media.data.model.Media

@Composable
fun DocumentMediaContent(media: Media, onDocClick: (String) -> Unit) {
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
                    file.fileSize?.let { size ->
                        val kb = size / 1024
                        if (kb > 1024) {
                            Text("${kb / 1024} MB")
                        } else {
                            Text("$kb KB")
                        }
                    }
                },
                leadingContent = {
                    Icon(Icons.Default.Description, contentDescription = null)
                },
                modifier = Modifier.clickable { onDocClick(file.id) }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

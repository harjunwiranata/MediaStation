package id.storia.media.ui.player.book

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import id.storia.media.data.remote.KtorClient
import id.storia.media.ui.components.ReadingMode
import id.storia.media.ui.components.ZoomableBox
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun PdfViewer(
    itemId: String,
    filePath: String,
    modifier: Modifier,
    readingMode: ReadingMode
) {
    val context = LocalContext.current
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageDimensions by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var localFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(filePath) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(filePath)
            val tempFile = File(context.cacheDir, "reading_${itemId}.pdf")
            try {
                when {
                    uri.scheme == "content" -> {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    uri.scheme == "http" || uri.scheme == "https" -> {
                        val response = KtorClient.client.get(filePath)
                        if (response.status.value in 200..299) {
                            val channel = response.bodyAsChannel()
                            tempFile.outputStream().use { output ->
                                channel.copyTo(output)
                            }
                        } else {
                            throw Exception("Failed to download file: ${response.status}")
                        }
                    }
                    else -> {
                        val sourceFile = File(filePath)
                        if (sourceFile.exists()) {
                            sourceFile.copyTo(tempFile, overwrite = true)
                        } else {
                            throw Exception("Source file not found: $filePath")
                        }
                    }
                }

                val pfd = context.contentResolver.openFileDescriptor(Uri.fromFile(tempFile), "r")
                if (pfd != null) {
                    val pdfRenderer = PdfRenderer(pfd)
                    val dims = mutableListOf<Pair<Int, Int>>()
                    for (i in 0 until pdfRenderer.pageCount) {
                        pdfRenderer.openPage(i).use { page ->
                            dims.add(page.width to page.height)
                        }
                    }
                    renderer = pdfRenderer
                    pageDimensions = dims
                    localFile = tempFile
                }
                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                isLoading = false
            }
        }
    }

    DisposableEffect(filePath) {
        onDispose {
            renderer?.close()
            localFile?.let { if (it.exists()) it.delete() }
            renderer = null
            localFile = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading || renderer == null || pageDimensions.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            when (readingMode) {
                ReadingMode.VERTICAL -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(pageDimensions.size, key = { it }) { index ->
                            PdfPage(
                                renderer = renderer!!,
                                index = index,
                                originalWidth = pageDimensions[index].first,
                                originalHeight = pageDimensions[index].second,
                                isVertical = true
                            )
                        }
                    }
                }
                ReadingMode.HORIZONTAL_LTR, ReadingMode.HORIZONTAL_RTL -> {
                    val pagerState = rememberPagerState(pageCount = { pageDimensions.size })
                    val layoutDirection = if (readingMode == ReadingMode.HORIZONTAL_RTL) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            PdfPage(
                                renderer = renderer!!,
                                index = pageIndex,
                                originalWidth = pageDimensions[pageIndex].first,
                                originalHeight = pageDimensions[pageIndex].second,
                                isVertical = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    index: Int,
    originalWidth: Int,
    originalHeight: Int,
    isVertical: Boolean = true
) {
    val context = LocalContext.current
    
    BoxWithConstraints(
        modifier = if (isVertical) {
            Modifier
                .fillMaxWidth()
                .aspectRatio(originalWidth.toFloat() / originalHeight.toFloat())
        } else {
            Modifier.fillMaxSize()
        },
        contentAlignment = Alignment.Center
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val densityDpi = context.resources.displayMetrics.densityDpi

        val bitmapsState = produceState<List<Bitmap>>(initialValue = emptyList(), index, isVertical) {
            withContext(Dispatchers.IO) {
                RenderLimiter.semaphore.withPermit {
                    try {
                        renderer.openPage(index).use { page ->
                            val scale = if (isVertical) {
                                screenWidth / page.width
                            } else {
                                screenHeight / page.height
                            }
                            
                            val totalWidth = (page.width * scale).toInt().coerceAtLeast(1)
                            val totalHeight = (page.height * scale).toInt().coerceAtLeast(1)
                            
                            val maxDimension = 4096
                            val chunks = mutableListOf<Bitmap>()
                            
                            if (totalHeight <= maxDimension && totalWidth <= maxDimension) {
                                val b = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
                                b.density = densityDpi
                                val matrix = Matrix()
                                matrix.postScale(scale, scale)
                                page.render(b, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                chunks.add(b)
                            } else {
                                var remainingHeight = totalHeight
                                var currentY = 0f
                                while (remainingHeight > 0) {
                                    val chunkHeight = minOf(remainingHeight, maxDimension)
                                    val b = Bitmap.createBitmap(totalWidth, chunkHeight, Bitmap.Config.ARGB_8888)
                                    b.density = densityDpi
                                    
                                    val matrix = Matrix()
                                    matrix.postScale(scale, scale)
                                    matrix.postTranslate(0f, -currentY)
                                    
                                    page.render(b, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    chunks.add(b)
                                    
                                    remainingHeight -= chunkHeight
                                    currentY += chunkHeight
                                }
                            }
                            value = chunks
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        value = emptyList()
                    }
                }
            }
        }

        val bitmaps = bitmapsState.value

        if (bitmaps.isNotEmpty()) {
            ZoomableBox(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = if (isVertical) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    bitmaps.forEach { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = if (isVertical) {
                                Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                            } else {
                                Modifier.fillMaxSize()
                            },
                            contentScale = if (isVertical) ContentScale.FillWidth else ContentScale.Fit
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

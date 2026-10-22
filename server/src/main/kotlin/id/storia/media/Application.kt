package id.storia.media

import id.storia.media.db.*
import id.storia.media.service.*
import id.storia.media.data.model.*
import id.storia.media.util.PathProvider
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.partialcontent.*
import kotlinx.coroutines.launch
import java.io.File
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.zip.ZipFile
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

fun main() {
    Database.connect(PathProvider.getDatabaseJdbcUrl(), driver = "org.h2.Driver")

    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            Libraries,
            LibraryFolders,
            MediaItems,
            MediaFiles,
            Subtitles,
            PlaybackProgressTable,
            ServerSettings
        )
    }

    val port = 9090
    val jmdns = JmDNS.create()
    val serviceInfo = ServiceInfo.create("_mediastation._tcp.local.", "MediaStation Server", port, "MediaStation Server")
    jmdns.registerService(serviceInfo)

    Runtime.getRuntime().addShutdownHook(Thread {
        jmdns.unregisterAllServices()
        jmdns.close()
    })

    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(PartialContent)

    routing {
        get("/") {
            call.respond(mapOf("status" to "MediaStation Server is running"))
        }

        route("/api/v1") {
            route("/libraries") {
                get {
                    call.respond(LibraryService.getAllLibraries())
                }
                
                post {
                    val input = call.receive<CreateLibraryRequest>()
                    val lib = LibraryService.createLibrary(input.name, input.type, input.paths)
                    call.respond(HttpStatusCode.Created, lib)
                }

                get("/{id}") {
                    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val lib = LibraryService.getLibraryById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respond(lib)
                }

                delete("/{id}") {
                    val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    LibraryService.deleteLibrary(id)
                    call.respond(mapOf("success" to true))
                }

                post("/{id}/scan") {
                    val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    // Launch scan in background
                    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                    kotlinx.coroutines.GlobalScope.launch {
                        ScannerService.scanLibrary(id)
                    }
                    call.respond(mapOf("message" to "Scan initiated"))
                }
                get("/{id}/media") {
                    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val search = call.request.queryParameters["search"]
                    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 40
                    val media = LibraryService.getMediaItemsByLibrary(id, search, false, offset, limit)
                    call.respond(media)
                }
            }

            route("/media") {
                get("/recent") {
                    val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 40
                    call.respond(LibraryService.getRecentMediaItems(limit, offset, false))
                }
                
                get("/{id}") {
                    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val item = LibraryService.getMediaItemDetails(id, false) ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respond(item)
                }
            }
            
            route("/files") {
                get("/{id}") {
                    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val file = LibraryService.getMediaFileById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respond(file)
                }

                get("/{id}/pages") {
                    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    call.respond(LibraryService.getPages(id))
                }

                get("/{id}/raw") {
                    serveRawFile(call)
                }

                get("/{id}/raw/{filename}") {
                    serveRawFile(call)
                }
            }

            route("/playback") {
                post("/progress") {
                    val progress = call.receive<PlaybackProgress>()
                    LibraryService.updatePlaybackProgress(
                        progress.mediaFileId,
                        progress.clientId ?: "default",
                        progress.positionSeconds,
                        progress.durationSeconds ?: 0,
                        progress.pageNumber,
                        progress.totalPages
                    )
                    call.respond(GenericResponse(true))
                }
            }

            route("/stream") {
                get("/{id}") {
                    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val pageFile = LibraryService.getPageFile(id, "") // Passing empty string to trigger base file logic if not a page request
                    if (pageFile != null && pageFile.exists()) {
                        call.respondFile(pageFile)
                    } else {
                        // Fallback to original stream logic if getPageFile doesn't handle it
                        val path = LibraryService.getFilePath(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                        val file = File(path)
                        if (file.exists()) {
                            call.respondFile(file)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }

            route("/images") {
                get("/{id}/poster") {
                    val id = call.parameters["id"]?.trim() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val path = LibraryService.getPosterPath(id) ?: run {
                        call.response.header(HttpHeaders.CacheControl, "no-store, must-revalidate")
                        return@get call.respond(HttpStatusCode.NotFound)
                    }
                    val file = File(path)
                    if (file.exists()) {
                        val ext = file.extension.lowercase()
                        val type = when (ext) {
                            "jpg", "jpeg" -> ContentType.Image.JPEG
                            "png" -> ContentType.Image.PNG
                            "webp" -> ContentType("image", "webp")
                            "tbn" -> ContentType.Image.JPEG
                            else -> ContentType.defaultForFileExtension(ext)
                        }
                        call.response.header(HttpHeaders.ContentType, type.toString())
                        call.respondFile(file)
                    } else {
                        call.response.header(HttpHeaders.CacheControl, "no-store, must-revalidate")
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
                get("/{id}/fanart") {
                    val id = call.parameters["id"]?.trim() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val path = LibraryService.getFanartPath(id) ?: run {
                        call.response.header(HttpHeaders.CacheControl, "no-store, must-revalidate")
                        return@get call.respond(HttpStatusCode.NotFound)
                    }
                    val file = File(path)
                    if (file.exists()) {
                        val ext = file.extension.lowercase()
                        val type = when (ext) {
                            "jpg", "jpeg" -> ContentType.Image.JPEG
                            "png" -> ContentType.Image.PNG
                            "webp" -> ContentType("image", "webp")
                            "tbn" -> ContentType.Image.JPEG
                            else -> ContentType.defaultForFileExtension(ext)
                        }
                        call.response.header(HttpHeaders.ContentType, type.toString())
                        call.respondFile(file)
                    } else {
                        call.response.header(HttpHeaders.CacheControl, "no-store, must-revalidate")
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                get("/{id}/thumbnail") {
                    val id = call.parameters["id"]?.trim() ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val thumbnailPath = LibraryService.getThumbnailPath(id)
                    val file = if (thumbnailPath != null) {
                        File(thumbnailPath)
                    } else {
                        LibraryService.getFirstPageFile(id)
                    }

                    if (file != null && file.exists()) {
                        val ext = file.extension.lowercase()
                        val type = when (ext) {
                            "jpg", "jpeg" -> ContentType.Image.JPEG
                            "png" -> ContentType.Image.PNG
                            "webp" -> ContentType("image", "webp")
                            "tbn" -> ContentType.Image.JPEG
                            else -> ContentType.defaultForFileExtension(ext)
                        }
                        call.response.header(HttpHeaders.ContentType, type.toString())
                        call.respondFile(file)
                    } else {
                        call.response.header(HttpHeaders.CacheControl, "no-store, must-revalidate")
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            route("/system") {
                get("/info") {
                    call.respond(LibraryService.getSystemInfo())
                }
            }

            get("/scan/status") {
                call.respond(ScannerService.getStatus())
            }
        }
    }
}

private suspend fun serveRawFile(call: ApplicationCall) {
    val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest)
    val filename = call.parameters["filename"]
    val path = LibraryService.getFilePath(id) ?: return call.respond(HttpStatusCode.NotFound)
    val file = File(path)
    
    if (!file.exists()) return call.respond(HttpStatusCode.NotFound)

    if (filename != null) {
        val pageFile = LibraryService.getPageFile(id, filename)
        if (pageFile != null && pageFile.exists()) {
            call.respondFile(pageFile)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    } else {
        if (file.isFile) {
            val ext = file.extension.lowercase()
            val type = when (ext) {
                "jpg", "jpeg" -> ContentType.Image.JPEG
                "png" -> ContentType.Image.PNG
                "webp" -> ContentType("image", "webp")
                "pdf" -> ContentType.Application.Pdf
                "cbz" -> ContentType("application", "x-cbz")
                else -> ContentType.defaultForFileExtension(ext)
            }
            call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${file.name}\"")
            call.response.header(HttpHeaders.ContentType, type.toString())
            call.respondFile(file)
        } else {
            call.respond(HttpStatusCode.BadRequest, "Requested item is a directory, filename required")
        }
    }
}

package id.storia.media.entrypoint

import id.storia.media.api.module
import id.storia.media.db.*
import id.storia.media.util.PathProvider
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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

    embeddedServer(Netty, port = port, host = "0.0.0.0", module = io.ktor.server.application.Application::module)
        .start(wait = true)
}

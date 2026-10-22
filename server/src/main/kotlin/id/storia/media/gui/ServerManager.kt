package id.storia.media.gui

import id.storia.media.db.*
import id.storia.media.module
import id.storia.media.util.PathProvider
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger as LogbackLogger
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

object ServerManager {
    private var serverEngine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var jmdns: JmDNS? = null

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _port = MutableStateFlow(9090)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var isDbInitialized = false

    init {
        attachLogAppender()
    }

    private fun attachLogAppender() {
        runCatching {
            val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? LogbackLogger
            if (rootLogger != null) {
                val appender = GuiLogAppender()
                appender.context = rootLogger.loggerContext
                appender.name = "GUI_LOG_APPENDER"
                appender.start()
                rootLogger.addAppender(appender)
            }
        }
    }

    fun initializeDatabase() {
        if (isDbInitialized) return
        synchronized(this) {
            if (isDbInitialized) return
            try {
                Database.connect(PathProvider.getDatabaseJdbcUrl(), driver = "org.h2.Driver")
                transaction {
                    @Suppress("DEPRECATION")
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
                isDbInitialized = true
            } catch (e: Exception) {
                _errorMessage.value = "DB Init Error: ${e.message}"
            }
        }
    }

    fun startServer(targetPort: Int = _port.value) {
        if (_status.value == ServerStatus.RUNNING || _status.value == ServerStatus.STARTING) return

        _status.value = ServerStatus.STARTING
        _errorMessage.value = null
        _port.value = targetPort

        try {
            initializeDatabase()

            runCatching {
                val dns = JmDNS.create()
                val serviceInfo = ServiceInfo.create("_mediastation._tcp.local.", "MediaStation Server", targetPort, "MediaStation Server")
                dns.registerService(serviceInfo)
                jmdns = dns
            }

            val engine = embeddedServer(Netty, port = targetPort, host = "0.0.0.0", module = { module() })
            engine.start(wait = false)
            serverEngine = engine

            _status.value = ServerStatus.RUNNING
        } catch (e: Exception) {
            _status.value = ServerStatus.ERROR
            _errorMessage.value = e.message ?: "Failed to start server"
        }
    }

    fun stopServer() {
        if (_status.value == ServerStatus.STOPPED || _status.value == ServerStatus.STOPPING) return

        _status.value = ServerStatus.STOPPING
        try {
            jmdns?.runCatching {
                unregisterAllServices()
                close()
            }
            jmdns = null

            serverEngine?.stop(1000, 2000)
            serverEngine = null

            _status.value = ServerStatus.STOPPED
        } catch (e: Exception) {
            _status.value = ServerStatus.ERROR
            _errorMessage.value = e.message ?: "Failed to stop server"
        }
    }
}

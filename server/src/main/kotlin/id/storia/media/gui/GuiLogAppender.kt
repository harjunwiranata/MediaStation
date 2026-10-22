package id.storia.media.gui

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LogMessage(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String
)

class GuiLogAppender : AppenderBase<ILoggingEvent>() {

    override fun append(eventObject: ILoggingEvent) {
        val formattedTime = formatter.format(Instant.ofEpochMilli(eventObject.timeStamp))
        val entry = LogMessage(
            timestamp = formattedTime,
            level = eventObject.level.toString(),
            tag = eventObject.loggerName.substringAfterLast('.'),
            message = eventObject.formattedMessage
        )
        synchronized(lock) {
            val currentList = _logs.value.toMutableList()
            if (currentList.size >= MAX_LOGS) {
                currentList.removeAt(0)
            }
            currentList.add(entry)
            _logs.value = currentList
        }
    }

    companion object {
        private const val MAX_LOGS = 1000
        private val lock = Any()
        private val _logs = MutableStateFlow<List<LogMessage>>(emptyList())
        val logs: StateFlow<List<LogMessage>> = _logs.asStateFlow()

        private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())

        fun clearLogs() {
            synchronized(lock) {
                _logs.value = emptyList()
            }
        }
    }
}

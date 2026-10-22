package id.storia.media

import id.storia.media.db.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import kotlin.test.Test

class DbTest {
    @Test
    fun testH2Connection() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        
        transaction {
            addLogger(StdOutSqlLogger)
            
            SchemaUtils.create(
                Libraries,
                LibraryFolders,
                MediaItems,
                MediaFiles,
                Subtitles,
                PlaybackProgressTable,
                ServerSettings
            )
            
            Libraries.insert {
                it[id] = "test-lib"
                it[name] = "Test Library"
                it[type] = "movies"
            }
            
            val count = Libraries.selectAll().count()
            println("Libraries count: $count")
            assert(count == 1L)
        }
    }
}

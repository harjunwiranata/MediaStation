package id.storia.media

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

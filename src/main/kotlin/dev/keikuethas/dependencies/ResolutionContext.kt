package dev.keikuethas.dependencies

import java.net.http.HttpClient
import java.time.Duration

data class Limits(
    val maxParentDepth: Int = 20,
    val maxImportDepth: Int = 20,
    val maxLoads: Int = 500
)

class ResolutionContext(
    repoUrl: String,
    val limits: Limits = Limits(),
    httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
) {
    val downloader: PomDownloader = PomDownloader(repoUrl, httpClient)

    val pomCache: MutableMap<String, PomDocument> = mutableMapOf()

    fun newSession(): ResolutionSession = ResolutionSession(this)
}
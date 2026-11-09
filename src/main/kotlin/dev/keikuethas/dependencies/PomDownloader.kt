package dev.keikuethas.dependencies

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import dev.keikuethas.dependencies.FetchStatus.NOT_FOUND
import dev.keikuethas.dependencies.FetchStatus.OTHER
import dev.keikuethas.dependencies.FetchStatus.TRANSIENT

sealed interface FetchResult {
    data class Success(val body: String) : FetchResult
    data class Failure(val status: FetchStatus, val message: String) : FetchResult
}

class PomDownloader(
    private val repoUrl: String,
    private val client: HttpClient,
    private val requestTimeout: Duration = Duration.ofSeconds(30)
) {
    fun fetch(coords: Coordinates): FetchResult {
        val url = getUrl(coords)
        return try {
            val req = buildRequest(url)
            val resp = sendRequest(req)
            when {
                resp.statusCode() == 200 ->
                    FetchResult.Success(resp.body())
                resp.statusCode() == 404 ->
                    FetchResult.Failure(NOT_FOUND, "HTTP 404 for $url")
                resp.statusCode() in 500..599 ->
                    FetchResult.Failure(TRANSIENT, "HTTP ${resp.statusCode()} for $url")
                else ->
                    FetchResult.Failure(OTHER, "HTTP ${resp.statusCode()} for $url")
            }
        } catch (e: IOException) {
            FetchResult.Failure(TRANSIENT, "IO error for $url: ${e.message}")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            FetchResult.Failure(TRANSIENT, "Interrupted for $url")
        } catch (e: Exception) {
            FetchResult.Failure(OTHER, "Error for $url: ${e.message}")
        }
    }

    private fun sendRequest(req: HttpRequest) =
        client.send(req, HttpResponse.BodyHandlers.ofString())

    private fun buildRequest(url: String) =
        HttpRequest.newBuilder().uri(URI.create(url))
            .timeout(requestTimeout).GET().build()

    private fun getUrl(coords: Coordinates): String {
        val groupPath = coords.groupId.replace('.', '/')
        val classifierPart = coords.classifier?.let { "-$it" } ?: ""
        val url = "${repoUrl.trimEnd('/')}/$groupPath/${coords.artifactId}/${coords.version}/" +
                "${coords.artifactId}-${coords.version}$classifierPart.pom"

        return url
    }
}
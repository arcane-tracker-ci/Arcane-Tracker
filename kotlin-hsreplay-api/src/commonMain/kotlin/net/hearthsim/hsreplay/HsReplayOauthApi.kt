package net.hearthsim.hsreplay

import io.ktor.client.HttpClient
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import net.hearthsim.console.Console
import net.hearthsim.hsreplay.model.Token

class HsReplayOauthApi(val userAgent: String,
                       val clientId: String,
                       val clientSecret: String,
                       console: Console) {
    private val client = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json.nonstrict).apply {
                setMapper(Token::class, Token.serializer())
            }
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    console.debug(message)
                }
            }
            level = LogLevel.NONE
        }

    }

    companion object {
        const val AUTHORIZE_URL = "https://hsreplay.net/oauth2/authorize/?utm_source=arcanetracker&utm_medium=client"
        const val CALLBACK_URL = "arcanetracker://callback/"
    }

    suspend fun login(code: String): Token = client.post("https://hsreplay.net/oauth2/token/") {
        body = FormDataContent(Parameters.build {
            append("code", code)
            append("client_id", clientId)
            append("client_secret", clientSecret)
            append("grant_type", "authorization_code")
            append("redirect_uri", CALLBACK_URL) // not sure we need redirect_uri but it's working so I'm leaving it
        })
        header("User-Agent", userAgent)
    }

    suspend fun refresh(refreshToken: String): HttpResponse = client.post("https://hsreplay.net/oauth2/token/") {
        body = FormDataContent(Parameters.build {
            append("client_id", clientId)
            append("client_secret", clientSecret)
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
        })
        header("User-Agent", userAgent)
    }
}
package io.itch.mattemade.pipeliner

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Date

const val CREDENTIALS_FILE_NAME = "tokens/credentials.json"
const val SERVICE_FILE_NAME = "tokens/service.json"
const val TOKENS_DIRECTORY_PATH = "tokens"
const val APPLICATION_NAME = "Pipeliner"
val JSON_FACTORY = GsonFactory.getDefaultInstance()
val SCOPES = listOf(SheetsScopes.SPREADSHEETS_READONLY)

@Volatile var authUrl: String? = null
@Volatile var credentials: Credential? = null
var serviceCredential: GoogleCredentials? = null

@Throws(IOException::class)
fun getCredentials(transport: NetHttpTransport) {
    // Load client secrets.
    serviceCredential?.let {
        if (it.accessToken.expirationTime.before(Date())) {
            it.refresh()
        }
        return
    }

    val input: InputStream = File(SERVICE_FILE_NAME).inputStream()
    serviceCredential = ServiceAccountCredentials.fromStream(input).createScoped(SCOPES)
    serviceCredential?.refresh()

    /*val clientSecrets =
        GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(input))

    // Build flow and trigger user authorization request.
    val flow = GoogleAuthorizationCodeFlow.Builder(
        transport, JSON_FACTORY, clientSecrets, SCOPES
    )
        .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
        .setAccessType("online")
        .build()
    val receiver = LocalServerReceiver.Builder().setHost("demo.mattemade.net").setPort(8888).build()

    val authorize = AuthorizationCodeInstalledApp(flow, receiver, {
        credentials = null
        authUrl = it
    }).authorize("user")
    authUrl = null
    credentials = authorize*/
}
package io.itch.mattemade.pipeliner

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticRootFolder
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val timeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
private val dataFile = File("data.txt").also { if (!it.exists()) it.createNewFile() }
private val accessTokenFile = File("access_token.txt").also { if (!it.exists()) it.createNewFile() }
private val defaultAccessToken = "default"
private val newLine = "\n"
private val divider = "|"
private var accessToken = defaultAccessToken

fun main() {
    accessToken = accessTokenFile.readText().trim().takeIf { it.isNotEmpty() } ?: defaultAccessToken

    val environment = applicationEngineEnvironment {
        connector {
            port = 32515
        }
        module(Application::module)
    }
    embeddedServer(Netty, environment = environment)
        .start(wait = true)
}

fun Application.module() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.AccessControlAllowHeaders)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        allowHeader(HttpHeaders.ContentDisposition)
        anyHost()
    }
    routing {

        /*get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }*/
        get("/listFiles/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString(separator = "/") ?: ""
            val filePath = "files/$path"
            println("requesting files from $filePath")
            val fileList =
                File(filePath).listFiles()?.map { "${it.name}|${it.isDirectory}|${it.isLaunchableDir()}" }
                    ?: emptyList<String>()
            val response = fileList.joinToString(separator = ",")
            println("responding with $response")
            call.respondText(response)
        }
        get("/delete/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString(separator = "/") ?: ""
            val filePath = "files/$path"
            println("requesting deleting $filePath")
            File(filePath).apply {
                if (exists()) {
                    deleteRecursively()
                }
            }
            call.respondText("ok")
        }
        get("/getZip/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString(separator = "/") ?: ""
            val inputDirectoryPath = "files/$path"
            val zipFilePath = "files/zip/$path.zip"
            println("creating zip from $path")
            val zip = File(zipFilePath)
            zip.parentFile.mkdirs()
            if (zip.exists()) {
                zip.delete()
            }
            val inputDirectory = File(inputDirectoryPath)
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { zos ->
                inputDirectory.walkTopDown().forEach { file ->
                    val zipFileName = file.absolutePath
                        .removePrefix(inputDirectory.absolutePath)
                        .removePrefix("\\") // windows dev
                        .removePrefix("/") // linux prod
                    val entry = ZipEntry("$zipFileName${(if (file.isDirectory) "/" else "")}")
                    zos.putNextEntry(entry)
                    if (file.isFile) {
                        file.inputStream().use { fis -> fis.copyTo(zos) }
                    }
                }
                //zos.closeEntry()
            }
            call.respondText(zipFilePath)
        }
        post("/uploadFile") {
            val multipartData = call.receiveMultipart()
            var fileToReplace: String? = null

            multipartData.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> fileToReplace = part.value
                    is PartData.FileItem -> {
                        val fileName = fileToReplace ?: "assets/${part.originalFileName}"
                        val writingFile = File("files/$fileName")
                        writingFile.parentFile.mkdirs()
                        if (writingFile.exists()) {
                            writingFile.delete()
                        }
                        println("writing to $fileName")

                        part.streamProvider().use { provider ->
                            writingFile.outputStream().use { outputStream ->
                                provider.copyTo(outputStream)
                            }
                        }

                        /*part.provider().use { provider ->

                        }*/

                        println("completed")
                    }

                    else -> {}
                }
            }
            call.respondText("A file is uploaded")
        }

        staticFiles("/files", File(staticRootFolder, "files").also { it.mkdir() })
        staticFiles("/", File(staticRootFolder, "files/wasmJs/productionExecutable").also { it.mkdir() }) {
            //default("index.html")
        }
        val ignoringFiles = setOf("styles.css", "hepler.js", "composeApp.js")

        /*staticFiles("/{...}", File(staticRootFolder, "files/wasmJs/productionExecutable")) {
            exclude {
                println("checking ${it.name}")
                it.name in ignoringFiles
            }
        }*/

        get("/data") {
            if (accessToken == defaultAccessToken) {
                accessToken = accessTokenFile.readText().trim().takeIf { it.isNotEmpty() } ?: defaultAccessToken
                call.respondText("change access token and repeat a couple of times!")
                return@get
            }

            if (accessToken != call.parameters["token"]) {
                call.respondText("error", status = HttpStatusCode.Unauthorized)
                return@get
            }

            call.respondFile(dataFile)
        }

        post("/data") {
            val receivedData = call.receiveText()
            synchronized(dataFile) {
                dataFile.appendText(timeFormatter.format(LocalDateTime.now()))
                dataFile.appendText(divider)
                dataFile.appendText(call.request.header("X-Forwarded-For") ?: "")
                dataFile.appendText(divider)
                dataFile.appendText(receivedData)
                dataFile.appendText(newLine)
            }
            call.response.status(HttpStatusCode.OK)
        }
    }
}

private fun File.isLaunchableDir(): Boolean =
    isDirectory && File(this, "index.html").exists()
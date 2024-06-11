package io.itch.mattemade.pipeliner

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticRootFolder
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.File
import java.net.InetAddress

fun main() {
    embeddedServer(Netty, port = 80, host = "0.0.0.0", module = Application::module)
    //embeddedServer(Netty, port = 80, host = "194.164.91.172", module = Application::module)
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
        post("/uploadFile") {
            val multipartData = call.receiveMultipart()
            var fileToReplace: String? = null

            multipartData.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> fileToReplace = part.value
                    is PartData.FileItem -> {
                        val fileName = fileToReplace ?: "assets/${part.originalFileName}"
                        val writingFile = File("files/$fileName")
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
            //val file = File("uploads/ktor_logo.png")
            //call.receiveChannel().copyAndClose(file.writeChannel())
            call.respondText("A file is uploaded")
        }


        val uploadedFilesDir = File(staticRootFolder, "files").also { it.mkdir() }
        staticFiles("/files", uploadedFilesDir)
        //staticFiles("/", File(staticRootFolder, "composeApp/build/dist/wasmJs/developmentExecutable"))
        staticFiles("/", File(staticRootFolder, "composeApp/build/dist/wasmJs/productionExecutable"))

    }
}

private fun File.isLaunchableDir(): Boolean =
    isDirectory && File(this, "index.html").exists()
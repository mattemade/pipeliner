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
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

fun main() {
    //embeddedServer(Netty, port = 80, host = "0.0.0.0", module = Application::module)
    embeddedServer(Netty, port = 80, host = "194.164.91.172", module = Application::module)
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
            val zipFilePath = "zip/$path.zip"
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
                    val entry = ZipEntry( "$zipFileName${(if (file.isDirectory) "/" else "" )}")
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
            //val file = File("uploads/ktor_logo.png")
            //call.receiveChannel().copyAndClose(file.writeChannel())
            if (fileToReplace == "/compose.zip") {
                val frontendDir = File("composeApp/build/dist/wasmJs/productionExecutable")
                frontendDir.listFiles().forEach {
                    it.deleteRecursively()
                }
                ZipFile(File("files/compose.zip")).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        if (!entry.isDirectory) {
                            zip.getInputStream(entry).use { input ->
                                File(frontendDir, entry.name.replace("\\", "/")).apply {
                                    parentFile.mkdirs()
                                    createNewFile()
                                }.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
            }
            call.respondText("A file is uploaded")
        }

        staticFiles("/files", File(staticRootFolder, "files").also { it.mkdir() })
        staticFiles("/", File(staticRootFolder, "composeApp/build/dist/wasmJs/productionExecutable"))
        staticFiles("/zip", File(staticRootFolder, "zip").also { it.mkdir() })
    }
}

private fun File.isLaunchableDir(): Boolean =
    isDirectory && File(this, "index.html").exists()
package com.blakesmods.plugin.watcher

import com.mongodb.client.MongoDatabase
import com.reposilite.maven.MavenFacade
import com.reposilite.maven.api.DeployEvent
import com.reposilite.maven.api.LookupRequest
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import com.reposilite.plugin.facade
import com.reposilite.storage.api.Location
import org.apache.commons.codec.digest.DigestUtils
import org.bson.types.ObjectId
import org.litote.kmongo.KMongo
import org.litote.kmongo.getCollection
import java.util.Date

@Suppress("PropertyName")
data class ModFile(
    val _id: ObjectId? = null,
    val file_name: String,
    val file_size: Number,
    val mod_id: String,
    val upload_date: Date,
    val maven_path: String,
    val md5_hash: String,
    val site_downloads: Number = 0,
    val mc_version: String,
    val mc_version_group: String,
    val mc_version_parts: VersionParts,
    val mc_versions: List<String>,
    val mod_version: String,
    val mod_version_parts: VersionParts,
    val curseforge_downloads: Number = 0,
    val modrinth_downloads: Number = 0,
)

data class VersionParts(
    val major: Number,
    val minor: Number,
    val patch: Number
)

data class FileInfo(
    val fileName: String,
    val modID: String,
    val mavenPath: String,
    val mcVersion: String,
    val mcVersionGroup: String,
    val modVersion: String
)

@Plugin(name = "watcher", dependencies = ["maven"])
class WatcherPlugin : ReposilitePlugin() {
    override fun initialize(): Facade? {
        logger.info("")
        logger.info("--- Watcher Plugin")

        val maven = facade<MavenFacade>()
        val db = getDatabase()

        val filesDB = db.getCollection<ModFile>("mod_files")

        event { event: DeployEvent ->
            if (event.gav.endsWith("-api.jar")) {
                logger.info("Skipping jar ${event.gav} as it's an API jar")
                return@event
            }

            if (!event.gav.endsWith(".jar")) {
                return@event
            }

            val info = createFileInfo(event.gav)
            val details = maven.findFile(
                LookupRequest(
                    accessToken = null,
                    repository = event.repository.name,
                    gav = event.gav
                )
            ).orNull() ?: throw Error("Failed to fetch details for file ${event.gav}")

            val file = ModFile(
                file_name = info.fileName,
                file_size = details.first.contentLength,
                mod_id = info.modID,
                upload_date = Date(),
                maven_path = info.mavenPath,
                md5_hash = DigestUtils.md5Hex(details.second),
                mc_version = info.mcVersion,
                mc_version_group = info.mcVersionGroup,
                mc_version_parts = createVersionParts(info.mcVersion),
                mc_versions = listOf(info.mcVersion),
                mod_version = info.modVersion,
                mod_version_parts = createVersionParts(info.modVersion)
            )

            filesDB.insertOne(file)

            logger.info("Created new mod file: $file")
        }

        return null
    }
}

private fun getDatabase(): MongoDatabase {
    System.setProperty("org.litote.mongo.mapping.service", "org.litote.kmongo.jackson.JacksonClassMappingTypeService")

    val dbURL = System.getenv("MONGODB_URL") ?: "mongodb://root:example@localhost:27017/?authSource=admin"
    val client = KMongo.createClient(dbURL)

    return client.getDatabase("blakesmods")
}

private fun createFileInfo(path: Location): FileInfo {
    val parts = path.toString().split("/")
    val fileName = parts.last()
    val modID = parts[2]
    val fileNameParts = fileName.split("-")
    val mavenPath = "/$path"
    val mcVersion = fileNameParts[1]
    val mcVersionGroup = mcVersion.split(".").subList(0, 2).joinToString(".")
    val modVersion = fileNameParts[2].replace(".jar", "")

    return FileInfo(
        fileName = fileName,
        modID = modID,
        mavenPath = mavenPath,
        mcVersion = mcVersion,
        mcVersionGroup = mcVersionGroup,
        modVersion = modVersion
    )
}

private fun createVersionParts(version: String): VersionParts {
    val parts = version.split(".")

    return VersionParts(
        major = parts[0].toIntOrDefault(),
        minor = parts[1].toIntOrDefault(),
        patch = parts[2].toIntOrDefault()
    )
}

private fun String.toIntOrDefault(): Int {
    return try {
        this.toInt()
    } catch (_: Error) {
        0
    }
}
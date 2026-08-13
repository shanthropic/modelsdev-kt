package dev.shantoislam.modelskt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

public class FileCatalogCache(
    cacheDirectory: String,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : CatalogCache {
    private val directory = cacheDirectory.toPath()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(sourceId: String): CachedCatalog? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val base = safeName(sourceId)
            val cachePath = directory / "$base.cache.json"
            if (!fileSystem.exists(cachePath)) return@withContext null
            runCatching {
                val record = fileSystem.source(cachePath).buffer().use {
                    json.decodeFromString<CacheRecord>(it.readUtf8())
                }
                require(record.schema == CACHE_SCHEMA) { "Unsupported catalog cache schema" }
                CachedCatalog(sourceId, record.payload, record.etag, record.refreshedAtEpochMillis)
            }.getOrElse {
                fileSystem.delete(cachePath, mustExist = false)
                null
            }
        }
    }

    override suspend fun write(catalog: CachedCatalog): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            fileSystem.createDirectories(directory)
            val base = safeName(catalog.sourceId)
            atomicWrite(
                "$base.cache.json",
                json.encodeToString(
                    CacheRecord(
                        schema = CACHE_SCHEMA,
                        payload = catalog.payload,
                        etag = catalog.etag,
                        refreshedAtEpochMillis = catalog.refreshedAtEpochMillis,
                    )
                ),
            )
        }
    }

    override suspend fun invalidate(sourceId: String): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            val base = safeName(sourceId)
            fileSystem.delete(directory / "$base.cache.json", mustExist = false)
        }
    }

    private fun atomicWrite(fileName: String, value: String) {
        val target = directory / fileName
        val temporary = directory / "$fileName.tmp"
        fileSystem.sink(temporary, mustCreate = false).buffer().use { sink ->
            sink.writeUtf8(value)
            sink.flush()
        }
        try {
            fileSystem.atomicMove(temporary, target)
        } catch (error: Exception) {
            fileSystem.delete(target, mustExist = false)
            fileSystem.atomicMove(temporary, target)
        }
    }

    private fun safeName(sourceId: String): String = sourceId.map { character ->
        if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
    }.joinToString("")

    private companion object {
        const val CACHE_SCHEMA = 1
    }
}

@Serializable
private data class CacheRecord(
    val schema: Int,
    val payload: String,
    val etag: String?,
    val refreshedAtEpochMillis: Long,
)

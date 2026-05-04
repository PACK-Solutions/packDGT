package com.example.packdgt.service

import com.example.packdgt.api.dto.GenerateRequest
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DocumentStoreService(private val ttlMinutes: Long = 30) {

    private val logger = LoggerFactory.getLogger(DocumentStoreService::class.java)

    data class StoredDocument(
        val id: String,
        val fileName: String,
        var pdfBytes: ByteArray,
        val createdAt: Instant,
        val originalRequest: GenerateRequest? = null
    )

    private val store = ConcurrentHashMap<String, StoredDocument>()

    fun store(fileName: String, pdfBytes: ByteArray, originalRequest: GenerateRequest? = null): String {
        val id = UUID.randomUUID().toString()
        store[id] = StoredDocument(id, fileName, pdfBytes, Instant.now(), originalRequest)
        logger.debug("Document stocké : id={}, fileName={}, size={} octets", id, fileName, pdfBytes.size)
        return id
    }

    fun get(id: String): StoredDocument? {
        val doc = store[id] ?: return null
        if (isExpired(doc)) {
            store.remove(id)
            return null
        }
        return doc
    }

    fun update(id: String, newPdfBytes: ByteArray): StoredDocument? {
        val doc = store[id] ?: return null
        val updated = doc.copy(pdfBytes = newPdfBytes)
        store[id] = updated
        logger.debug("Document mis à jour : id={}, nouvelle taille={} octets", id, newPdfBytes.size)
        return updated
    }

    fun evictExpired() {
        val before = store.size
        store.entries.removeIf { isExpired(it.value) }
        val evicted = before - store.size
        if (evicted > 0) {
            logger.info("Éviction : {} document(s) expiré(s) supprimé(s), {} restant(s)", evicted, store.size)
        }
    }

    private fun isExpired(doc: StoredDocument): Boolean {
        return doc.createdAt.plusSeconds(ttlMinutes * 60).isBefore(Instant.now())
    }
}

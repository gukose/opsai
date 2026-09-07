package com.hotelopai.assistant.application

import java.io.InputStream

data class StoredObject(val reference: String, val contentType: String, val size: Long)

interface AttachmentObjectStorage {
    fun store(reference: String, bytes: ByteArray, contentType: String): StoredObject
    fun read(reference: String): InputStream
}

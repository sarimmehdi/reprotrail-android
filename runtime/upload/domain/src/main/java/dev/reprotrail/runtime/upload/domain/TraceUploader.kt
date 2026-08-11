package dev.reprotrail.runtime.upload.domain

import dev.reprotrail.runtime.upload.domain.model.TraceUpload
import dev.reprotrail.runtime.upload.domain.model.TraceUploadOutcome

/** Sends immutable trace content to its project-scoped hosted ingestion boundary. */
fun interface TraceUploader {
    /** Uploads one trace without changing its session ID or content. */
    suspend fun upload(trace: TraceUpload): TraceUploadOutcome
}

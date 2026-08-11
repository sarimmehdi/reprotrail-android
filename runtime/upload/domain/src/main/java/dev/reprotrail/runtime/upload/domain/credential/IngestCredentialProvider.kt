package dev.reprotrail.runtime.upload.domain.credential

/**
 * Supplies an ingest credential only when an upload attempt begins.
 *
 * Implementations may bridge a host application's manual wiring, Koin graph, Hilt graph, or secure
 * credential store. ReproTrail does not persist or log the returned value.
 */
fun interface IngestCredentialProvider {
    /** Returns the current raw project ingest credential. */
    suspend fun getCredential(): String
}

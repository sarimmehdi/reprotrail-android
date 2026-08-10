package dev.reprotrail.runtime.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.reprotrail.runtime.data.database.ReproTrailDatabase
import dev.reprotrail.runtime.domain.model.StoredTraceAction
import dev.reprotrail.runtime.domain.model.StoredTraceSession
import dev.reprotrail.runtime.domain.model.StoredTraceSessionState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTraceSessionRepositoryTest {
    private lateinit var database: ReproTrailDatabase

    private lateinit var repository: RoomTraceSessionRepositoryImpl

    private fun session(
        id: String,
        createdAtEpochMs: Long,
    ): StoredTraceSession =
        StoredTraceSession(
            id = id,
            startedAt = "2026-08-10T10:00:00Z",
            packageName = "dev.reprotrail.sample",
            policyVersion = "test-policy",
            environmentJson = "{}",
            state = StoredTraceSessionState.ACTIVE,
            droppedActionCount = 0,
            createdAtEpochMs = createdAtEpochMs,
            endedAt = null,
            durationMs = null,
        )

    private fun action(
        sessionId: String,
        sequence: Int,
    ): StoredTraceAction =
        StoredTraceAction(
            id = "action-$sequence",
            sessionId = sessionId,
            sequence = sequence,
            offsetMs = sequence.toLong(),
            payloadJson = "{\"type\":\"tap\"}",
        )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    ReproTrailDatabase::class.java,
                ).build()
        repository = RoomTraceSessionRepositoryImpl(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `actions are stored in sequence order`() =
        runTest {
            // Given
            val session = session(id = "ordered", createdAtEpochMs = 1)
            repository.startSession(session, retainedSessionCount = 3)
            // When
            repository.appendAction(action(session.id, sequence = 1), maximumActionCount = 3)
            repository.appendAction(action(session.id, sequence = 0), maximumActionCount = 3)
            // Then
            assertEquals(
                listOf(0, 1),
                repository.loadSession(session.id)?.actions?.map { it.sequence },
            )
        }

    @Test
    fun `concurrent appends never exceed the action limit`() =
        runTest {
            // Given
            val session = session(id = "bounded", createdAtEpochMs = 1)
            repository.startSession(session, retainedSessionCount = 3)
            // When
            val accepted =
                (0 until 20)
                    .map { sequence ->
                        async {
                            repository.appendAction(action(session.id, sequence), maximumActionCount = 5)
                        }
                    }.awaitAll()
            val stored = repository.loadSession(session.id)
            // Then
            assertEquals(5, accepted.count { it })
            assertEquals(5, stored?.actions?.size)
            assertEquals(15, stored?.session?.droppedActionCount)
        }

    @Test
    fun `starting a session prunes the oldest retained session`() =
        runTest {
            // Given
            repository.startSession(session(id = "oldest", createdAtEpochMs = 1), retainedSessionCount = 2)
            repository.startSession(session(id = "middle", createdAtEpochMs = 2), retainedSessionCount = 2)
            // When
            repository.startSession(session(id = "newest", createdAtEpochMs = 3), retainedSessionCount = 2)
            // Then
            assertNull(repository.loadSession("oldest"))
            assertNotNull(repository.loadSession("middle"))
            assertNotNull(repository.loadSession("newest"))
        }

    @Test
    fun `only completed sessions are available for export`() =
        runTest {
            // Given
            val completed = session(id = "completed", createdAtEpochMs = 1)
            val active = session(id = "active", createdAtEpochMs = 2)
            repository.startSession(completed, retainedSessionCount = 3)
            repository.completeSession(completed.id, endedAt = "2026-08-10T10:00:01Z", durationMs = 1000)
            repository.startSession(active, retainedSessionCount = 3)
            // When
            val latest = repository.loadLatestCompletedSession()
            // Then
            assertEquals("completed", latest?.session?.id)
            assertEquals(StoredTraceSessionState.COMPLETED, latest?.session?.state)
        }
}

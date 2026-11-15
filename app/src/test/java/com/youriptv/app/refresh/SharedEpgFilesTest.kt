package com.youriptv.app.refresh

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedEpgFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun oneDownloadFeedsTwoPlaylistsUntilLastImportReleasesFile() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pool = SharedEpgFiles(scope, folder.newFolder())
            val downloaded = CompletableDeferred<Unit>()
            val secondImport = CompletableDeferred<Unit>()
            var count = 0
            lateinit var staged: File
            val producer: suspend (File, () -> Boolean) -> Unit = { file, _ ->
                count++; staged = file; downloaded.await(); file.writeText("<tv/>")
            }
            val first = async(Dispatchers.Unconfined) { pool.withFile("opaque-source", producer) { it.readText() } }
            val second = async(Dispatchers.Unconfined) { pool.withFile("opaque-source", producer) { secondImport.await(); it.readText() } }
            assertEquals(1, count)
            downloaded.complete(Unit)
            assertEquals("<tv/>", withTimeout(2_000) { first.await() })
            assertTrue(staged.exists())
            secondImport.complete(Unit)
            assertEquals("<tv/>", withTimeout(2_000) { second.await() })
            assertFalse(staged.exists())
        } finally { scope.cancel() }
    }

    @Test fun cancellingOnePlaylistDoesNotCancelSharedSourceNeededByAnother() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pool = SharedEpgFiles(scope, folder.newFolder())
            val release = CompletableDeferred<Unit>()
            var count = 0
            val producer: suspend (File, () -> Boolean) -> Unit = { file, cancelled ->
                count++; release.await(); assertFalse(cancelled()); file.writeText("valid")
            }
            val first = async(Dispatchers.Unconfined) { pool.withFile("shared", producer) { it.readText() } }
            val second = async(Dispatchers.Unconfined) { pool.withFile("shared", producer) { it.readText() } }
            first.cancelAndJoin()
            release.complete(Unit)
            assertEquals("valid", withTimeout(2_000) { second.await() })
            assertEquals(1, count)
        } finally { scope.cancel() }
    }

    @Test fun failedDownloadIsRemovedAndNextRequestCanRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val directory = folder.newFolder()
            val pool = SharedEpgFiles(scope, directory)
            val failure = runCatching {
                pool.withFile("source", { _, _ -> throw java.io.IOException("fixture failure") }) { it.length() }
            }
            assertTrue(failure.isFailure)
            assertTrue(directory.listFiles()!!.isEmpty())
            val value = pool.withFile("source", { file, _ -> file.writeText("retry") }) { it.readText() }
            assertEquals("retry", value)
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun processRecoveryRemovesOnlyOwnedAbandonedGuideFiles() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val directory = folder.newFolder()
            val abandoned = File(directory, "epg-abandoned.xml").apply { writeText("partial") }
            val unrelated = File(directory, "other.xml").apply { writeText("keep") }
            SharedEpgFiles(scope, directory).removeAbandoned()
            assertFalse(abandoned.exists())
            assertEquals("keep", unrelated.readText())
        } finally { scope.cancel() }
    }
}

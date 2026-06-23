package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.storage.preference.api.PreferenceProvider
import cash.z.ecc.android.sdk.internal.storage.preference.model.entry.PreferenceKey
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineTransactionTrackerTest {
    @Test
    fun markTransactions_newTxId_persistsTxId() =
        runBlocking {
            val tracker = PreferenceOfflineTransactionTracker(FakePreferenceProvider())
            val txId = txId(1)

            tracker.markTransactions(listOf(txId))

            assertEquals(setOf(txId), tracker.markedTransactions(listOf(txId)))
        }

    @Test
    fun retainTransactions_missingTxId_removesTxId() =
        runBlocking {
            val tracker = PreferenceOfflineTransactionTracker(FakePreferenceProvider())
            val retained = txId(1)
            val removed = txId(2)
            tracker.markTransactions(listOf(retained, removed))

            tracker.retainTransactions(listOf(retained))

            val marked = tracker.markedTransactions(listOf(retained, removed))

            assertTrue(retained in marked)
            assertFalse(removed in marked)
        }

    private fun txId(value: Byte) = FirstClassByteArray(byteArrayOf(value))
}

private class FakePreferenceProvider : PreferenceProvider {
    private val strings = mutableMapOf<String, String?>()

    override suspend fun hasKey(key: PreferenceKey) = key.key in strings

    override suspend fun putString(key: PreferenceKey, value: String?) {
        strings[key.key] = value
    }

    override suspend fun getString(key: PreferenceKey): String? = strings[key.key]

    override fun observe(key: PreferenceKey): Flow<Unit> = emptyFlow()

    override suspend fun clearPreferences(): Boolean {
        strings.clear()
        return true
    }
}

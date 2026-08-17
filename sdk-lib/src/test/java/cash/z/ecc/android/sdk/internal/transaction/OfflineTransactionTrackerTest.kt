package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.ext.toHexReversed
import cash.z.ecc.android.sdk.internal.storage.preference.api.PreferenceProvider
import cash.z.ecc.android.sdk.internal.storage.preference.model.entry.PreferenceKey
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineTransactionTrackerTest {
    @Test
    fun markedTransactions_storedTxId_returnsCandidate() =
        runBlocking {
            val preferences = FakePreferenceProvider()
            val tracker = PreferenceOfflineTransactionTracker(preferences, SCOPE)
            val txId = txId(1)
            preferences.putString(legacyKey(), txId.legacyTrackerKey())

            assertEquals(setOf(txId), tracker.markedTransactions(listOf(txId)))
        }

    @Test
    fun markedTransactions_missingCandidate_keepsTxIdStored() =
        runBlocking {
            val preferences = FakePreferenceProvider()
            val tracker = PreferenceOfflineTransactionTracker(preferences, SCOPE)
            val queried = txId(1)
            val notQueried = txId(2)
            preferences.putString(
                legacyKey(),
                listOf(queried, notQueried).joinToString(SEPARATOR) { it.legacyTrackerKey() }
            )

            val queriedMarked = tracker.markedTransactions(listOf(queried))
            val notQueriedMarked = tracker.markedTransactions(listOf(notQueried))

            assertTrue(queried in queriedMarked)
            assertTrue(notQueried in notQueriedMarked)
        }

    private fun txId(value: Byte) = FirstClassByteArray(byteArrayOf(value))

    private fun FirstClassByteArray.legacyTrackerKey() = byteArray.toHexReversed()

    private fun legacyKey() = PreferenceKey("${KEY_PREFIX}_$SCOPE")

    private companion object {
        private const val SCOPE = "scope"
        private const val KEY_PREFIX = "offline_created_transaction_ids"
        private const val SEPARATOR = ","
    }
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

package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.ext.toHexReversed
import cash.z.ecc.android.sdk.internal.storage.preference.api.PreferenceProvider
import cash.z.ecc.android.sdk.internal.storage.preference.model.entry.PreferenceKey
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface OfflineTransactionTracker {
    suspend fun markTransactions(txIds: Collection<FirstClassByteArray>)

    suspend fun markedTransactions(txIds: Collection<FirstClassByteArray>): Set<FirstClassByteArray>

    suspend fun retainTransactions(txIds: Collection<FirstClassByteArray>)
}

internal object NoOpOfflineTransactionTracker : OfflineTransactionTracker {
    override suspend fun markTransactions(txIds: Collection<FirstClassByteArray>) = Unit

    override suspend fun markedTransactions(txIds: Collection<FirstClassByteArray>) = emptySet<FirstClassByteArray>()

    override suspend fun retainTransactions(txIds: Collection<FirstClassByteArray>) = Unit
}

internal class PreferenceOfflineTransactionTracker(
    private val preferenceProvider: PreferenceProvider
) : OfflineTransactionTracker {
    private val mutex = Mutex()

    override suspend fun markTransactions(txIds: Collection<FirstClassByteArray>) {
        if (txIds.isEmpty()) return

        mutex.withLock {
            val stored = readTransactionIds().toMutableSet()
            stored.addAll(txIds.map { it.trackerKey() })
            writeTransactionIds(stored)
        }
    }

    override suspend fun markedTransactions(txIds: Collection<FirstClassByteArray>): Set<FirstClassByteArray> =
        mutex.withLock {
            val stored = readTransactionIds()
            txIds.filterTo(mutableSetOf()) { it.trackerKey() in stored }
        }

    override suspend fun retainTransactions(txIds: Collection<FirstClassByteArray>) {
        mutex.withLock {
            val activeTxIds = txIds.mapTo(mutableSetOf()) { it.trackerKey() }
            val retained = readTransactionIds().filterTo(mutableSetOf()) { it in activeTxIds }
            writeTransactionIds(retained)
        }
    }

    private suspend fun readTransactionIds(): Set<String> =
        preferenceProvider
            .getString(KEY)
            ?.split(SEPARATOR)
            ?.filterTo(mutableSetOf()) { it.isNotBlank() }
            .orEmpty()

    private suspend fun writeTransactionIds(txIds: Set<String>) {
        preferenceProvider.putString(
            key = KEY,
            value = txIds.sorted().joinToString(SEPARATOR)
        )
    }

    private companion object {
        private val KEY = PreferenceKey("offline_created_transaction_ids")
        private const val SEPARATOR = ","
    }
}

private fun FirstClassByteArray.trackerKey() = byteArray.toHexReversed()

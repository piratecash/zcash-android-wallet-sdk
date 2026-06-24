package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.ext.toHexReversed
import cash.z.ecc.android.sdk.internal.storage.preference.api.PreferenceProvider
import cash.z.ecc.android.sdk.internal.storage.preference.model.entry.PreferenceKey
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

internal interface OfflineTransactionTracker {
    suspend fun markTransactions(txIds: Collection<FirstClassByteArray>)

    suspend fun markedTransactions(txIds: Collection<FirstClassByteArray>): Set<FirstClassByteArray>

    suspend fun retainTransactions(txIds: Collection<FirstClassByteArray>)

    suspend fun <T> withOfflineCreation(block: suspend () -> T): T

    fun isOfflineCreationInProgress(): Boolean
}

internal object NoOpOfflineTransactionTracker : OfflineTransactionTracker {
    override suspend fun markTransactions(txIds: Collection<FirstClassByteArray>) = Unit

    override suspend fun markedTransactions(txIds: Collection<FirstClassByteArray>) = emptySet<FirstClassByteArray>()

    override suspend fun retainTransactions(txIds: Collection<FirstClassByteArray>) = Unit

    override suspend fun <T> withOfflineCreation(block: suspend () -> T): T = block()

    override fun isOfflineCreationInProgress(): Boolean = false
}

internal class PreferenceOfflineTransactionTracker(
    private val preferenceProvider: PreferenceProvider,
    scope: String
) : OfflineTransactionTracker {
    private val mutex = Mutex()
    private val offlineCreationDepth = AtomicInteger(0)
    private val key = PreferenceKey("${KEY_PREFIX}_$scope")

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

    override suspend fun <T> withOfflineCreation(block: suspend () -> T): T {
        offlineCreationDepth.incrementAndGet()
        return try {
            block()
        } finally {
            offlineCreationDepth.decrementAndGet()
        }
    }

    override fun isOfflineCreationInProgress(): Boolean = offlineCreationDepth.get() > 0

    private suspend fun readTransactionIds(): Set<String> =
        preferenceProvider
            .getString(key)
            ?.split(SEPARATOR)
            ?.filterTo(mutableSetOf()) { it.isNotBlank() }
            .orEmpty()

    private suspend fun writeTransactionIds(txIds: Set<String>) {
        preferenceProvider.putString(
            key = key,
            value = txIds.sorted().joinToString(SEPARATOR)
        )
    }

    private companion object {
        private const val KEY_PREFIX = "offline_created_transaction_ids"
        private const val SEPARATOR = ","
    }
}

private fun FirstClassByteArray.trackerKey() = byteArray.toHexReversed()

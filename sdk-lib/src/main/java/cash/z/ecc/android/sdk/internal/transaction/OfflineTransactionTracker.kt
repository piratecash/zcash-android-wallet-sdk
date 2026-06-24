package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.ext.toHexReversed
import cash.z.ecc.android.sdk.internal.storage.preference.api.PreferenceProvider
import cash.z.ecc.android.sdk.internal.storage.preference.model.entry.PreferenceKey
import cash.z.ecc.android.sdk.model.FirstClassByteArray

internal interface OfflineTransactionTracker {
    suspend fun markedTransactions(txIds: Collection<FirstClassByteArray>): Set<FirstClassByteArray>
}

internal object NoOpOfflineTransactionTracker : OfflineTransactionTracker {
    override suspend fun markedTransactions(txIds: Collection<FirstClassByteArray>) = emptySet<FirstClassByteArray>()
}

internal class PreferenceOfflineTransactionTracker(
    private val preferenceProvider: PreferenceProvider,
    scope: String
) : OfflineTransactionTracker {
    private val key = PreferenceKey("${KEY_PREFIX}_$scope")

    override suspend fun markedTransactions(txIds: Collection<FirstClassByteArray>): Set<FirstClassByteArray> {
        val stored = readTransactionIds()
        return txIds.filterTo(mutableSetOf()) { it.trackerKey() in stored }
    }

    private suspend fun readTransactionIds(): Set<String> =
        preferenceProvider
            .getString(key)
            ?.split(SEPARATOR)
            ?.filterTo(mutableSetOf()) { it.isNotBlank() }
            .orEmpty()

    private companion object {
        private const val KEY_PREFIX = "offline_created_transaction_ids"
        private const val SEPARATOR = ","
    }
}

private fun FirstClassByteArray.trackerKey() = byteArray.toHexReversed()

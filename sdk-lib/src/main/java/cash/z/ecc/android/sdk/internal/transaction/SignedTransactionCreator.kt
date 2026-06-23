package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.model.EncodedTransaction
import cash.z.ecc.android.sdk.model.SignedRawZcashTransaction

internal class SignedTransactionCreator(
    private val offlineTransactionTracker: OfflineTransactionTracker
) {
    suspend fun create(transactions: List<EncodedTransaction>): List<SignedRawZcashTransaction> {
        offlineTransactionTracker.markTransactions(transactions.map { it.txId })
        return transactions.map { it.toSignedRawZcashTransaction() }
    }
}

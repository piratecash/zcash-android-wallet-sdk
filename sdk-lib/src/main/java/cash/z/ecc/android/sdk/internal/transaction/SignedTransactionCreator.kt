package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.model.EncodedTransaction
import cash.z.ecc.android.sdk.model.SignedRawZcashTransaction

internal class SignedTransactionCreator(
    private val offlineTransactionTracker: OfflineTransactionTracker
) {
    suspend fun create(createTransactions: suspend () -> List<EncodedTransaction>): List<SignedRawZcashTransaction> =
        offlineTransactionTracker.withOfflineCreation {
            val transactions = createTransactions()
            offlineTransactionTracker.markTransactions(transactions.map { it.txId })
            transactions.map { it.toSignedRawZcashTransaction() }
        }
}

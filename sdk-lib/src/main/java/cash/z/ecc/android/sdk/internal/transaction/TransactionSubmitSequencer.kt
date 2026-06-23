package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.model.EncodedTransaction
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map

internal class TransactionSubmitSequencer(
    private val txManager: OutboundTransactionManager
) {
    fun submit(transactions: List<EncodedTransaction>): Flow<TransactionSubmitResult> {
        var anySubmissionFailed = false
        return transactions
            .asFlow()
            .map { transaction ->
                if (anySubmissionFailed) {
                    TransactionSubmitResult.NotAttempted(transaction.txId)
                } else {
                    txManager.submit(transaction).also { submission ->
                        when (submission) {
                            is TransactionSubmitResult.Success -> Unit

                            is TransactionSubmitResult.Failure,
                            is TransactionSubmitResult.NotAttempted -> anySubmissionFailed = true
                        }
                    }
                }
            }
    }
}

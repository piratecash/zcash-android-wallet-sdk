package cash.z.ecc.android.sdk.block.processor

import cash.z.ecc.android.sdk.block.processor.CompactBlockProcessor.Companion.TRANSACTION_RESUBMIT_RETRIES
import cash.z.ecc.android.sdk.exception.LightWalletException
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.internal.Twig
import cash.z.ecc.android.sdk.internal.ext.retryUpToAndContinue
import cash.z.ecc.android.sdk.internal.model.DbTransactionOverview
import cash.z.ecc.android.sdk.internal.repository.DerivedDataRepository
import cash.z.ecc.android.sdk.internal.transaction.OfflineTransactionTracker
import cash.z.ecc.android.sdk.internal.transaction.OutboundTransactionManager
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.TransactionSubmitResult

internal class UnminedTransactionResubmitter(
    private val repository: DerivedDataRepository,
    private val txManager: OutboundTransactionManager,
    private val offlineTransactionTracker: OfflineTransactionTracker
) {
    @Throws(TransactionEncoderException.TransactionNotFoundException::class)
    suspend fun resubmit(blockHeight: BlockHeight?) {
        if (blockHeight != null && !offlineTransactionTracker.isOfflineCreationInProgress()) {
            resubmitTransactions(blockHeight)
        } else if (blockHeight != null) {
            Twig.debug { "Trx resubmission skipped while offline transaction creation is in progress" }
        }
    }

    private suspend fun resubmitTransactions(blockHeight: BlockHeight) {
        val transactions = repository.findUnminedTransactionsWithinExpiry(blockHeight)
        val transactionIds = transactions.map { it.rawId }
        offlineTransactionTracker.retainTransactions(transactionIds)
        val offlineCreatedTransactionIds = offlineTransactionTracker.markedTransactions(transactionIds)

        val transactionsForResubmission = mutableListOf<DbTransactionOverview>()
        var skippedOfflineTransactions = 0
        transactions.forEach { transaction ->
            if (transaction.rawId in offlineCreatedTransactionIds) {
                skippedOfflineTransactions++
            } else {
                transactionsForResubmission += transaction
            }
        }

        Twig.debug {
            "Trx resubmission: ${transactions.size}, " +
                transactions.joinToString(separator = ", ") { it.txIdString() } +
                ", skipped offline-created: $skippedOfflineTransactions"
        }

        if (transactionsForResubmission.isEmpty()) {
            Twig.debug { "Trx resubmission: No trx for resubmission found" }
        } else {
            submitTransactions(transactionsForResubmission)
        }
    }

    private suspend fun submitTransactions(transactions: List<DbTransactionOverview>) {
        transactions.forEach { transaction ->
            val trxForResubmission =
                repository.findEncodedTransactionByTxId(transaction.rawId)
                    ?: throw TransactionEncoderException.TransactionNotFoundException(transaction.rawId)

            Twig.debug { "Trx resubmission: Found: ${trxForResubmission.txIdString()}" }

            retryUpToAndContinue(TRANSACTION_RESUBMIT_RETRIES) {
                when (val response = txManager.submit(trxForResubmission)) {
                    is TransactionSubmitResult.Success -> {
                        Twig.info { "Trx resubmission success: ${response.txIdString()}" }
                    }

                    is TransactionSubmitResult.Failure -> {
                        Twig.error { "Trx resubmission failure: ${response.description}" }
                        throw LightWalletException.TransactionSubmitException(
                            response.code,
                            response.description,
                        )
                    }

                    is TransactionSubmitResult.NotAttempted -> {
                        Twig.warn { "Trx resubmission not attempted: ${response.txIdString()}" }
                    }
                }
            }
        }
    }
}

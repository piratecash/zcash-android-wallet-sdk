package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.model.EncodedTransaction
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TransactionSubmitSequencerTest {
    @Test
    fun submit_firstFailure_marksRemainingNotAttempted() =
        runBlocking {
            val first = transaction(1)
            val second = transaction(2)
            val txManager =
                SubmitOnlyTransactionManager(
                    TransactionSubmitResult.Failure(first.txId, grpcError = true, code = 1, description = "failed")
                )
            val sequencer = TransactionSubmitSequencer(txManager)

            val results = sequencer.submit(listOf(first, second)).toList()

            assertIs<TransactionSubmitResult.Failure>(results[0])
            assertIs<TransactionSubmitResult.NotAttempted>(results[1])
            assertEquals(listOf(first.txId), txManager.submittedTxIds)
        }

    @Test
    fun submit_allSuccess_submitsEveryTransaction() =
        runBlocking {
            val first = transaction(1)
            val second = transaction(2)
            val txManager =
                SubmitOnlyTransactionManager(
                    TransactionSubmitResult.Success(first.txId),
                    TransactionSubmitResult.Success(second.txId)
                )
            val sequencer = TransactionSubmitSequencer(txManager)

            val results = sequencer.submit(listOf(first, second)).toList()

            assertEquals(2, results.size)
            assertEquals(listOf(first.txId, second.txId), txManager.submittedTxIds)
        }

    private fun transaction(txId: Byte) =
        EncodedTransaction(
            txId = FirstClassByteArray(byteArrayOf(txId)),
            raw = FirstClassByteArray(byteArrayOf(txId, 0x10)),
            expiryHeight = BlockHeight.new(1_000)
        )
}

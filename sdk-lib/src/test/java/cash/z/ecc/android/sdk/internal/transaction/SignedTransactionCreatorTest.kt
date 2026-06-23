package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.model.EncodedTransaction
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SignedTransactionCreatorTest {
    @Test
    fun create_validTransactions_returnsSignedTransactionsAndMarksTxIds() =
        runBlocking {
            val tracker = RecordingOfflineTransactionTracker()
            val creator = SignedTransactionCreator(tracker)
            val first = transaction(1, 11, 1_000)
            val second = transaction(2, 12, 2_000)

            val signed = creator.create(listOf(first, second))

            assertEquals(listOf(first.txId, second.txId), tracker.markedTxIds)
            assertEquals(listOf(first.raw, second.raw), signed.map { it.raw })
            assertEquals(listOf(first.txId, second.txId), signed.map { it.txId })
            assertEquals(listOf(first.expiryHeight, second.expiryHeight), signed.map { it.expiryHeight })
        }

    private fun transaction(txId: Byte, raw: Byte, expiryHeight: Long) =
        EncodedTransaction(
            txId = FirstClassByteArray(byteArrayOf(txId)),
            raw = FirstClassByteArray(byteArrayOf(raw)),
            expiryHeight = BlockHeight.new(expiryHeight)
        )
}

private class RecordingOfflineTransactionTracker : OfflineTransactionTracker {
    val markedTxIds = mutableListOf<FirstClassByteArray>()

    override suspend fun markTransactions(txIds: Collection<FirstClassByteArray>) {
        markedTxIds += txIds
    }

    override suspend fun markedTransactions(txIds: Collection<FirstClassByteArray>) = emptySet<FirstClassByteArray>()

    override suspend fun retainTransactions(txIds: Collection<FirstClassByteArray>) = Unit
}

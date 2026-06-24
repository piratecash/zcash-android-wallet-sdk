package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.model.EncodedTransaction
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignedTransactionCreatorTest {
    @Test
    fun create_validTransactions_returnsSignedTransactionsAndMarksTxIds() =
        runBlocking {
            val tracker = RecordingOfflineTransactionTracker()
            val creator = SignedTransactionCreator(tracker)
            val first = transaction(1, 11, 1_000)
            val second = transaction(2, 12, 2_000)

            val signed =
                creator.create {
                    tracker.inProgressDuringCreate = tracker.isOfflineCreationInProgress()
                    listOf(first, second)
                }

            assertEquals(listOf(first.txId, second.txId), tracker.markedTxIds)
            assertEquals(listOf(first.raw, second.raw), signed.map { it.raw })
            assertEquals(listOf(first.txId, second.txId), signed.map { it.txId })
            assertEquals(listOf(first.expiryHeight, second.expiryHeight), signed.map { it.expiryHeight })
            assertTrue(tracker.inProgressDuringCreate)
            assertTrue(tracker.inProgressDuringMark)
            assertFalse(tracker.isOfflineCreationInProgress())
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
    var inProgressDuringCreate = false
    var inProgressDuringMark = false
    private var offlineCreationDepth = 0

    override suspend fun markTransactions(txIds: Collection<FirstClassByteArray>) {
        inProgressDuringMark = isOfflineCreationInProgress()
        markedTxIds += txIds
    }

    override suspend fun markedTransactions(txIds: Collection<FirstClassByteArray>) = emptySet<FirstClassByteArray>()

    override suspend fun retainTransactions(txIds: Collection<FirstClassByteArray>) = Unit

    override suspend fun <T> withOfflineCreation(block: suspend () -> T): T {
        offlineCreationDepth++
        return try {
            block()
        } finally {
            offlineCreationDepth--
        }
    }

    override fun isOfflineCreationInProgress(): Boolean = offlineCreationDepth > 0
}

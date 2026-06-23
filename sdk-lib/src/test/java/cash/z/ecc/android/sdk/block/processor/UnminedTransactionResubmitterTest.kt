package cash.z.ecc.android.sdk.block.processor

import cash.z.ecc.android.sdk.internal.model.DbBlock
import cash.z.ecc.android.sdk.internal.model.DbTransactionOverview
import cash.z.ecc.android.sdk.internal.model.EncodedTransaction
import cash.z.ecc.android.sdk.internal.model.OutputProperties
import cash.z.ecc.android.sdk.internal.repository.DerivedDataRepository
import cash.z.ecc.android.sdk.internal.transaction.OfflineTransactionTracker
import cash.z.ecc.android.sdk.internal.transaction.SubmitOnlyTransactionManager
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.TransactionId
import cash.z.ecc.android.sdk.model.TransactionRecipient
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import cash.z.ecc.android.sdk.model.Zatoshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class UnminedTransactionResubmitterTest {
    @Test
    fun resubmit_offlineCreatedTransaction_skipsSubmit() =
        runBlocking {
            val offline = transaction(1)
            val online = transaction(2)
            val repository =
                FakeDerivedDataRepository(
                    listOf(overview(offline), overview(online)),
                    listOf(offline, online)
                )
            val tracker = SetOfflineTransactionTracker(offline.txId)
            val txManager =
                SubmitOnlyTransactionManager(TransactionSubmitResult.Success(online.txId))
            val resubmitter = UnminedTransactionResubmitter(repository, txManager, tracker)

            resubmitter.resubmit(BlockHeight.new(1_500))

            assertEquals(listOf(offline.txId, online.txId), tracker.retainedTxIds)
            assertEquals(listOf(online.txId), txManager.submittedTxIds)
            assertEquals(listOf(online.txId), repository.requestedEncodedTxIds)
        }

    @Test
    fun resubmit_noBlockHeight_skipsRepository() =
        runBlocking {
            val repository = FakeDerivedDataRepository(emptyList(), emptyList())
            val tracker = SetOfflineTransactionTracker()
            val txManager = SubmitOnlyTransactionManager()
            val resubmitter = UnminedTransactionResubmitter(repository, txManager, tracker)

            resubmitter.resubmit(null)

            assertEquals(0, repository.findUnminedCalls)
            assertEquals(emptyList(), txManager.submittedTxIds)
        }

    private fun transaction(txId: Byte) =
        EncodedTransaction(
            txId = FirstClassByteArray(byteArrayOf(txId)),
            raw = FirstClassByteArray(byteArrayOf(txId, 0x10)),
            expiryHeight = BlockHeight.new(2_000)
        )

    private fun overview(transaction: EncodedTransaction) =
        DbTransactionOverview(
            rawId = transaction.txId,
            minedHeight = null,
            expiryHeight = transaction.expiryHeight,
            index = null,
            raw = transaction.raw,
            isSentTransaction = true,
            netValue = Zatoshi(0),
            totalSpent = Zatoshi(1),
            totalReceived = Zatoshi(0),
            feePaid = null,
            isChange = false,
            receivedNoteCount = 0,
            sentNoteCount = 1,
            memoCount = 0,
            blockTimeEpochSeconds = null,
            isShielding = false,
            isExpiredUnmined = false
        )
}

private class SetOfflineTransactionTracker(
    private vararg val offlineTxIds: FirstClassByteArray
) : OfflineTransactionTracker {
    val retainedTxIds = mutableListOf<FirstClassByteArray>()

    override suspend fun markTransactions(txIds: Collection<FirstClassByteArray>) = Unit

    override suspend fun markedTransactions(txIds: Collection<FirstClassByteArray>): Set<FirstClassByteArray> =
        txIds.filterTo(mutableSetOf()) { it in offlineTxIds }

    override suspend fun retainTransactions(txIds: Collection<FirstClassByteArray>) {
        retainedTxIds.clear()
        retainedTxIds += txIds
    }
}

private class FakeDerivedDataRepository(
    private val unminedTransactions: List<DbTransactionOverview>,
    encodedTransactions: List<EncodedTransaction>
) : DerivedDataRepository {
    private val encodedByTxId = encodedTransactions.associateBy { it.txId }
    val requestedEncodedTxIds = mutableListOf<FirstClassByteArray>()
    var findUnminedCalls = 0

    override suspend fun firstUnenhancedHeight(): BlockHeight? = null

    override suspend fun findEncodedTransactionByTxId(txId: FirstClassByteArray): EncodedTransaction? {
        requestedEncodedTxIds += txId
        return encodedByTxId[txId]
    }

    override suspend fun findUnminedTransactionsWithinExpiry(blockHeight: BlockHeight): List<DbTransactionOverview> {
        findUnminedCalls++
        return unminedTransactions
    }

    override suspend fun getOldestTransaction(): DbTransactionOverview? = null

    override suspend fun findMinedHeight(rawTransactionId: ByteArray): BlockHeight? = null

    override suspend fun findMatchingTransactionId(rawTransactionId: ByteArray): Long? = null

    override suspend fun getTransactionCount(): Long = 0

    override fun invalidate() = Unit

    override val allTransactions: Flow<List<DbTransactionOverview>> = emptyFlow()

    override suspend fun getTransactions(accountUuid: AccountUuid): Flow<List<DbTransactionOverview>> = emptyFlow()

    override fun getOutputProperties(transactionId: TransactionId): Flow<OutputProperties> = emptyFlow()

    override fun getTransactionsByMemoSubstring(query: String): Flow<List<TransactionId>> = emptyFlow()

    override fun getRecipients(transactionId: TransactionId): Flow<TransactionRecipient> = emptyFlow()

    override suspend fun debugQuery(query: String): String = ""

    override suspend fun findBlockByHeight(blockHeight: BlockHeight): DbBlock? = null

    override suspend fun close() = Unit

    override suspend fun isClosed() = false
}

package cash.z.ecc.android.sdk.internal

import cash.z.ecc.android.sdk.internal.model.JniAccount
import cash.z.ecc.android.sdk.internal.model.JniAccountUsk
import cash.z.ecc.android.sdk.internal.model.JniBlockMeta
import cash.z.ecc.android.sdk.internal.model.JniEncodedTransaction
import cash.z.ecc.android.sdk.internal.model.JniRewindResult
import cash.z.ecc.android.sdk.internal.model.JniScanRange
import cash.z.ecc.android.sdk.internal.model.JniScanSummary
import cash.z.ecc.android.sdk.internal.model.JniSingleUseTransparentAddress
import cash.z.ecc.android.sdk.internal.model.JniSubtreeRoot
import cash.z.ecc.android.sdk.internal.model.JniTransactionDataRequest
import cash.z.ecc.android.sdk.internal.model.JniUnifiedSpendingKey
import cash.z.ecc.android.sdk.internal.model.JniWalletSummary
import cash.z.ecc.android.sdk.internal.model.ProposalUnsafe
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.UnifiedSpendingKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypesafeBackendMutationGateTest {
    @Test
    fun putUtxo_runningWriter_waitsForGate() =
        runBlocking {
            val backend = BlockingBackend()
            val typesafeBackend = TypesafeBackendImpl(backend, WalletDbMutationGate())
            val initJob = async { typesafeBackend.initDataDb(null) }
            backend.initDataDbEntered.await()

            val putJob =
                async {
                    typesafeBackend.putUtxo(
                        txId = byteArrayOf(1),
                        index = 0,
                        script = byteArrayOf(2),
                        value = 3,
                        height = BlockHeight.new(4)
                    )
                }
            yield()

            assertFalse(backend.putUtxoEntered.isCompleted)
            backend.releaseInitDataDb.complete(Unit)
            initJob.await()
            putJob.await()
            assertTrue(backend.putUtxoEntered.isCompleted)
        }

    @Test
    fun decryptAndStoreTransaction_runningWriter_waitsForGate() =
        runBlocking {
            val backend = BlockingBackend()
            val typesafeBackend = TypesafeBackendImpl(backend, WalletDbMutationGate())
            val initJob = async { typesafeBackend.initDataDb(null) }
            backend.initDataDbEntered.await()

            val decryptJob =
                async {
                    typesafeBackend.decryptAndStoreTransaction(byteArrayOf(1), BlockHeight.new(2))
                }
            yield()

            assertFalse(backend.decryptAndStoreEntered.isCompleted)
            backend.releaseInitDataDb.complete(Unit)
            initJob.await()
            decryptJob.await()
            assertTrue(backend.decryptAndStoreEntered.isCompleted)
        }

    @Test
    fun createProposedTransactionsDetached_runningWriter_waitsForGate() =
        runBlocking {
            val backend = BlockingBackend()
            val typesafeBackend = TypesafeBackendImpl(backend, WalletDbMutationGate())
            val initJob = async { typesafeBackend.initDataDb(null) }
            backend.initDataDbEntered.await()

            val createJob =
                async {
                    typesafeBackend.createProposedTransactionsDetached(fakeProposal(), fakeUsk())
                }
            yield()

            assertFalse(backend.createDetachedEntered.isCompleted)
            backend.releaseInitDataDb.complete(Unit)
            initJob.await()
            createJob.await()
            assertTrue(backend.createDetachedEntered.isCompleted)
        }
}

@Suppress("TooManyFunctions")
private class BlockingBackend : Backend {
    val initDataDbEntered = CompletableDeferred<Unit>()
    val releaseInitDataDb = CompletableDeferred<Unit>()
    val putUtxoEntered = CompletableDeferred<Unit>()
    val decryptAndStoreEntered = CompletableDeferred<Unit>()
    val createDetachedEntered = CompletableDeferred<Unit>()

    override val dataDbFile: File = File("unused")
    override val networkId: Int = 0

    override suspend fun initDataDb(seed: ByteArray?): Int {
        initDataDbEntered.complete(Unit)
        releaseInitDataDb.await()
        return 0
    }

    override suspend fun putUtxo(
        txId: ByteArray,
        index: Int,
        script: ByteArray,
        value: Long,
        height: Long
    ) {
        putUtxoEntered.complete(Unit)
    }

    override suspend fun decryptAndStoreTransaction(tx: ByteArray, minedHeight: Long?): ByteArray {
        decryptAndStoreEntered.complete(Unit)
        return byteArrayOf(9)
    }

    override suspend fun getAccounts(): List<JniAccount> = unused()

    override suspend fun createAccount(
        accountName: String,
        keySource: String?,
        seed: ByteArray,
        treeState: ByteArray,
        recoverUntil: Long?
    ): JniAccountUsk = unused()

    override suspend fun importAccountUfvk(
        accountName: String,
        keySource: String?,
        ufvk: String,
        treeState: ByteArray,
        recoverUntil: Long?,
        purpose: Int,
        seedFingerprint: ByteArray?,
        zip32AccountIndex: Long?
    ): JniAccount = unused()

    override suspend fun getAccountForUfvk(ufvk: String): JniAccount? = unused()

    override suspend fun proposeTransferFromUri(accountUuid: ByteArray, uri: String): ProposalUnsafe = unused()

    override suspend fun proposeTransfer(
        accountUuid: ByteArray,
        to: String,
        value: Long,
        memo: ByteArray?
    ): ProposalUnsafe = unused()

    override suspend fun proposeOrchardToIronwoodMigration(accountUuid: ByteArray): ProposalUnsafe = unused()

    override suspend fun proposeShielding(
        accountUuid: ByteArray,
        shieldingThreshold: Long,
        memo: ByteArray?,
        transparentReceiver: String?
    ): ProposalUnsafe? = unused()

    override suspend fun createProposedTransactions(
        proposal: ProposalUnsafe,
        unifiedSpendingKey: ByteArray
    ): List<ByteArray> = unused()

    override suspend fun createProposedTransactionsDetached(
        proposal: ProposalUnsafe,
        unifiedSpendingKey: ByteArray
    ): List<JniEncodedTransaction> {
        createDetachedEntered.complete(Unit)
        return listOf(
            JniEncodedTransaction(
                txId = byteArrayOf(1),
                raw = byteArrayOf(2),
                expiryHeight = 3
            )
        )
    }

    override suspend fun createPcztFromProposal(accountUuid: ByteArray, proposal: ProposalUnsafe): ByteArray = unused()

    override suspend fun redactPcztForSigner(pczt: ByteArray): ByteArray = unused()

    override suspend fun pcztRequiresSaplingProofs(pczt: ByteArray): Boolean = unused()

    override suspend fun addProofsToPczt(pczt: ByteArray): ByteArray = unused()

    override suspend fun extractAndStoreTxFromPczt(
        pcztWithProofs: ByteArray,
        pcztWithSignatures: ByteArray
    ): ByteArray = unused()

    override suspend fun getCurrentAddress(accountUuid: ByteArray): String = unused()

    override suspend fun getSingleUseTransparentAddress(
        accountUuid: ByteArray
    ): JniSingleUseTransparentAddress = unused()

    override suspend fun getNextAvailableAddress(
        accountUuid: ByteArray,
        receiverFlags: Int
    ): String = unused()

    override suspend fun listTransparentReceivers(accountUuid: ByteArray): List<String> = unused()

    override fun getBranchIdForHeight(height: Long): Long = unused()

    override suspend fun rewindToHeight(height: Long): JniRewindResult = unused()

    override suspend fun truncateToChainState(chainState: ByteArray) = unused<Unit>()

    override suspend fun getLatestCacheHeight(): Long? = unused()

    override suspend fun findBlockMetadata(height: Long): JniBlockMeta? = unused()

    override suspend fun rewindBlockMetadataToHeight(height: Long) = unused<Unit>()

    override suspend fun getTotalTransparentBalance(address: String): Long = unused()

    override suspend fun setTransactionStatus(txId: ByteArray, status: Long) = unused<Unit>()

    override suspend fun getMemoAsUtf8(txId: ByteArray, protocol: Int, outputIndex: Int): String? = unused()

    override suspend fun putSubtreeRoots(
        saplingStartIndex: Long,
        saplingRoots: List<JniSubtreeRoot>,
        orchardStartIndex: Long,
        orchardRoots: List<JniSubtreeRoot>,
        ironwoodStartIndex: Long,
        ironwoodRoots: List<JniSubtreeRoot>
    ) = unused<Unit>()

    override suspend fun updateChainTip(height: Long) = unused<Unit>()

    override suspend fun getFullyScannedHeight(): Long? = unused()

    override suspend fun getMaxScannedHeight(): Long? = unused()

    override suspend fun scanBlocks(fromHeight: Long, fromState: ByteArray, limit: Long): JniScanSummary = unused()

    override suspend fun transactionDataRequests(): List<JniTransactionDataRequest> = unused()

    override suspend fun fixWitnesses() = unused<Unit>()

    override suspend fun getWalletSummary(): JniWalletSummary = unused()

    override suspend fun suggestScanRanges(): List<JniScanRange> = unused()

    override fun getSaplingReceiver(ua: String): String? = unused()

    override fun getTransparentReceiver(ua: String): String? = unused()

    override suspend fun initBlockMetaDb(): Int = unused()

    override suspend fun writeBlockMetadata(blockMetadata: List<JniBlockMeta>) = unused<Unit>()

    override fun isValidSaplingAddr(addr: String): Boolean = unused()

    override fun isValidTransparentAddr(addr: String): Boolean = unused()

    override fun isValidUnifiedAddr(addr: String): Boolean = unused()

    override fun isValidTexAddr(addr: String): Boolean = unused()

    override suspend fun deleteAccount(accountUuid: ByteArray): Boolean = unused()

    override suspend fun isSeedRelevantToAnyDerivedAccounts(seed: ByteArray): Boolean = unused()
}

private fun <T> unused(): T = error("Unused in this test")

private fun fakeProposal() = Proposal.fromByteArray(byteArrayOf(0x10, 0x03))

private fun fakeUsk() = UnifiedSpendingKey(JniUnifiedSpendingKey(byteArrayOf(1)))

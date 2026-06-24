package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.internal.SaplingParamFetcher
import cash.z.ecc.android.sdk.internal.SaplingParamTool
import cash.z.ecc.android.sdk.internal.SaplingParamToolProperties
import cash.z.ecc.android.sdk.internal.SaplingParameters
import cash.z.ecc.android.sdk.internal.TypesafeBackend
import cash.z.ecc.android.sdk.internal.model.EncodedTransaction
import cash.z.ecc.android.sdk.internal.model.JniUnifiedSpendingKey
import cash.z.ecc.android.sdk.internal.repository.DerivedDataRepository
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.UnifiedSpendingKey
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TransactionEncoderImplTest {
    @Test
    fun createProposedTransactionsDetached_success_usesBackendResultWithoutRepositoryRead() =
        runBlocking {
            val detachedTransaction = encodedTransaction(txIdValue = 1, rawValue = 2)
            val backend = RecordingTypesafeBackend(detachedTransactions = listOf(detachedTransaction))
            val repository = RecordingDerivedDataRepository()
            val encoder = transactionEncoder(backend, repository)

            val result = encoder.createProposedTransactionsDetached(fakeProposal(), fakeUsk())

            assertEquals(listOf(detachedTransaction), result)
            assertEquals(1, backend.detachedCalls)
            assertEquals(0, backend.onlineCalls)
            assertEquals(emptyList(), repository.requestedTxIds)
        }

    @Test
    fun createProposedTransactions_onlineSuccess_readsStoredTransactions() =
        runBlocking {
            val txId = FirstClassByteArray(byteArrayOf(3))
            val storedTransaction = encodedTransaction(txId = txId, rawValue = 4)
            val backend = RecordingTypesafeBackend(onlineTxIds = listOf(txId))
            val repository = RecordingDerivedDataRepository(storedTransactions = mapOf(txId to storedTransaction))
            val encoder = transactionEncoder(backend, repository)

            val result = encoder.createProposedTransactions(fakeProposal(), fakeUsk())

            assertEquals(listOf(storedTransaction), result)
            assertEquals(1, backend.onlineCalls)
            assertEquals(0, backend.detachedCalls)
            assertEquals(listOf(txId), repository.requestedTxIds)
        }

    @Test
    fun createProposedTransactionsDetached_backendFailure_throwsTransactionNotCreated() =
        runBlocking {
            val failure = IllegalStateException("detached failed")
            val backend = RecordingTypesafeBackend(detachedFailure = failure)
            val repository = RecordingDerivedDataRepository()
            val encoder = transactionEncoder(backend, repository)

            val exception =
                assertFailsWith<TransactionEncoderException.TransactionNotCreatedException> {
                    encoder.createProposedTransactionsDetached(fakeProposal(), fakeUsk())
                }

            assertSame(failure, exception.rootCause)
            assertEquals(1, backend.detachedCalls)
            assertEquals(emptyList(), repository.requestedTxIds)
        }

    private fun transactionEncoder(
        backend: RecordingTypesafeBackend,
        repository: RecordingDerivedDataRepository
    ) =
        TransactionEncoderImpl(
            backend = backend,
            saplingParamFetcher = testSaplingParamFetcher(backend),
            repository = repository
        )

    private fun testSaplingParamFetcher(backend: TypesafeBackend): SaplingParamFetcher {
        val paramsDir = Files.createTempDirectory("zcash-params").toFile()
        val legacyDir = File(paramsDir, "legacy")
        val params =
            listOf(
                SaplingParameters(
                    destinationDirectory = paramsDir,
                    fileName = SaplingParamTool.SPEND_PARAM_FILE_NAME,
                    fileMaxSizeBytes = 0,
                    fileHash = ""
                ),
                SaplingParameters(
                    destinationDirectory = paramsDir,
                    fileName = SaplingParamTool.OUTPUT_PARAM_FILE_NAME,
                    fileMaxSizeBytes = 0,
                    fileHash = ""
                )
            )
        params.forEach { File(paramsDir, it.fileName).writeBytes(byteArrayOf()) }
        return SaplingParamFetcher(
            SaplingParamTool(
                SaplingParamToolProperties(
                    saplingParams = params,
                    paramsDirectory = paramsDir,
                    paramsLegacyDirectory = legacyDir
                )
            ),
            backend
        )
    }

    private class RecordingTypesafeBackend(
        private val onlineTxIds: List<FirstClassByteArray> = emptyList(),
        private val detachedTransactions: List<EncodedTransaction> = emptyList(),
        private val detachedFailure: Throwable? = null
    ) : TypesafeBackend by unusedProxy() {
        var onlineCalls = 0
            private set
        var detachedCalls = 0
            private set

        override suspend fun createProposedTransactions(
            proposal: Proposal,
            usk: UnifiedSpendingKey
        ): List<FirstClassByteArray> {
            onlineCalls++
            return onlineTxIds
        }

        override suspend fun createProposedTransactionsDetached(
            proposal: Proposal,
            usk: UnifiedSpendingKey
        ): List<EncodedTransaction> {
            detachedCalls++
            detachedFailure?.let { throw it }
            return detachedTransactions
        }
    }

    private class RecordingDerivedDataRepository(
        private val storedTransactions: Map<FirstClassByteArray, EncodedTransaction> = emptyMap()
    ) : DerivedDataRepository by unusedProxy() {
        val requestedTxIds = mutableListOf<FirstClassByteArray>()

        override suspend fun findEncodedTransactionByTxId(txId: FirstClassByteArray): EncodedTransaction? {
            requestedTxIds += txId
            return storedTransactions[txId]
        }
    }

    private companion object {
        private fun encodedTransaction(
            txIdValue: Byte,
            rawValue: Byte
        ) = encodedTransaction(txId = FirstClassByteArray(byteArrayOf(txIdValue)), rawValue = rawValue)

        private fun encodedTransaction(
            txId: FirstClassByteArray,
            rawValue: Byte
        ) =
            EncodedTransaction(
                txId = txId,
                raw = FirstClassByteArray(byteArrayOf(rawValue)),
                expiryHeight = BlockHeight.new(2_000)
            )

        private fun fakeProposal() = Proposal.fromByteArray(byteArrayOf(0x10, 0x03))

        private fun fakeUsk() = UnifiedSpendingKey(JniUnifiedSpendingKey(byteArrayOf(1)))

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> unusedProxy(): T =
            Proxy.newProxyInstance(
                T::class.java.classLoader,
                arrayOf(T::class.java)
            ) { _, method, _ ->
                error("Unexpected ${T::class.simpleName}.${method.name} call")
            } as T
    }
}

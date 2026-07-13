package cash.z.ecc.android.sdk.block.processor

import cash.z.ecc.android.sdk.fixture.UnusedProxyFixture
import cash.z.ecc.android.sdk.internal.SaplingParamFetcher
import cash.z.ecc.android.sdk.internal.SaplingParamTool
import cash.z.ecc.android.sdk.internal.SaplingParamToolProperties
import cash.z.ecc.android.sdk.internal.TypesafeBackend
import cash.z.ecc.android.sdk.internal.block.CompactBlockDownloader
import cash.z.ecc.android.sdk.internal.model.DbTransactionOverview
import cash.z.ecc.android.sdk.internal.model.ScanProgress
import cash.z.ecc.android.sdk.internal.model.ScanRange
import cash.z.ecc.android.sdk.internal.model.WalletSummary
import cash.z.ecc.android.sdk.internal.repository.CompactBlockRepository
import cash.z.ecc.android.sdk.internal.repository.DerivedDataRepository
import cash.z.ecc.android.sdk.internal.transaction.NoOpOfflineTransactionTracker
import cash.z.ecc.android.sdk.internal.transaction.SubmitOnlyTransactionManager
import cash.z.ecc.android.sdk.model.AccountBalance
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.SdkFlags
import cash.z.ecc.android.sdk.model.WalletBalance
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.lightwallet.client.CombinedWalletClient
import co.electriccoin.lightwallet.client.ServiceMode
import co.electriccoin.lightwallet.client.model.BlockHeightUnsafe
import co.electriccoin.lightwallet.client.model.Response
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompactBlockProcessorTest {
    @Test
    fun should_refresh_preparation_test() {
        assertTrue {
            CompactBlockProcessor.shouldRefreshPreparation(
                lastPreparationTime = CompactBlockProcessor.SYNCHRONIZATION_RESTART_TIMEOUT,
                currentTimeMillis = CompactBlockProcessor.SYNCHRONIZATION_RESTART_TIMEOUT * 2,
                limitTime = CompactBlockProcessor.SYNCHRONIZATION_RESTART_TIMEOUT
            )
        }
    }

    @Test
    fun should_not_refresh_preparation_test() {
        assertFalse {
            CompactBlockProcessor.shouldRefreshPreparation(
                lastPreparationTime = CompactBlockProcessor.SYNCHRONIZATION_RESTART_TIMEOUT,
                currentTimeMillis = CompactBlockProcessor.SYNCHRONIZATION_RESTART_TIMEOUT,
                limitTime = CompactBlockProcessor.SYNCHRONIZATION_RESTART_TIMEOUT
            )
        }
    }

    /**
     * Reproduces a stuck-pending-change bug: [CompactBlockProcessor.processNewBlocksInSbSOrder] advances the
     * local chain tip past a transaction's expiry height (via `updateChainTip` inside
     * `runSbSSyncingPreparation`), which releases that transaction's pending change on the Rust side. But when
     * preparation concludes with
     * [cash.z.ecc.android.sdk.block.processor.model.SbSPreparationResult.NoMoreBlocksToProcess], the published
     * [CompactBlockProcessor.walletBalances] must still be refreshed against the freshly-advanced tip, otherwise
     * it keeps reporting the stale pre-expiry `changePending` value instead of the fresh, released one.
     */
    @Test
    fun processNewBlocksInSbSOrder_chainTipAdvancesPastExpiry_releasesStaleChangePending() =
        runBlocking {
            val accountUuid = AccountUuid.new(ByteArray(16) { it.toByte() })
            val expiryHeight = BlockHeight.new(600_000)
            val networkHeight = expiryHeight + 1
            val staleChangePending = Zatoshi(50_000)

            val backend =
                FakeTypesafeBackend(
                    accountUuid = accountUuid,
                    expiryHeight = expiryHeight,
                    staleChangePending = staleChangePending,
                    networkHeight = networkHeight
                )
            val repository = FakeDerivedDataRepository()
            val downloader =
                CompactBlockDownloader(
                    walletClient = FakeCombinedWalletClient(networkHeight),
                    compactBlockRepository = UnusedProxyFixture.new<CompactBlockRepository>()
                )
            val processor =
                CompactBlockProcessor(
                    backend = backend,
                    downloader = downloader,
                    minimumHeight = networkHeight,
                    repository = repository,
                    sdkFlags = SdkFlags(isTorEnabled = false, isExchangeRateEnabled = false),
                    saplingParamFetcher = fakeSaplingParamFetcher(backend),
                    unminedTransactionResubmitter =
                        UnminedTransactionResubmitter(
                            repository,
                            SubmitOnlyTransactionManager(),
                            NoOpOfflineTransactionTracker
                        )
                )

            // Simulate a previous sync cycle that published the balance while the change was still pending.
            processor.refreshWalletSummary()

            // Drives the same preparation step that runs inside the block-scanning loop: it advances the local
            // chain tip past the transaction's expiry height before any scanning happens, then finds no ranges
            // left to scan.
            processor.processNewBlocksInSbSOrder(
                backend = backend,
                downloader = downloader,
                repository = repository,
                lastValidHeight = expiryHeight,
                firstUnenhancedHeight = null
            )

            val publishedChangePending = processor.walletBalances.value?.get(accountUuid)?.sapling?.changePending

            // Desired behavior: the chain tip already advanced past expiry, releasing the change, so the
            // published balance should reflect 0 pending change. Today it still reports the stale value because
            // refreshWalletSummary() is never called on this code path.
            assertEquals(Zatoshi(0), publishedChangePending)
        }

    private fun fakeSaplingParamFetcher(backend: TypesafeBackend) =
        SaplingParamFetcher(
            SaplingParamTool(
                SaplingParamToolProperties(
                    saplingParams = emptyList(),
                    paramsDirectory = File("unused"),
                    paramsLegacyDirectory = File("unused")
                )
            ),
            backend
        )

    /**
     * Models the Rust backend's chain-tip-vs-expiry behavior: [getWalletSummary] reports the stale
     * [staleChangePending] until [updateChainTip] is called with a height past [expiryHeight], after which the
     * expired change is considered released and reported as zero.
     */
    private class FakeTypesafeBackend(
        private val accountUuid: AccountUuid,
        private val expiryHeight: BlockHeight,
        private val staleChangePending: Zatoshi,
        private val networkHeight: BlockHeight
    ) : TypesafeBackend by UnusedProxyFixture.new() {
        override val network = ZcashNetwork.Mainnet

        private var chainTipAdvancedPastExpiry = false

        override suspend fun updateChainTip(height: BlockHeight) {
            if (height > expiryHeight) {
                chainTipAdvancedPastExpiry = true
            }
        }

        override suspend fun suggestScanRanges(): List<ScanRange> = emptyList()

        override suspend fun getWalletSummary(): WalletSummary {
            val changePending = if (chainTipAdvancedPastExpiry) Zatoshi(0) else staleChangePending
            return WalletSummary(
                accountBalances =
                    mapOf(
                        accountUuid to
                            AccountBalance(
                                sapling = WalletBalance(Zatoshi(0), changePending, Zatoshi(0)),
                                orchard = WalletBalance(Zatoshi(0), Zatoshi(0), Zatoshi(0)),
                                unshielded = Zatoshi(0)
                            )
                    ),
                chainTipHeight = networkHeight,
                fullyScannedHeight = expiryHeight,
                scanProgress = ScanProgress(1, 1),
                recoveryProgress = null,
                nextSaplingSubtreeIndex = 0u,
                nextOrchardSubtreeIndex = 0u
            )
        }
    }

    private class FakeDerivedDataRepository : DerivedDataRepository by UnusedProxyFixture.new() {
        override suspend fun firstUnenhancedHeight(): BlockHeight? = null

        override suspend fun findUnminedTransactionsWithinExpiry(
            blockHeight: BlockHeight
        ): List<DbTransactionOverview> = emptyList()
    }

    private class FakeCombinedWalletClient(
        private val networkHeight: BlockHeight
    ) : CombinedWalletClient by UnusedProxyFixture.new() {
        override suspend fun getLatestBlockHeight(serviceMode: ServiceMode): Response<BlockHeightUnsafe> =
            Response.Success(BlockHeightUnsafe(networkHeight.value))
    }
}

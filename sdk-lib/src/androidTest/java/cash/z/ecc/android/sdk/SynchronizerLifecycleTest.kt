package cash.z.ecc.android.sdk

import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.sdk.block.processor.CompactBlockProcessor
import cash.z.ecc.android.sdk.fixture.AccountCreateSetupFixture
import cash.z.ecc.android.sdk.fixture.WalletFixture
import cash.z.ecc.android.sdk.model.ZcashNetwork
import co.electriccoin.lightwallet.client.model.LightWalletEndpoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SynchronizerLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    @LargeTest
    fun new_autoStartFalse_restoreWallet_makesNoNetworkCallAndKeepsLocalDataReadable() =
        runBlocking {
            val alias = newAlias()
            withUnusedServer { endpoint, connections ->
                val persistedAccounts =
                    newSynchronizer(endpoint, alias, autoStart = false).let { synchronizer ->
                        synchronizer as SdkSynchronizer
                        assertEquals(Synchronizer.LifecycleState.Paused, synchronizer.lifecycleState.value)
                        assertEquals(Synchronizer.Status.STOPPED, synchronizer.status.first())

                        val accounts = synchronizer.getAccounts()
                        assertTrue(accounts.isNotEmpty())
                        // A never-scanned wallet legitimately holds no transactions and no balance
                        // summary; what must work offline is reading the local stores at all.
                        synchronizer.allTransactions.first()
                        synchronizer.walletBalances.first()

                        synchronizer.closeFlow().first()
                        accounts
                    }

                // Reopening the same alias must return exactly what the first instance persisted,
                // and still without touching the network.
                newSynchronizer(endpoint, alias, autoStart = false).use { reopened ->
                    assertEquals(persistedAccounts, reopened.getAccounts())
                }

                assertEquals(0, connections.get())
            }
        }

    @Test
    @LargeTest
    fun pauseSync_resumeSync_repeatedly_keepsSynchronizingWithDirectClient() =
        assertPauseResumeCycles(isTorEnabled = false)

    @Test
    @LargeTest
    fun pauseSync_resumeSync_repeatedly_keepsSynchronizingWithTorClient() =
        assertPauseResumeCycles(isTorEnabled = true)

    @Test
    @LargeTest
    fun processorStopped_whileRunning_marksTerminallyStoppedAndRefusesResume() =
        runBlocking {
            withUnusedServer { endpoint, _ ->
                newSynchronizer(endpoint, newAlias(), autoStart = true).use { synchronizer ->
                    synchronizer as SdkSynchronizer
                    assertEquals(Synchronizer.LifecycleState.Running, synchronizer.lifecycleState.value)

                    // This is the observable part of the processor's fail() path, which stops and rethrows.
                    synchronizer.processor.stop()

                    withTimeout(TIMEOUT) {
                        synchronizer.lifecycleState.first { it == Synchronizer.LifecycleState.TerminallyStopped }
                    }
                    assertFalse(synchronizer.resumeSync())
                    assertEquals(
                        Synchronizer.LifecycleState.TerminallyStopped,
                        synchronizer.lifecycleState.value
                    )

                    // A refused resume must not have relaunched synchronization behind the state.
                    delay(RELAUNCH_OBSERVATION)
                    assertEquals(
                        CompactBlockProcessor.State.Stopped,
                        synchronizer.processor.state.value
                    )
                    assertEquals(
                        Synchronizer.LifecycleState.TerminallyStopped,
                        synchronizer.lifecycleState.value
                    )
                }
            }
        }

    private fun assertPauseResumeCycles(isTorEnabled: Boolean) =
        runBlocking {
            newSynchronizer(
                endpoint = MAINNET_ENDPOINT,
                alias = newAlias(),
                autoStart = true,
                isTorEnabled = isTorEnabled
            ).use { synchronizer ->
                synchronizer as SdkSynchronizer

                repeat(PAUSE_RESUME_CYCLES) {
                    awaitSyncing(synchronizer)

                    synchronizer.pauseSync()
                    assertEquals(Synchronizer.LifecycleState.Paused, synchronizer.lifecycleState.value)
                    assertEquals(Synchronizer.Status.STOPPED, synchronizer.status.value)

                    // Resuming succeeds only while the downloader and the wallet client are still alive.
                    assertTrue(synchronizer.resumeSync())
                    assertEquals(Synchronizer.LifecycleState.Running, synchronizer.lifecycleState.value)
                }

                awaitSyncing(synchronizer)
            }
        }

    private suspend fun awaitSyncing(synchronizer: Synchronizer) {
        withTimeout(TIMEOUT) {
            synchronizer.status.first {
                it == Synchronizer.Status.SYNCING || it == Synchronizer.Status.SYNCED
            }
        }
    }

    /** Dashes are rejected by PreferenceKey, which the alias flows into. */
    private fun newAlias() = "lifecycle" + UUID.randomUUID().toString().replace("-", "")

    private suspend fun newSynchronizer(
        endpoint: LightWalletEndpoint,
        alias: String,
        autoStart: Boolean,
        isTorEnabled: Boolean = false
    ): CloseableSynchronizer =
        Synchronizer.new(
            alias = alias,
            birthday = null,
            context = context,
            lightWalletEndpoint = endpoint,
            setup =
                AccountCreateSetupFixture.new(
                    seed = Mnemonics.MnemonicCode(WalletFixture.BEN_SEED_PHRASE).toEntropy()
                ),
            walletInitMode = WalletInitMode.RestoreWallet,
            zcashNetwork = ZcashNetwork.Mainnet,
            isTorEnabled = isTorEnabled,
            isExchangeRateEnabled = false,
            autoStart = autoStart
        )

    /**
     * Runs [block] against an endpoint pointing at a local server that counts accepted connections and
     * answers nothing, so any network call the SDK makes is both visible and harmless.
     */
    private inline fun withUnusedServer(block: (LightWalletEndpoint, AtomicInteger) -> Unit) {
        val connections = AtomicInteger(0)
        ServerSocket(0).use { serverSocket ->
            thread(isDaemon = true) {
                while (true) {
                    try {
                        serverSocket.accept()
                        connections.incrementAndGet()
                    } catch (_: SocketException) {
                        return@thread
                    }
                }
            }
            block(
                LightWalletEndpoint("127.0.0.1", serverSocket.localPort, isSecure = false),
                connections
            )
        }
    }

    private companion object {
        private const val PAUSE_RESUME_CYCLES = 3
        private val TIMEOUT = 5.minutes
        private val RELAUNCH_OBSERVATION = 5.seconds

        /** LightWalletEndpointFixture still points at mainnet.lightwalletd.com, which no longer resolves. */
        private val MAINNET_ENDPOINT = LightWalletEndpoint("zec.rocks", 443, isSecure = true)
    }
}

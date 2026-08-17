package cash.z.ecc.android.sdk.internal

import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.model.Zatoshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

internal class SaplingParamFetcher(
    private val saplingParamTool: SaplingParamTool,
    private val backend: TypesafeBackend
) {
    private val mutex = Mutex()

    @Suppress("TooGenericExceptionCaught")
    suspend fun downloadIfNeeded() =
        mutex.withLock {
            try {
                val accounts = backend.getAccounts()
                val balances = backend.getWalletSummary()?.accountBalances.orEmpty()
                val needsSaplingParams =
                    accounts
                        .asSequence()
                        .map { it.accountUuid }
                        .any { accountUuid ->
                            val totalSaplingBalance = balances[accountUuid]?.sapling?.total ?: Zatoshi(0)
                            if (totalSaplingBalance > Zatoshi(0)) return@any true
                            val totalTransparentBalance = balances[accountUuid]?.unshielded ?: Zatoshi(0)
                            totalTransparentBalance > Zatoshi(0)
                        }

                if (needsSaplingParams) {
                    retryAndContinue { ensureParams() }
                }
            } catch (e: Exception) {
                Twig.error(e) { "Caught exception while fetching sapling params." }
            }
        }

    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    suspend fun forceDownload() =
        mutex.withLock {
            try {
                ensureParams()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Twig.error(e) { "Caught exception while fetching sapling params." }
            }
        }

    /**
     * Same as [forceDownload], but reports missing proving parameters to the caller instead of
     * swallowing them, so the UI can explain why signing is impossible.
     */
    @Suppress("TooGenericExceptionCaught", "RethrowCaughtException")
    suspend fun requireParams() =
        mutex.withLock {
            try {
                ensureParams()
            } catch (e: CancellationException) {
                throw e
            } catch (e: TransactionEncoderException) {
                Twig.error(e) { "Sapling params are unavailable." }
                throw TransactionEncoderException.MissingParamsException
            } catch (e: Exception) {
                Twig.error(e) { "Caught exception while fetching sapling params." }
            }
        }

    private suspend fun ensureParams() = saplingParamTool.ensureParams(saplingParamTool.properties.paramsDirectory)

    private suspend inline fun retryAndContinue(block: () -> Unit) {
        var failedAttempts = 0
        while (failedAttempts < 2) {
            @Suppress("TooGenericExceptionCaught")
            try {
                block()
                return
            } catch (_: Throwable) {
                failedAttempts++
                if (failedAttempts == 2) return
                delay(10.seconds)
            }
        }
    }
}

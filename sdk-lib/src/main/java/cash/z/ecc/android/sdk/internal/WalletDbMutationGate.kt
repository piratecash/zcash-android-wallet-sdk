package cash.z.ecc.android.sdk.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

internal class WalletDbMutationGate {
    private val mutex = Mutex()

    suspend fun <T> withWriteAccess(
        operation: String,
        block: suspend () -> T
    ): T {
        val waitStarted = TimeSource.Monotonic.markNow()
        return mutex.withLock {
            val waitDuration = waitStarted.elapsedNow()
            if (waitDuration.inWholeMilliseconds > 0) {
                Twig.debug { "Wallet DB mutation gate waited $waitDuration for $operation" }
            }
            block()
        }
    }
}

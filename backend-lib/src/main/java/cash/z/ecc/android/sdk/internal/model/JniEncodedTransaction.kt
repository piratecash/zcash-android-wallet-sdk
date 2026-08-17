package cash.z.ecc.android.sdk.internal.model

import androidx.annotation.Keep
import cash.z.ecc.android.sdk.internal.ext.isInUIntRange

@Keep
class JniEncodedTransaction(
    val txId: ByteArray,
    val raw: ByteArray,
    val expiryHeight: Long
) {
    init {
        require(txId.isNotEmpty()) {
            "Transaction ID must not be empty"
        }
        require(raw.isNotEmpty()) {
            "Raw transaction must not be empty"
        }
        require(expiryHeight == UNKNOWN_EXPIRY_HEIGHT || expiryHeight.isInUIntRange()) {
            "Expiry height $expiryHeight is outside of allowed UInt range"
        }
    }

    companion object {
        const val UNKNOWN_EXPIRY_HEIGHT = -1L
    }
}

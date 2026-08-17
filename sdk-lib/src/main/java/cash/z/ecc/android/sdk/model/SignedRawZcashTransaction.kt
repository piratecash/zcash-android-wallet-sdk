package cash.z.ecc.android.sdk.model

import cash.z.ecc.android.sdk.internal.ext.toHexReversed

/**
 * A locally created, signed Zcash transaction that has not necessarily been submitted.
 */
data class SignedRawZcashTransaction(
    val raw: FirstClassByteArray,
    val txId: FirstClassByteArray,
    val expiryHeight: BlockHeight?
) {
    init {
        require(raw.byteArray.isNotEmpty()) { "Signed raw transaction must not be empty" }
        require(txId.byteArray.isNotEmpty()) { "Transaction ID must not be empty" }
    }

    fun txIdString() = txId.byteArray.toHexReversed()

    override fun toString() = "SignedRawZcashTransaction"
}

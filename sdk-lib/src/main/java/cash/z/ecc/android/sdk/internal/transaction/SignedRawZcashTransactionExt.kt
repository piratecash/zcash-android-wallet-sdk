package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.model.EncodedTransaction
import cash.z.ecc.android.sdk.model.SignedRawZcashTransaction

internal fun EncodedTransaction.toSignedRawZcashTransaction() =
    SignedRawZcashTransaction(
        raw = raw,
        txId = txId,
        expiryHeight = expiryHeight
    )

internal fun SignedRawZcashTransaction.toEncodedTransaction() =
    EncodedTransaction(
        txId = txId,
        raw = raw,
        expiryHeight = expiryHeight
    )

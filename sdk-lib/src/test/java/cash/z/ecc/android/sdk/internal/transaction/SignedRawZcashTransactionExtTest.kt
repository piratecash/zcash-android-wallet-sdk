package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.SignedRawZcashTransaction
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SignedRawZcashTransactionExtTest {
    @Test
    fun toEncodedTransaction_submitRawTransactionInput_preservesFields() =
        runBlocking {
            val raw = FirstClassByteArray(byteArrayOf(0x10, 0x11))
            val txId = FirstClassByteArray(byteArrayOf(0x01, 0x02))
            val expiryHeight = BlockHeight.new(1_000)
            val signed = SignedRawZcashTransaction(raw = raw, txId = txId, expiryHeight = expiryHeight)
            val txManager = SubmitOnlyTransactionManager(TransactionSubmitResult.Success(txId))

            txManager.submit(signed.toEncodedTransaction())

            val submitted = txManager.submittedTransactions.single()
            assertEquals(raw, submitted.raw)
            assertEquals(txId, submitted.txId)
            assertEquals(expiryHeight, submitted.expiryHeight)
        }
}

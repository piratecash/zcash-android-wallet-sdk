package cash.z.ecc.android.sdk.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SignedRawZcashTransactionTest {
    @Test
    fun new_emptyRaw_throws() {
        assertFailsWith<IllegalArgumentException> {
            SignedRawZcashTransaction(
                raw = FirstClassByteArray(byteArrayOf()),
                txId = txId(),
                expiryHeight = null
            )
        }
    }

    @Test
    fun new_emptyTxId_throws() {
        assertFailsWith<IllegalArgumentException> {
            SignedRawZcashTransaction(
                raw = raw(),
                txId = FirstClassByteArray(byteArrayOf()),
                expiryHeight = null
            )
        }
    }

    @Test
    fun equals_sameBytes_returnsTrue() {
        val first = SignedRawZcashTransaction(raw(), txId(), BlockHeight.new(1_000))
        val second = SignedRawZcashTransaction(raw(), txId(), BlockHeight.new(1_000))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun txIdString_validTxId_returnsReversedHex() {
        val transaction = SignedRawZcashTransaction(raw(), FirstClassByteArray(byteArrayOf(0x01, 0x02)), null)

        assertEquals("0201", transaction.txIdString())
    }

    private fun raw() = FirstClassByteArray(byteArrayOf(0x10, 0x11))

    private fun txId() = FirstClassByteArray(byteArrayOf(0x01, 0x02))
}

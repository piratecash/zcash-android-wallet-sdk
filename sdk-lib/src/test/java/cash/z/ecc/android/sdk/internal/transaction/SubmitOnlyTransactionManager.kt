package cash.z.ecc.android.sdk.internal.transaction

import cash.z.ecc.android.sdk.internal.model.EncodedTransaction
import cash.z.ecc.android.sdk.model.Account
import cash.z.ecc.android.sdk.model.AccountUuid
import cash.z.ecc.android.sdk.model.FirstClassByteArray
import cash.z.ecc.android.sdk.model.Pczt
import cash.z.ecc.android.sdk.model.Proposal
import cash.z.ecc.android.sdk.model.TransactionSubmitResult
import cash.z.ecc.android.sdk.model.UnifiedSpendingKey
import cash.z.ecc.android.sdk.model.Zatoshi

internal class SubmitOnlyTransactionManager(
    vararg responses: TransactionSubmitResult
) : OutboundTransactionManager {
    private val responses = ArrayDeque(responses.toList())
    val submittedTxIds = mutableListOf<FirstClassByteArray>()
    val submittedTransactions = mutableListOf<EncodedTransaction>()

    override suspend fun submit(encodedTransaction: EncodedTransaction): TransactionSubmitResult {
        submittedTransactions += encodedTransaction
        submittedTxIds += encodedTransaction.txId
        return responses.removeFirst()
    }

    override suspend fun createProposedTransactions(
        proposal: Proposal,
        usk: UnifiedSpendingKey
    ): List<EncodedTransaction> = unexpected()

    override suspend fun proposeTransferFromUri(account: Account, uri: String): Proposal = unexpected()

    override suspend fun proposeTransfer(
        account: Account,
        recipient: String,
        amount: Zatoshi,
        memo: String
    ): Proposal = unexpected()

    override suspend fun proposeShielding(
        account: Account,
        shieldingThreshold: Zatoshi,
        memo: String,
        transparentReceiver: String?
    ): Proposal? = unexpected()

    override suspend fun createPcztFromProposal(accountUuid: AccountUuid, proposal: Proposal): Pczt = unexpected()

    override suspend fun redactPcztForSigner(pczt: Pczt): Pczt = unexpected()

    override suspend fun pcztRequiresSaplingProofs(pczt: Pczt): Boolean = unexpected()

    override suspend fun addProofsToPczt(pczt: Pczt): Pczt = unexpected()

    override suspend fun extractAndStoreTxFromPczt(
        pcztWithProofs: Pczt,
        pcztWithSignatures: Pczt
    ): EncodedTransaction = unexpected()

    override suspend fun isValidShieldedAddress(address: String): Boolean = unexpected()

    override suspend fun isValidTransparentAddress(address: String): Boolean = unexpected()

    override suspend fun isValidUnifiedAddress(address: String): Boolean = unexpected()

    override suspend fun isValidTexAddress(address: String): Boolean = unexpected()

    private fun unexpected(): Nothing = error("Unexpected OutboundTransactionManager call")
}

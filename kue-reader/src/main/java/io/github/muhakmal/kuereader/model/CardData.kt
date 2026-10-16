package io.github.muhakmal.kuereader.model

data class CardData(
    val cardName: String,
    val balance: Double,
    val transactionHistory: List<Transaction> = emptyList(),
    val rawId: String = "",
    val debugLog: String = "",
    /**
     * Whether [balance] was genuinely read from the card.
     * Some cards return 0.0 upon read failure; this flag prevents reporting failure as a zero balance.
     */
    val balanceKnown: Boolean = true,
    /** ISO 4217 currency code (e.g., IDR or JPY). */
    val currency: String = CURRENCY_IDR
) {
    /** True if the balance was successfully decoded and the card is a recognized electronic money card. */
    val isBalanceReadable: Boolean
        get() = balanceKnown && isCardRecognized(cardName)

    companion object {
        const val CURRENCY_IDR = "IDR"
        const val CURRENCY_JPY = "JPY"

        fun isCardRecognized(cardName: String): Boolean =
            !cardName.contains("Legacy", ignoreCase = true) &&
            !cardName.contains("Unknown", ignoreCase = true) &&
            !cardName.contains("Error", ignoreCase = true) &&
            !cardName.contains("Failed", ignoreCase = true) &&
            !cardName.contains("Sony", ignoreCase = true) &&
            !cardName.contains("Detected", ignoreCase = true) &&
            !cardName.contains("Encrypted", ignoreCase = true) &&
            !cardName.contains("Protected", ignoreCase = true)
    }
}

data class Transaction(
    val date: String,
    val amount: Double,
    val type: String,
    val location: String? = null,
    val terminalId: String? = null
)

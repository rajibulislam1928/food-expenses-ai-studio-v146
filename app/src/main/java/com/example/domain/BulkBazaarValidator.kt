package com.example.domain

data class BulkBazaarRowDraft(
  val id: Int,
  val title: String = "",
  val amountText: String = "",
)

data class BulkBazaarItem(
  val title: String,
  val amount: Double,
)

data class BulkBazaarValidation(
  val validItems: List<BulkBazaarItem>,
  val incompleteRowIds: Set<Int>,
  val totalAmount: Double,
)

object BulkBazaarValidator {

  val DEFAULT_GROCERY_PRESETS =
    listOf(
      "Rice",
      "Cooking Oil",
      "Chicken",
      "Fish",
      "Eggs",
      "Potatoes",
      "Onions",
      "Tomatoes",
      "Lentils (Dal)",
      "Vegetables",
      "Spices",
      "Green Chili",
      "Milk & Tea",
    )

  fun validate(rows: List<BulkBazaarRowDraft>): BulkBazaarValidation {
    val validItems = mutableListOf<BulkBazaarItem>()
    val incompleteIds = mutableSetOf<Int>()
    var total = 0.0

    rows.forEach { row ->
      val trimmedTitle = row.title.trim()
      val trimmedAmount = row.amountText.trim()
      if (trimmedTitle.isEmpty() && trimmedAmount.isEmpty()) {
        return@forEach
      }
      val parsedAmount = trimmedAmount.toDoubleOrNull()
      if (trimmedTitle.isNotEmpty() && parsedAmount != null && parsedAmount > 0.0 && parsedAmount <= 10_000_000.0) {
        val cleanTitle = trimmedTitle.take(100)
        val roundedAmount = kotlin.math.round(parsedAmount * 100.0) / 100.0
        validItems.add(BulkBazaarItem(title = cleanTitle, amount = roundedAmount))
        total += roundedAmount
      } else {
        incompleteIds.add(row.id)
      }
    }

    return BulkBazaarValidation(
      validItems = validItems,
      incompleteRowIds = incompleteIds,
      totalAmount = kotlin.math.round(total * 100.0) / 100.0,
    )
  }
}

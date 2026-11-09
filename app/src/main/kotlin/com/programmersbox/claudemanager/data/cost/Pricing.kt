package com.programmersbox.claudemanager.data.cost

/** Token counts of one API response, as Claude Code records them in `message.usage`. */
data class Usage(
    val model: String?,
    val input: Long = 0,
    val cacheWrite5m: Long = 0,
    val cacheWrite1h: Long = 0,
    val cacheRead: Long = 0,
    val output: Long = 0,
) {
    val promptTokens: Long get() = input + cacheWrite5m + cacheWrite1h + cacheRead
}

/**
 * Claude API list prices in USD per million tokens, from Anthropic's published pricing as of
 * 2026-10-06. Cache writes cost 1.25x input (5-minute TTL) or 2x input (1-hour TTL); cache reads
 * cost [readMultiplier] x input. This is an estimate of API-equivalent cost: subscription plans
 * are not billed per token.
 */
data class ModelPrice(val input: Double, val output: Double, val readMultiplier: Double = 0.1)

object Pricing {
    const val AS_OF = "2026-10-06"

    // Longest prefix first so "claude-opus-5-5" wins over "claude-opus-5".
    private val prices: List<Pair<String, ModelPrice>> = listOf(
        "claude-fable-5-1" to ModelPrice(10.0, 50.0, 0.025),
        "claude-mythos-5-1" to ModelPrice(10.0, 50.0, 0.025),
        "claude-fable-5" to ModelPrice(10.0, 50.0),
        "claude-opus-5-5" to ModelPrice(4.0, 20.0, 0.05),
        "claude-opus-5" to ModelPrice(5.0, 25.0),
        "claude-opus-4-8" to ModelPrice(5.0, 25.0),
        "claude-opus-4-7" to ModelPrice(5.0, 25.0),
        "claude-opus-4-6" to ModelPrice(5.0, 25.0),
        "claude-sonnet-5-5" to ModelPrice(2.0, 10.0),
        "claude-sonnet-5" to ModelPrice(2.0, 10.0),
        "claude-sonnet-4-6" to ModelPrice(3.0, 15.0),
        "claude-haiku-4-5" to ModelPrice(1.0, 5.0),
    ).sortedByDescending { it.first.length }

    /** Claude Haiku 5.5 bills by prompt length: up to 100K tokens, or longer. */
    private val haiku55Short = ModelPrice(0.10, 0.50)
    private val haiku55Long = ModelPrice(0.50, 2.50)

    fun priceFor(model: String?, promptTokens: Long): ModelPrice? {
        val id = model?.lowercase()?.removePrefix("anthropic.") ?: return null
        if (id.startsWith("claude-haiku-5-5")) return if (promptTokens <= 100_000) haiku55Short else haiku55Long
        return prices.firstOrNull { id.startsWith(it.first) }?.second
    }

    /** Cost in USD, or null when the model has no known price. */
    fun cost(usage: Usage): Double? {
        val p = priceFor(usage.model, usage.promptTokens) ?: return null
        val perToken = 1.0 / 1_000_000
        return perToken * (
            usage.input * p.input +
                usage.cacheWrite5m * p.input * 1.25 +
                usage.cacheWrite1h * p.input * 2.0 +
                usage.cacheRead * p.input * p.readMultiplier +
                usage.output * p.output
            )
    }
}

/** Sum of known costs plus how many responses had a model without a price. */
data class CostEstimate(val usd: Double, val unpricedResponses: Int) {
    operator fun plus(other: CostEstimate) = CostEstimate(usd + other.usd, unpricedResponses + other.unpricedResponses)

    companion object {
        val Zero = CostEstimate(0.0, 0)

        fun of(usages: Collection<Usage>): CostEstimate = usages.fold(Zero) { acc, u ->
            val c = Pricing.cost(u)
            if (c == null) acc.copy(unpricedResponses = acc.unpricedResponses + 1) else acc.copy(usd = acc.usd + c)
        }
    }
}

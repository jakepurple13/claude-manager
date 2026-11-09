package com.programmersbox.claudemanager.data

import com.programmersbox.claudemanager.data.cost.CostEstimate
import com.programmersbox.claudemanager.data.cost.Pricing
import com.programmersbox.claudemanager.data.cost.Usage
import com.programmersbox.claudemanager.data.transcript.TranscriptAccumulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PricingTest {
    private fun close(expected: Double, actual: Double?) =
        assertEquals(expected, actual ?: error("no price"), 1e-9)

    @Test
    fun opus55UsesHalvedCacheReads() {
        // 1M of each token kind on Opus 5.5: 4 + 5 (5m write) + 8 (1h write) + 0.2 (read) + 20 (out)
        val u = Usage("claude-opus-5-5", 1_000_000, 1_000_000, 1_000_000, 1_000_000, 1_000_000)
        close(37.2, Pricing.cost(u))
    }

    @Test
    fun longestPrefixWinsAndOldIdsWork() {
        close(5.0, Pricing.cost(Usage("claude-opus-5", input = 1_000_000)))
        close(4.0, Pricing.cost(Usage("claude-opus-5-5", input = 1_000_000)))
        close(0.25, Pricing.cost(Usage("claude-fable-5-1", cacheRead = 1_000_000)))
        close(1.0, Pricing.cost(Usage("claude-fable-5", cacheRead = 1_000_000)))
    }

    @Test
    fun haiku55SwitchesRateCardAbove100k() {
        close(0.10 * 0.1, Pricing.cost(Usage("claude-haiku-5-5", input = 100_000)))
        close(0.50 * 0.100001, Pricing.cost(Usage("claude-haiku-5-5", input = 100_001)))
    }

    @Test
    fun unknownModelIsCountedNotPriced() {
        assertNull(Pricing.cost(Usage("some-other-model", input = 10)))
        val est = CostEstimate.of(listOf(Usage("some-other-model", input = 10), Usage("claude-sonnet-5-5", output = 1_000_000)))
        assertEquals(1, est.unpricedResponses)
        assertEquals(10.0, est.usd, 1e-9)
    }

    @Test
    fun accumulatorPricesEachMessageOnceWithCacheSplit() {
        val acc = TranscriptAccumulator()
        val record = """{"type":"assistant","message":{"id":"m1","model":"claude-sonnet-5-5","usage":{"input_tokens":1000000,"cache_creation_input_tokens":2000000,"cache_read_input_tokens":0,"output_tokens":100000,"cache_creation":{"ephemeral_5m_input_tokens":1000000,"ephemeral_1h_input_tokens":1000000}},"content":[]}}"""
        acc.accept(record)
        acc.accept(record) // same message split over two records
        // 2 input + 2.5 (5m) + 4 (1h) + 1 (0.1M out at $10)
        assertEquals(9.5, acc.cost.usd, 1e-9)
    }
}

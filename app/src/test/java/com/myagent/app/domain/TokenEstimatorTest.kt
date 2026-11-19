package com.myagent.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TokenEstimator 的单元测试。
 */
class TokenEstimatorTest {

    @Test
    fun estimate_countsCjkCharsAsOneTokenEach() {
        assertEquals(2, TokenEstimator.estimate("你好"))
    }

    @Test
    fun estimate_roundsUpLatinCharsPerFour() {
        assertEquals(2, TokenEstimator.estimate("hello"))
    }

    @Test
    fun estimate_combinesCjkAndLatin() {
        // "你好" = 2，其余 5 个字符 ceil(5 / 4) = 2，共 4。
        assertEquals(4, TokenEstimator.estimate("你好world"))
    }

    @Test
    fun estimate_returnsZeroForEmptyText() {
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test
    fun estimate_roundsUpShortLatinText() {
        assertEquals(1, TokenEstimator.estimate("abc"))
    }

    @Test
    fun estimate_countsFullWidthPunctuationAsCjk() {
        assertEquals(1, TokenEstimator.estimate("！"))
    }
}

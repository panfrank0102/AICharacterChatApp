package com.myagent.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SseParser 的单元测试。
 */
class SseParserTest {

    @Test
    fun extractData_returnsJsonContent() {
        assertEquals(
            "{\"choices\":[]}",
            SseParser.extractData("data: {\"choices\":[]}"),
        )
    }

    @Test
    fun extractData_returnsNullForEmptyData() {
        assertNull(SseParser.extractData("data:"))
    }

    @Test
    fun extractData_returnsNullForNonDataLine() {
        assertNull(SseParser.extractData("event: message"))
    }

    @Test
    fun extractData_returnsNullForBlankLine() {
        assertNull(SseParser.extractData(""))
    }

    @Test
    fun extractData_trimsContent() {
        assertEquals("abc", SseParser.extractData("data:  abc  "))
    }

    @Test
    fun isDone_returnsTrueForDoneMarker() {
        assertTrue(SseParser.isDone("data: [DONE]"))
    }

    @Test
    fun isDone_returnsTrueForDoneMarkerWithTrailingSpace() {
        assertTrue(SseParser.isDone("data: [DONE] "))
    }

    @Test
    fun isDone_returnsFalseForOtherData() {
        assertFalse(SseParser.isDone("data: something"))
    }
}

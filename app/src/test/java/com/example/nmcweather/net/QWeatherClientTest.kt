package com.example.nmcweather.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QWeatherClientTest {
    @Test
    fun normalizeHost_acceptsHttpsHostnameOnly() {
        assertEquals("abcd1234.qweatherapi.com", QWeatherClient.normalizeHost("abcd1234.qweatherapi.com"))
        assertEquals("abcd1234.qweatherapi.com", QWeatherClient.normalizeHost("https://ABCD1234.qweatherapi.com/"))
    }

    @Test
    fun normalizeHost_rejectsUnsafeComponents() {
        assertNull(QWeatherClient.normalizeHost("http://example.com"))
        assertNull(QWeatherClient.normalizeHost("https://example.com/path"))
        assertNull(QWeatherClient.normalizeHost("https://example.com?key=value"))
        assertNull(QWeatherClient.normalizeHost("https://user@example.com"))
        assertNull(QWeatherClient.normalizeHost("https://example.com:8443"))
    }
}

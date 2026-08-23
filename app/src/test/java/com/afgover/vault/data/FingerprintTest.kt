package com.afgover.vault.data

import com.afgover.vault.core.Fingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Parmak izi bilinen vektörlere uymalı; yoksa karşılaştırma anlamsızdır. */
class FingerprintTest {

    @Test
    fun `bos metnin sha256si bilinen degerdir`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Fingerprint.sha256Hex("")
        )
    }

    @Test
    fun `abc bilinen vektore uyar`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Fingerprint.sha256Hex("abc")
        )
    }

    @Test
    fun `son satir sonu parmak izini degistirir - komut uyarisinin sebebi`() {
        assertNotEquals(Fingerprint.sha256Hex("veri"), Fingerprint.sha256Hex("veri\n"))
    }

    @Test
    fun `gruplama ve kisaltma hex icerigini bozmaz`() {
        val hex = Fingerprint.sha256Hex("test")
        assertEquals(hex, Fingerprint.grouped(hex).replace(" ", ""))
        assertEquals(hex.take(8), Fingerprint.short(hex).take(9).replace(" ", ""))
    }
}

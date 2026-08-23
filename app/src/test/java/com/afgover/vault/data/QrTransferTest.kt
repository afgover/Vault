package com.afgover.vault.data

import com.afgover.vault.core.QrTransfer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kareler `vault-clip.py`'nin GERÇEK çıktısıdır (çapraz dil doğrulaması):
 * Python üretir, uygulama çözer. Biçim iki tarafta ayrışırsa test düşer.
 */
class QrTransferTest {

    private val veri = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("qr_kareleri.json")!!
            .readBytes().toString(Charsets.UTF_8)
    )

    private fun kareler(ad: String): List<String> {
        val arr = veri.getJSONObject(ad).getJSONArray("kareler")
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun zarf(ad: String) = veri.getJSONObject(ad).getString("zarf")

    private fun coz(metinler: List<String>): String? =
        QrTransfer.assemble(metinler.mapNotNull { QrTransfer.parse(it) })

    @Test
    fun `tek kareli zarf birebir cozulur`() {
        assertEquals(zarf("kucuk"), coz(kareler("kucuk")))
    }

    @Test
    fun `cok kareli zarf birebir cozulur`() {
        val k = kareler("buyuk")
        assertTrue("büyük zarf tek kareye sığmamalı", k.size > 1)
        assertEquals(zarf("buyuk"), coz(k))
    }

    @Test
    fun `kareler sirasiz ve tekrarli okunabilir`() {
        val k = kareler("buyuk")
        val karisik = (k.reversed() + k.first() + k).toList()
        assertEquals(zarf("buyuk"), coz(karisik))
    }

    @Test
    fun `eksik kare varken birlestirme yapilmaz`() {
        val k = kareler("buyuk")
        assertNull(coz(k.drop(1)))
        assertEquals(listOf(1), QrTransfer.missing(k.drop(1).mapNotNull(QrTransfer::parse)))
    }

    @Test
    fun `celiskili kare kumesi reddedilir`() {
        val a = QrTransfer.parse(kareler("kucuk")[0])!!
        val sahte = a.copy(index = 2, total = 2)
        assertNull(QrTransfer.assemble(listOf(a, sahte.copy(total = 3))))
    }

    @Test
    fun `bizim bicimimiz olmayan qr tanınmaz`() {
        for (yabanci in listOf(
            "https://vault.gover.us/b/abc", "{\"app\":\"vault\"}", "VLT1|0/2|Z|x",
            "VLT1|3/2|Z|x", "VLT1|1/1|X|x", "VLT1|1/1", ""
        )) {
            assertNull(yabanci, QrTransfer.parse(yabanci))
        }
    }

    @Test
    fun `bozuk base64 sessizce cop uretmez`() {
        val k = QrTransfer.parse(kareler("kucuk")[0])!!
        assertNull(QrTransfer.assemble(listOf(k.copy(data = "%%%bozuk%%%"))))
    }

    @Test
    fun `tarayicinin urettigi kareler de cozulur - ucuncu dil`() {
        // aktar.html'in kendi betiği koşturularak üretildi: Python, tarayıcı ve
        // uygulama aynı kare biçiminde buluşmalı.
        val v = JSONObject(
            javaClass.classLoader!!.getResourceAsStream("qr_tarayici.json")!!
                .readBytes().toString(Charsets.UTF_8)
        )
        val arr = v.getJSONArray("kareler")
        val metinler = (0 until arr.length()).map { arr.getString(it) }
        assertTrue("çok kareli olmalı", metinler.size > 1)
        assertEquals(v.getString("zarf"), coz(metinler))
    }
}

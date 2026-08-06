package com.afgover.vault.core

import java.security.SecureRandom
import kotlin.math.log2

/**
 * Kriptografik rastgelelikle (SecureRandom) şifre üretir.
 * Seçilen her karakter sınıfından en az bir karakter garanti edilir.
 */
object PasswordGenerator {

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"

    /** Karıştırılması kolay karakterler: l, I, 1, O, 0 vb. */
    private const val AMBIGUOUS = "lI1O0o"

    private val random = SecureRandom()

    data class Options(
        val length: Int = 20,
        val lower: Boolean = true,
        val upper: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
        val avoidAmbiguous: Boolean = true
    )

    private fun pools(options: Options): List<String> {
        val pools = buildList {
            if (options.lower) add(LOWER)
            if (options.upper) add(UPPER)
            if (options.digits) add(DIGITS)
            if (options.symbols) add(SYMBOLS)
        }.map { pool ->
            if (options.avoidAmbiguous) pool.filterNot { it in AMBIGUOUS } else pool
        }
        // Hiç sınıf seçilmediyse küçük harfe düş
        return pools.ifEmpty { listOf(LOWER) }
    }

    fun generate(options: Options): String {
        val pools = pools(options)
        val all = pools.joinToString("")
        val length = options.length.coerceIn(4, 128)

        val chars = CharArray(length)
        // Önce her sınıftan bir karakter garanti et
        pools.forEachIndexed { i, pool ->
            if (i < length) chars[i] = pool[random.nextInt(pool.length)]
        }
        for (i in pools.size.coerceAtMost(length) until length) {
            chars[i] = all[random.nextInt(all.length)]
        }
        // Fisher-Yates karıştırması (garanti karakterler başta kalmasın)
        for (i in length - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return String(chars)
    }

    /** Yaklaşık entropi (bit) — parola gücü göstergesi için. */
    fun entropyBits(options: Options): Int {
        val poolSize = pools(options).sumOf { it.length }
        return (options.length * log2(poolSize.toDouble())).toInt()
    }
}

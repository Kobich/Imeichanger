package com.engboost.imeichanger.domain

import kotlin.random.Random

enum class ImeiError {
    WRONG_LENGTH,
    NOT_DIGITS,
    INVALID_CHECKSUM,
}

object Imei {
    const val LENGTH = 15
    private const val TAC_LENGTH = 8

    fun validate(imei: String): ImeiError? = when {
        imei.length != LENGTH -> ImeiError.WRONG_LENGTH
        !imei.all { it.isDigit() } -> ImeiError.NOT_DIGITS
        luhnCheckDigit(imei.dropLast(1)) != imei.last().digitToInt() -> ImeiError.INVALID_CHECKSUM
        else -> null
    }

    /**
     * Генерирует валидный IMEI. Если передан [tac] (первые 8 цифр — модель устройства),
     * он сохраняется, случайными становятся только серийный номер и контрольная цифра.
     */
    fun generate(tac: String? = null, random: Random = Random.Default): String {
        val prefix = tac
            ?.takeIf { it.length == TAC_LENGTH && it.all(Char::isDigit) }
            ?: randomDigits(TAC_LENGTH, random)
        val body = prefix + randomDigits(LENGTH - 1 - TAC_LENGTH, random)
        return body + luhnCheckDigit(body)
    }

    fun tacOf(imei: String): String? = imei.take(TAC_LENGTH).takeIf { validate(imei) == null }

    private fun randomDigits(count: Int, random: Random): String =
        buildString(count) { repeat(count) { append(random.nextInt(10)) } }

    private fun luhnCheckDigit(body: String): Int {
        // Справа налево удваиваем каждую вторую цифру, начиная с крайней правой цифры тела.
        val sum = body.reversed().mapIndexed { index, char ->
            val digit = char.digitToInt()
            if (index % 2 == 0) (digit * 2).let { if (it > 9) it - 9 else it } else digit
        }.sum()
        return (10 - sum % 10) % 10
    }
}

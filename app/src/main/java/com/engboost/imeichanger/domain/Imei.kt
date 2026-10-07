package com.engboost.imeichanger.domain

import kotlin.random.Random

enum class ImeiError {
    WRONG_LENGTH,
    NOT_DIGITS,
    INVALID_CHECKSUM,
}

/** IMEI = TAC (8 digits) + serial number (6 digits) + Luhn check digit. */
object Imei {
    const val LENGTH = 15
    const val TAC_LENGTH = 8

    fun validate(imei: String): ImeiError? = when {
        imei.length != LENGTH -> ImeiError.WRONG_LENGTH
        !imei.all { it.isDigit() } -> ImeiError.NOT_DIGITS
        luhnCheckDigit(imei.dropLast(1)) != imei.last().digitToInt() -> ImeiError.INVALID_CHECKSUM
        else -> null
    }

    fun generate(tac: String, random: Random = Random.Default): String {
        require(tac.length == TAC_LENGTH && tac.all(Char::isDigit)) { "TAC must be $TAC_LENGTH digits: $tac" }
        val serial = buildString { repeat(LENGTH - TAC_LENGTH - 1) { append(random.nextInt(10)) } }
        val body = tac + serial
        return body + luhnCheckDigit(body)
    }

    private fun luhnCheckDigit(body: String): Int {
        val sum = body.reversed().mapIndexed { index, char ->
            val digit = char.digitToInt()
            if (index % 2 == 0) (digit * 2).let { if (it > 9) it - 9 else it } else digit
        }.sum()
        return (10 - sum % 10) % 10
    }
}

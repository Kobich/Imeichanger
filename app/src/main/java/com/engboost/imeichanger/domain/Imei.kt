package com.engboost.imeichanger.domain

enum class ImeiError {
    WRONG_LENGTH,
    NOT_DIGITS,
    INVALID_CHECKSUM,
}

object Imei {
    const val LENGTH = 15

    fun validate(imei: String): ImeiError? = when {
        imei.length != LENGTH -> ImeiError.WRONG_LENGTH
        !imei.all { it.isDigit() } -> ImeiError.NOT_DIGITS
        luhnCheckDigit(imei.dropLast(1)) != imei.last().digitToInt() -> ImeiError.INVALID_CHECKSUM
        else -> null
    }

    private fun luhnCheckDigit(body: String): Int {
        // Справа налево удваиваем каждую вторую цифру, начиная с крайней правой цифры тела.
        val sum = body.reversed().mapIndexed { index, char ->
            val digit = char.digitToInt()
            if (index % 2 == 0) (digit * 2).let { if (it > 9) it - 9 else it } else digit
        }.sum()
        return (10 - sum % 10) % 10
    }
}

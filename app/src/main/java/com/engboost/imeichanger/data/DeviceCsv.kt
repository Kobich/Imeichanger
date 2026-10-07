package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.DeviceModel
import com.engboost.imeichanger.domain.Imei

/** Parses TAC catalog rows `brand,tac,specs,model`, e.g. `10.OR,91161200,10.OR,10.OR D`. */
object DeviceCsv {
    private const val BRAND = 0
    private const val TAC = 1
    private const val SPECS = 2
    private const val MODEL = 3

    fun parseLine(line: String): DeviceModel? {
        if (line.isBlank()) return null
        val fields = splitFields(line).map { it.trim() }
        val brand = fields.getOrNull(BRAND).orEmpty()
        val tac = normalizeTac(fields.getOrNull(TAC).orEmpty()) ?: return null
        val model = fields.getOrNull(MODEL)?.takeIf { it.isNotEmpty() }
            ?: fields.getOrNull(SPECS)?.takeIf { it.isNotEmpty() }
            ?: brand
        if (model.isEmpty()) return null
        return DeviceModel(brand = brand, model = model, tac = tac)
    }

    // Excel strips leading zeros from TACs: 440207 -> 00440207.
    private fun normalizeTac(raw: String): String? =
        raw.takeIf { it.isNotEmpty() && it.length <= Imei.TAC_LENGTH && it.all(Char::isDigit) }
            ?.padStart(Imei.TAC_LENGTH, '0')

    private fun splitFields(line: String): List<String> {
        val separator = if (line.count { it == ';' } > line.count { it == ',' }) ';' else ','
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && inQuotes && line.getOrNull(i + 1) == '"' -> {
                    current.append('"')
                    i++
                }
                char == '"' -> inQuotes = !inQuotes
                char == separator && !inQuotes -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            i++
        }
        fields += current.toString()
        return fields
    }
}

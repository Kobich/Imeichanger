package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.DeviceImei
import com.engboost.imeichanger.domain.Imei

object DeviceCsv {

    /**
     * Разбирает строку `название,компания,imei` (разделитель `,` или `;`, поля можно брать в кавычки).
     * Заголовок, пустые и битые строки возвращают null.
     */
    fun parseLine(line: String): DeviceImei? {
        if (line.isBlank()) return null
        val separator = if (line.count { it == ';' } >= 2) ';' else ','
        val fields = splitFields(line, separator)
        if (fields.size < 3) return null
        val (name, company, imei) = fields.map { it.trim() }
        if (name.isEmpty() || Imei.validate(imei) != null) return null
        return DeviceImei(name = name, company = company, imei = imei)
    }

    private fun splitFields(line: String, separator: Char): List<String> {
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

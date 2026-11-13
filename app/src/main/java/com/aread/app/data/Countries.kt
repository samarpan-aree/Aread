package com.aread.app.data

data class Country(val iso: String, val name: String, val dial: String, val maxDigits: Int) {
    val flag: String
        get() = iso.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

object Countries {
    private val raw = listOf(
        Country("NP", "Nepal", "977", 10), Country("IN", "India", "91", 10),
        Country("US", "United States", "1", 10), Country("CA", "Canada", "1", 10),
        Country("GB", "United Kingdom", "44", 10), Country("AU", "Australia", "61", 9),
        Country("BD", "Bangladesh", "880", 10), Country("PK", "Pakistan", "92", 10),
        Country("LK", "Sri Lanka", "94", 9), Country("BT", "Bhutan", "975", 8),
        Country("CN", "China", "86", 11), Country("JP", "Japan", "81", 10),
        Country("KR", "South Korea", "82", 10), Country("SG", "Singapore", "65", 8),
        Country("MY", "Malaysia", "60", 10), Country("TH", "Thailand", "66", 9),
        Country("ID", "Indonesia", "62", 12), Country("PH", "Philippines", "63", 10),
        Country("VN", "Vietnam", "84", 10), Country("AE", "United Arab Emirates", "971", 9),
        Country("SA", "Saudi Arabia", "966", 9), Country("QA", "Qatar", "974", 8),
        Country("KW", "Kuwait", "965", 8), Country("BH", "Bahrain", "973", 8),
        Country("OM", "Oman", "968", 8), Country("IL", "Israel", "972", 9),
        Country("TR", "Turkey", "90", 10), Country("DE", "Germany", "49", 11),
        Country("FR", "France", "33", 9), Country("IT", "Italy", "39", 10),
        Country("ES", "Spain", "34", 9), Country("PT", "Portugal", "351", 9),
        Country("NL", "Netherlands", "31", 9), Country("BE", "Belgium", "32", 9),
        Country("CH", "Switzerland", "41", 9), Country("SE", "Sweden", "46", 9),
        Country("NO", "Norway", "47", 8), Country("DK", "Denmark", "45", 8),
        Country("FI", "Finland", "358", 10), Country("IE", "Ireland", "353", 9),
        Country("PL", "Poland", "48", 9), Country("RU", "Russia", "7", 10),
        Country("UA", "Ukraine", "380", 9), Country("BR", "Brazil", "55", 11),
        Country("MX", "Mexico", "52", 10), Country("AR", "Argentina", "54", 10),
        Country("CL", "Chile", "56", 9), Country("CO", "Colombia", "57", 10),
        Country("PE", "Peru", "51", 9), Country("ZA", "South Africa", "27", 9),
        Country("NG", "Nigeria", "234", 10), Country("KE", "Kenya", "254", 9),
        Country("EG", "Egypt", "20", 10), Country("GH", "Ghana", "233", 9),
        Country("ET", "Ethiopia", "251", 9), Country("NZ", "New Zealand", "64", 9),
        Country("HK", "Hong Kong", "852", 8), Country("TW", "Taiwan", "886", 9),
        Country("AF", "Afghanistan", "93", 9), Country("MM", "Myanmar", "95", 9),
        Country("MV", "Maldives", "960", 7), Country("IR", "Iran", "98", 10),
        Country("IQ", "Iraq", "964", 10), Country("JO", "Jordan", "962", 9),
        Country("LB", "Lebanon", "961", 8), Country("MA", "Morocco", "212", 9),
        Country("TZ", "Tanzania", "255", 9), Country("UG", "Uganda", "256", 9),
    )

    val all: List<Country> = listOf(raw.first { it.iso == "NP" }) +
        raw.filter { it.iso != "NP" }.sortedBy { it.name }

    val default: Country get() = all.first()

    fun format(country: Country, digits: String) = "+${country.dial} $digits"

    fun e164(country: Country, digits: String) = "+${country.dial}$digits"

    fun parse(stored: String): Pair<Country, String> {
        val clean = stored.trim()
        if (!clean.startsWith("+")) return default to clean.filter { it.isDigit() }
        val digits = clean.drop(1).filter { it.isDigit() }
        val match = all.filter { digits.startsWith(it.dial) }.maxByOrNull { it.dial.length }
        return if (match != null) match to digits.removePrefix(match.dial).take(match.maxDigits)
        else default to digits
    }
}

package whitehall.yesterday.search

/**
 * Expands user search queries with UK government department acronyms and policy synonyms.
 *
 * Applied to lexical (FTS) queries only — the embedding model handles acronyms
 * well enough in vector space that expanding the semantic query would dilute the signal.
 *
 * expand() returns a list of query strings to OR together in FTS.
 * If no expansion applies, returns a singleton list containing the original query.
 */
object QueryExpander {

    private val acronyms: Map<String, String> = mapOf(
        "dwp"    to "Department for Work and Pensions",
        "dhsc"   to "Department of Health and Social Care",
        "hmrc"   to "HM Revenue and Customs",
        "hmt"    to "HM Treasury",
        "ons"    to "Office for National Statistics",
        "mod"    to "Ministry of Defence",
        "moj"    to "Ministry of Justice",
        "fcdo"   to "Foreign Commonwealth and Development Office",
        "dfe"    to "Department for Education",
        "desnz"  to "Department for Energy Security and Net Zero",
        "dbt"    to "Department for Business and Trade",
        "mhclg"  to "Ministry of Housing Communities and Local Government",
        "nhs"    to "National Health Service",
        "ofsted" to "Office for Standards in Education",
        "ofgem"  to "Office of Gas and Electricity Markets",
        "cma"    to "Competition and Markets Authority",
        "fca"    to "Financial Conduct Authority",
        "pra"    to "Prudential Regulation Authority",
        "hse"    to "Health and Safety Executive",
        "ea"     to "Environment Agency",
        "dft"    to "Department for Transport",
    )

    private val synonyms: Map<String, String> = mapOf(
        "welfare"   to "benefits",
        "benefits"  to "welfare",
        "net zero"  to "decarbonisation carbon emissions climate",
        "migration" to "asylum immigration",
        "asylum"    to "migration immigration",
    )

    /** Returns a list of FTS query strings to union. Usually 1 element; 2 when an expansion exists. */
    fun expand(query: String): List<String> {
        val lower = query.lowercase().trim()
        val result = mutableListOf(query)

        // Whole-query acronym (e.g. "DWP" → full name)
        acronyms[lower]?.let { result.add(it) }

        // Per-token acronym expansion (e.g. "DWP funding" → also search "Department for Work and Pensions funding")
        if (result.size == 1) {
            val tokens = lower.split(Regex("\\s+"))
            tokens.forEach { tok -> acronyms[tok]?.let { result.add(it) } }
        }

        // Whole-query synonym (e.g. "welfare" → also search "benefits")
        synonyms[lower]?.let { result.add(it) }

        return result.distinct()
    }
}

package io.github.ethanscottwalden.colpool.ui.profile

import io.github.ethanscottwalden.colpool.data.model.OptionItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Kotlin copy of the seeded option lists must match `functions/src/shared/schema/options.ts` (ids, labels, order). */
class SeededOptionsTest {
    private val source: String = listOf("../../functions/src/shared/schema/options.ts", "functions/src/shared/schema/options.ts")
        .map(::File).first { it.exists() }.readText()

    /** Entries of `export const NAME: OptionEntry[] = list([ ["id", "Label"], ... ]);` in file order. */
    private fun seeded(name: String): List<OptionItem> {
        val body = Regex("""export const $name: OptionEntry\[\] = list\(\[(.*?)\]\);""", RegexOption.DOT_MATCHES_ALL)
            .find(source)?.groupValues?.get(1) ?: error("$name not found in options.ts")
        return Regex("""\[\s*"([^"]+)",\s*"([^"]+)"\s*\]""").findAll(body).map { OptionItem(it.groupValues[1], it.groupValues[2]) }.toList()
    }

    private fun check(name: String, copy: List<OptionItem>) {
        val expected = seeded(name)
        assertTrue("$name should not be empty", expected.isNotEmpty())
        assertEquals("$name differs from options.ts", expected, copy)
    }

    @Test fun genders() = check("GENDER_OPTIONS", SeededOptions.ALL.genders)
    @Test fun years() = check("YEAR_OPTIONS", SeededOptions.ALL.years)
    @Test fun majors() = check("MAJOR_OPTIONS", SeededOptions.ALL.majors)
    @Test fun interests() = check("INTEREST_OPTIONS", SeededOptions.ALL.interests)
    @Test fun traits() = check("TRAIT_OPTIONS", SeededOptions.ALL.traits)
}

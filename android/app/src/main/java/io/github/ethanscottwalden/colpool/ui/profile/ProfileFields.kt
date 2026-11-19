package io.github.ethanscottwalden.colpool.ui.profile

import io.github.ethanscottwalden.colpool.data.model.OptionItem

enum class OptionsStatus { Loading, Loaded, Failed }

/** RC-30 limits used by the profile forms (`PROFILE_LIMITS` in functions; Rules enforce the same). */
object ProfileLimits {
    const val AGE_MIN = 18
    const val ABOUT_ME_MAX = 300
    const val NAME_MAX = 30

    /** More digits than any real age; also keeps the number inside Int. */
    const val MAX_AGE_CHARS = 9
}

sealed interface AgeParse {
    data object Blank : AgeParse
    data object Invalid : AgeParse
    data class Valid(val value: Int) : AgeParse
}

/** Form rules shared by Onboarding and Edit Profile, so both screens treat the optional fields the same way. */
object ProfileFieldRules {
    /** RC-30: a whole number of 18 or older, no upper bound. Blank is fine (optional). */
    fun parseAge(text: String): AgeParse {
        val t = text.trim()
        if (t.isEmpty()) return AgeParse.Blank
        if (!t.all { it in '0'..'9' }) return AgeParse.Invalid
        val n = t.toIntOrNull() ?: return AgeParse.Invalid
        return if (n >= ProfileLimits.AGE_MIN) AgeParse.Valid(n) else AgeParse.Invalid
    }

    fun sanitizeAge(v: String): String = v.filter { c -> !c.isWhitespace() }.take(ProfileLimits.MAX_AGE_CHARS)

    /** A name as the Rules accept it: trimmed, not blank, at most 30 characters (RC-30). */
    fun validName(name: String): Boolean = name.trim().let { it.isNotEmpty() && it.length <= ProfileLimits.NAME_MAX }

    /** Keeps [id] only if it is in the seeded list (the list holds ids, never labels). */
    fun pick(id: String?, list: List<OptionItem>): String? = id?.takeIf { x -> list.any { it.id == x } }

    fun toggle(current: Set<String>, id: String, list: List<OptionItem>): Set<String> = when {
        id in current -> current - id
        list.any { it.id == id } -> current + id
        else -> current
    }

    /** Ids in the list's own order, so stored lists are stable. */
    fun inListOrder(list: List<OptionItem>, selected: Set<String>): List<String> = list.map { it.id }.filter { it in selected }
}

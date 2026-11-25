package io.github.ethanscottwalden.colpool.data.search

import android.content.Context
import io.github.ethanscottwalden.colpool.ui.search.MatchPreferences

/**
 * Where a student's host preferences live: on this device only, one set per signed-in uid (#37, provisional).
 * Never sent to a server and never logged. Cleared when that account logs out.
 */
interface PreferenceStore {
    fun load(uid: String): MatchPreferences
    fun save(uid: String, prefs: MatchPreferences)
    fun clear(uid: String)
}

/** Keeps preferences in memory only. Used by tests and previews. */
class InMemoryPreferenceStore : PreferenceStore {
    private val map = HashMap<String, MatchPreferences>()
    override fun load(uid: String) = map[uid] ?: MatchPreferences()
    override fun save(uid: String, prefs: MatchPreferences) { map[uid] = prefs }
    override fun clear(uid: String) { map.remove(uid) }
}

/** SharedPreferences-backed store (no new dependency). Backup is off for the app (`allowBackup=false`). */
class SharedPreferencesPreferenceStore(context: Context) : PreferenceStore {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(uid: String): MatchPreferences = MatchPreferences(
        interests = sp.getStringSet(key(uid, INTERESTS), null).orEmpty().toSet(),
        traits = sp.getStringSet(key(uid, TRAITS), null).orEmpty().toSet(),
        major = sp.getString(key(uid, MAJOR), null),
        year = sp.getString(key(uid, YEAR), null),
    )

    override fun save(uid: String, prefs: MatchPreferences) {
        sp.edit()
            .putStringSet(key(uid, INTERESTS), prefs.interests.toSet())
            .putStringSet(key(uid, TRAITS), prefs.traits.toSet())
            .putString(key(uid, MAJOR), prefs.major)
            .putString(key(uid, YEAR), prefs.year)
            .apply()
    }

    override fun clear(uid: String) {
        sp.edit().remove(key(uid, INTERESTS)).remove(key(uid, TRAITS)).remove(key(uid, MAJOR)).remove(key(uid, YEAR)).apply()
    }

    private fun key(uid: String, field: String) = "$uid/$field"

    private companion object {
        const val FILE = "colpool_match_preferences"
        const val INTERESTS = "interests"
        const val TRAITS = "traits"
        const val MAJOR = "major"
        const val YEAR = "year"
    }
}

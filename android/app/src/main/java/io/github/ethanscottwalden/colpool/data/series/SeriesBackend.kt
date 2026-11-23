package io.github.ethanscottwalden.colpool.data.series

/** One active campus point (`campusPoints/{id}`, RC-24). Hosts pick from these; there is no free text. */
data class CampusPoint(val id: String, val name: String)

/**
 * One validation error from the backend (ADR 0007): a stable [code], a dotted [path] into the series input
 * (`""` for the form as a whole) and limit or weekday [params]. The backend sends no display text.
 */
data class SeriesError(val code: String, val path: String, val params: Map<String, Any?> = emptyMap())

/** What the `routePreview` callable answered (ADR 0008). */
sealed interface RoutePreviewResult {
    data class Success(val draftId: String, val inputHash: String) : RoutePreviewResult

    /** `series-invalid` or `route-failed`: the errors to show against fields. */
    data class Invalid(val errors: List<SeriesError>) : RoutePreviewResult

    /** Routes was busy (`unavailable`). */
    data object Unavailable : RoutePreviewResult

    /** Anything else (`internal`, network, unknown). */
    data object Failed : RoutePreviewResult
}

/** Firebase access for Create Series. Faked in JVM tests. */
interface SeriesBackend {
    /** Reads the active campus points, in `order`. Throws on failure. */
    suspend fun loadCampusPoints(): List<CampusPoint>

    /** Calls `routePreview` with the `SeriesInput` map. Never throws except cancellation. */
    suspend fun routePreview(series: Map<String, Any>): RoutePreviewResult
}

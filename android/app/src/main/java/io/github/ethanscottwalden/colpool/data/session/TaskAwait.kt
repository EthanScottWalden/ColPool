package io.github.ethanscottwalden.colpool.data.session

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Suspends until a Firebase [Task] completes; throws its exception on failure. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { t ->
        if (t.isSuccessful) cont.resume(t.result) else cont.resumeWithException(t.exception ?: IllegalStateException("Task failed"))
    }
}

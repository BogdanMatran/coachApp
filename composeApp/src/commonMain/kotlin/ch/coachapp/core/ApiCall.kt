package ch.coachapp.core

import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlin.coroutines.cancellation.CancellationException

/** Runs a backend call and turns failures into a [Result] with a readable message. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(AppException(e.userMessage(), e))
    }

class AppException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun Throwable.userMessage(): String = when (this) {
    is AppException -> message ?: "Something went wrong"
    is AuthRestException -> errorDescription.ifBlank { error }
    is RestException -> error.ifBlank { "Request failed" }
    is HttpRequestException -> "Can't reach the server. Check your connection."
    else -> message ?: "Something went wrong"
}

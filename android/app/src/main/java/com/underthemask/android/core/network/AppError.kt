package com.underthemask.android.core.network

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

enum class ErrorKind { VALIDATION, UNAUTHORIZED, NOT_FOUND, CONFLICT, SERVER, NETWORK, UNKNOWN }

data class AppError(
    val kind: ErrorKind,
    val code: String,
    val userMessage: String,
)

class AppException(val error: AppError, cause: Throwable? = null) : Exception(error.userMessage, cause)

class ErrorMapper(private val json: Json) {
    fun map(throwable: Throwable): AppException {
        if (throwable is AppException) return throwable
        if (throwable is IOException) {
            return AppException(
                AppError(
                    ErrorKind.NETWORK,
                    "NETWORK_ERROR",
                    "Server trenutno nije dostupan. Proveri internet vezu i pokušaj ponovo.",
                ),
                throwable,
            )
        }
        if (throwable is SerializationException) {
            return AppException(
                AppError(ErrorKind.SERVER, "INVALID_RESPONSE", "Server je vratio nepoznat format odgovora."),
                throwable,
            )
        }
        if (throwable is HttpException) {
            val apiError = throwable.response()?.errorBody()?.string()?.let { body ->
                runCatching { json.decodeFromString<ApiErrorDto>(body) }.getOrNull()
            }
            val kind = when (throwable.code()) {
                400 -> ErrorKind.VALIDATION
                401, 403 -> ErrorKind.UNAUTHORIZED
                404 -> ErrorKind.NOT_FOUND
                409 -> ErrorKind.CONFLICT
                in 500..599 -> ErrorKind.SERVER
                else -> ErrorKind.UNKNOWN
            }
            return AppException(
                AppError(
                    kind = kind,
                    code = apiError?.code ?: "HTTP_${throwable.code()}",
                    userMessage = localizedMessage(
                        apiError?.code,
                        apiError?.message?.takeIf(String::isNotBlank) ?: defaultHttpMessage(throwable.code()),
                    ),
                ),
                throwable,
            )
        }
        return AppException(
            AppError(ErrorKind.UNKNOWN, "UNKNOWN_ERROR", "Zahtev nije uspeo. Pokušaj ponovo."),
            throwable,
        )
    }

    private fun defaultHttpMessage(status: Int) = when (status) {
        401, 403 -> "Sesija nije važeća ili akcija nije dozvoljena."
        404 -> "Lobby više ne postoji."
        409 -> "Akcija trenutno nije dozvoljena."
        in 500..599 -> "Server trenutno ima problem. Pokušaj ponovo."
        else -> "Zahtev nije uspeo."
    }

    private fun localizedMessage(code: String?, fallback: String): String = when (code) {
        "LOBBY_NOT_FOUND" -> "Lobby ne postoji ili više nije aktivan."
        "LOBBY_FULL" -> "Lobby je popunjen."
        "DUPLICATE_PLAYER_NAME" -> "To ime je već zauzeto u lobbyju."
        "INVALID_LOBBY_CODE" -> "Lobby kod nije važeći."
        "UNAUTHORIZED_PLAYER_TOKEN" -> "Sačuvana sesija više nije važeća."
        "ONLY_HOST_CAN_UPDATE_SETTINGS" -> "Samo host može da menja podešavanja."
        "ONLY_HOST_CAN_START_GAME" -> "Samo host može da pokrene igru."
        "ONLY_HOST_CAN_RESET_GAME" -> "Samo host može da vrati igrače u lobby."
        "SETTINGS_LOCKED" -> "Podešavanja su zaključana nakon početka igre."
        "LOBBY_NOT_WAITING" -> "Ova akcija je dozvoljena samo dok lobby čeka igrače."
        "INVALID_PLAYER_NAME" -> "Ime igrača nije važeća vrednost."
        "INVALID_GAME_SETTINGS" -> "Podešavanja igre nisu važeća."
        "NOT_ENOUGH_PLAYERS" -> "Nema dovoljno igrača za početak partije."
        "TOO_MANY_IMPOSTORS" -> "Broj impostora mora biti manji od broja igrača."
        "GAME_ALREADY_STARTED" -> "Partija je već u toku."
        "GAME_CONTENT_UNAVAILABLE" -> "Sadržaj za novu rundu trenutno nije dostupan. Pokušaj ponovo."
        "INVALID_CLUE" -> "Trag nije važeći."
        "SECRET_WORD_AS_CLUE" -> "Tajna reč ne može biti trag."
        "NOT_YOUR_TURN" -> "Nije tvoj red za slanje traga."
        "GAME_NOT_FINISHED" -> "Partija mora biti završena pre povratka u lobby."
        "VOTING_NOT_ACTIVE" -> "Glasanje još nije aktivno."
        "ALREADY_VOTED" -> "Tvoj glas je već zabeležen."
        "INVALID_VOTE_COUNT" -> "Izabran je pogrešan broj osumnjičenih."
        "INVALID_VOTE_TARGET" -> "Možeš glasati samo za druge igrače iz ove partije."
        "GAME_NOT_STARTED" -> "Partija još nije pokrenuta."
        "GAME_NOT_IN_PROGRESS" -> "Partija trenutno nije aktivna."
        "VALIDATION_ERROR" -> "Proveri unete podatke i pokušaj ponovo."
        else -> fallback
    }
}

suspend fun <T> ErrorMapper.apiCall(block: suspend () -> T): T = try {
    block()
} catch (throwable: Throwable) {
    throw map(throwable)
}

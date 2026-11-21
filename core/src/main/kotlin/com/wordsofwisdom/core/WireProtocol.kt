package com.wordsofwisdom.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("t")
sealed interface InboundMessage {

    @Serializable
    @SerialName("join")
    data class Join(
        val name: String = "",
        val token: String? = null,
        val avatarId: String? = null,
    ) : InboundMessage

    @Serializable
    @SerialName("config")
    data class Config(
        val length: GameLength? = null,
        val writeSeconds: Int? = null,
        val guessSeconds: Int? = null,
        val revealSeconds: Int? = null,
        val optionCount: Int? = null,
        val strictMetaphorGuard: Boolean? = null,
        val enableTieBreaker: Boolean? = null,
        val selectedThemes: List<String>? = null,
    ) : InboundMessage

    @Serializable
    @SerialName("add_bot")
    data class AddBot(val name: String? = null) : InboundMessage

    @Serializable
    @SerialName("remove_bot")
    data class RemoveBot(val playerId: String? = null) : InboundMessage

    @Serializable
    @SerialName("start")
    data object Start : InboundMessage

    @Serializable
    @SerialName("reroll")
    data object Reroll : InboundMessage

    @Serializable
    @SerialName("typing")
    data class Typing(
        val chars: Int = 0,
        val text: String? = null,
    ) : InboundMessage

    @Serializable
    @SerialName("submit_metaphor")
    data class SubmitMetaphor(val text: String = "") : InboundMessage

    @Serializable
    @SerialName("vote")
    data class Vote(val optionId: String = "") : InboundMessage

    @Serializable
    @SerialName("applause")
    data object Applause : InboundMessage

    @Serializable
    @SerialName("next")
    data object Next : InboundMessage

    @Serializable
    @SerialName("end_game")
    data object EndGame : InboundMessage

    @Serializable
    @SerialName("play_again")
    data object PlayAgain : InboundMessage
}

@Serializable
data class JoinedMessage(
    val t: String = "joined",
    val playerId: String,
    val token: String,
    val roomCode: String,
    val reconnected: Boolean = false,
)

@Serializable
data class MetaphorRejectedMessage(
    val t: String = "metaphor_rejected",
    val reason: String,
    val forbiddenWords: List<String> = emptyList(),
)

@Serializable
data class ErrorMessage(
    val t: String = "error",
    val code: String,
    val message: String,
)

/** Centralized JSON codec for phone <-> TV WebSocket wire protocol. */
@OptIn(ExperimentalSerializationApi::class)
object WireProtocol {

    val json: Json = Json {
        classDiscriminator = "t"
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun decodeInbound(raw: String): InboundMessage? = runCatching {
        json.decodeFromString(InboundMessage.serializer(), raw)
    }.getOrNull()

    fun encodeJoined(msg: JoinedMessage): String =
        json.encodeToString(JoinedMessage.serializer(), msg)

    fun encodeState(msg: PhoneStateMessage): String =
        json.encodeToString(PhoneStateMessage.serializer(), msg)

    fun encodeMetaphorRejected(msg: MetaphorRejectedMessage): String =
        json.encodeToString(MetaphorRejectedMessage.serializer(), msg)

    fun encodeError(code: String, message: String): String =
        json.encodeToString(ErrorMessage.serializer(), ErrorMessage(code = code, message = message))
}

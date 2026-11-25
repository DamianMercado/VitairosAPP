package cl.vitairos.captura.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class MessageKind { INFO, SUCCESS, WARNING, ERROR }

data class UiMessage(val text: String, val kind: MessageKind)

/**
 * Alertas dentro de la app (cuestionario §3): enviado correctamente, sin conexión, error,
 * pendiente de reintento. Se muestran como Snackbar en cualquier pantalla. Sin notificaciones push.
 */
class AppEvents {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 32)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun info(text: String) = _messages.tryEmit(UiMessage(text, MessageKind.INFO))
    fun success(text: String) = _messages.tryEmit(UiMessage(text, MessageKind.SUCCESS))
    fun warning(text: String) = _messages.tryEmit(UiMessage(text, MessageKind.WARNING))
    fun error(text: String) = _messages.tryEmit(UiMessage(text, MessageKind.ERROR))
}

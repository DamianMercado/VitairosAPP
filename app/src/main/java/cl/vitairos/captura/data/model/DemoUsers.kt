package cl.vitairos.captura.data.model

import java.security.MessageDigest

/**
 * Usuarios FICTICIOS para el inicio de sesión simulado (cuestionario §2).
 * Solo se guarda el hash SHA-256 de la contraseña; nunca la contraseña en texto plano.
 * Contraseña de demostración para los tres usuarios: demo123
 */
data class DemoUser(
    val id: String,
    val username: String,
    val displayName: String,
    val role: UserRole,
    internal val passwordHash: String,
)

object DemoUsers {
    val all: List<DemoUser> = listOf(
        DemoUser("u-op-01", "operador1", "Camila Rojas", UserRole.OPERADOR,
            "aac2199d4ce1df971c20415b2afc39537d85883137208e35dfbdf80806afc404"),
        DemoUser("u-op-02", "operador2", "Diego Fuentes", UserRole.OPERADOR,
            "d8bec6c5e92085bf0cd5a6fa9777d45241cafea9facb8bbc8fc55708966af9d7"),
        DemoUser("u-sup-01", "supervisor", "Valentina Soto", UserRole.SUPERVISOR,
            "034be1c0e0552ffd3b2df19329be0b35c5fbbefa7473a37083052e991a342dd3"),
    )

    fun byId(id: String?): DemoUser? = all.firstOrNull { it.id == id }

    /** Valida localmente usuario/contraseña. Devuelve el usuario o null. */
    fun authenticate(username: String, password: String): DemoUser? {
        val user = all.firstOrNull { it.username.equals(username.trim(), ignoreCase = true) } ?: return null
        return if (hash(user.username, password) == user.passwordHash) user else null
    }

    fun hash(username: String, password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("vitairos-mvp:$username:$password".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

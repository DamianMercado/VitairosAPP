package cl.vitairos.captura.ui.login

import cl.vitairos.captura.ui.components.ErrorIcon
import cl.vitairos.captura.ui.components.FieldError
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.vitairos.captura.AppContainer
import cl.vitairos.captura.R
import cl.vitairos.captura.data.model.DemoUsers
import cl.vitairos.captura.data.model.UserRole
import cl.vitairos.captura.ui.navigation.appViewModel
import cl.vitairos.captura.ui.theme.Vitairos
import cl.vitairos.captura.ui.theme.monoStyle
import kotlinx.coroutines.launch

class LoginViewModel(private val container: AppContainer) : ViewModel() {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var showPassword by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
        private set

    fun login() {
        val user = DemoUsers.authenticate(username, password)
        if (user == null) {
            error = "Usuario o contraseña incorrectos."
            return
        }
        error = null
        password = "" // la contraseña no se conserva en memoria ni en disco
        viewModelScope.launch {
            container.settingsStore.setUser(user.id)
            container.events.success("Sesión iniciada: ${user.displayName} (${user.role.label})")
        }
    }
}

@Composable
fun LoginScreen() {
    val vm = appViewModel { c, _ -> LoginViewModel(c) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Vitairos.VerdeNoche)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(painterResource(R.drawable.vitairos_isotipo), contentDescription = "Isotipo VITAIROS", modifier = Modifier.size(88.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "VITAIROS",
            style = MaterialTheme.typography.headlineLarge,
            color = Vitairos.Hueso,
            letterSpacing = 4.sp,
        )
        Text("INDUSTRIAL AI", style = monoStyle(14), color = Vitairos.Oro, letterSpacing = 3.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Captura móvil de reportes operacionales",
            style = MaterialTheme.typography.bodyMedium,
            color = Vitairos.Niebla,
        )
        Spacer(Modifier.height(28.dp))

        Surface(shape = RoundedCornerShape(20.dp), color = Vitairos.Hueso, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Selecciona un usuario de demostración o escribe tus credenciales ficticias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vitairos.VerdeMineral,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DemoUsers.all.forEach { u ->
                        FilterChip(
                            selected = vm.username == u.username,
                            onClick = { vm.username = u.username },
                            label = { Text(u.displayName.substringBefore(" "), maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    if (u.role == UserRole.SUPERVISOR) Icons.Outlined.AdminPanelSettings else Icons.Outlined.Engineering,
                                    contentDescription = u.role.label,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Vitairos.Niebla,
                                selectedLabelColor = Vitairos.VerdeNoche,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.username,
                    onValueChange = { vm.username = it },
                    label = { Text("Usuario") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    trailingIcon = if (vm.error != null) (@Composable { ErrorIcon() }) else null,
                    isError = vm.error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.password,
                    onValueChange = { vm.password = it },
                    label = { Text("Contraseña") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vm.error != null) ErrorIcon()
                        IconButton(onClick = { vm.showPassword = !vm.showPassword }) {
                            Icon(
                                if (vm.showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (vm.showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                            )
                        }
                        }
                    },
                    visualTransformation = if (vm.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    isError = vm.error != null,
                    supportingText = { vm.error?.let { FieldError(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.login() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.login() },
                    enabled = vm.username.isNotBlank() && vm.password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo, contentColor = Vitairos.Hueso),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Ingresar", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Prototipo académico DSY1105 · usuarios ficticios (contraseña demo: demo123).",
            style = MaterialTheme.typography.bodySmall,
            color = Vitairos.Acero,
        )
        Spacer(Modifier.width(1.dp))
    }
}

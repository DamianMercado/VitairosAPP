package cl.vitairos.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import cl.vitairos.app.R
import cl.vitairos.app.data.Catalogo
import cl.vitairos.app.ui.theme.Vitairos
import cl.vitairos.app.ui.theme.monoStyle

@Composable
fun LoginScreen(error: String?, alIngresar: (String, String) -> Unit) {
    var usuario by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }
    var verClave by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Vitairos.VerdeNoche)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(painterResource(R.drawable.vitairos_isotipo), contentDescription = "VITAIROS", modifier = Modifier.size(84.dp))
        Spacer(Modifier.height(12.dp))
        Text("VITAIROS", style = MaterialTheme.typography.headlineLarge, color = Vitairos.Hueso, letterSpacing = 4.sp)
        Text("INDUSTRIAL AI", style = monoStyle(14), color = Vitairos.Oro, letterSpacing = 3.sp)
        Spacer(Modifier.height(28.dp))

        Surface(shape = RoundedCornerShape(20.dp), color = Vitairos.Hueso, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Catalogo.usuarios.forEach { u ->
                        FilterChip(
                            selected = usuario == u.id,
                            onClick = { usuario = u.id },
                            label = { Text(u.nombre.substringBefore(" "), maxLines = 1) },
                        )
                    }
                }
                OutlinedTextField(
                    value = usuario,
                    onValueChange = { usuario = it },
                    label = { Text("Usuario") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    trailingIcon = if (error != null) (@Composable { IconoError() }) else null,
                    isError = error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = clave,
                    onValueChange = { clave = it },
                    label = { Text("Contraseña") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        if (error != null) IconoError()
                        IconButton(onClick = { verClave = !verClave }) {
                            Icon(
                                if (verClave) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (verClave) "Ocultar contraseña" else "Mostrar contraseña",
                            )
                        }
                        }
                    },
                    visualTransformation = if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { msg -> @Composable { MensajeError(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { alIngresar(usuario, clave) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        alIngresar(usuario, clave)
                        clave = ""
                    },
                    enabled = usuario.isNotBlank() && clave.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Vitairos.VerdeProfundo, contentColor = Vitairos.Hueso),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Ingresar", fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Prototipo académico DSY1105 · usuarios ficticios (contraseña demo: demo123).",
            style = MaterialTheme.typography.bodySmall,
            color = Vitairos.Acero,
        )
    }
}

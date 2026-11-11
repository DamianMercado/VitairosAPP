package cl.vitairos.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.vitairos.app.R
import cl.vitairos.app.ui.theme.Vitairos

/** Pantalla de carga que se muestra unos segundos al abrir la app: logo, nombre y rueda de carga. */
@Composable
fun CargaScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Vitairos.VerdeNoche),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.vitairos_isotipo),
                contentDescription = "Logo VITAIROS",
                modifier = Modifier.size(120.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "VITAIROS",
                color = Vitairos.Hueso,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(color = Vitairos.Cobre, strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text("Cargando…", color = Vitairos.Niebla, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

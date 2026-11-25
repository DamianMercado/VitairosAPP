package cl.vitairos.captura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import cl.vitairos.captura.ui.navigation.AppRoot
import cl.vitairos.captura.ui.theme.Vitairos
import cl.vitairos.captura.ui.theme.VitairosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Vitairos.VerdeNoche.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Vitairos.Blanco.toArgb(), Vitairos.VerdeNoche.toArgb()),
        )
        super.onCreate(savedInstanceState)
        val container = (application as VitairosApp).container
        setContent {
            VitairosTheme {
                AppRoot(container)
            }
        }
    }
}

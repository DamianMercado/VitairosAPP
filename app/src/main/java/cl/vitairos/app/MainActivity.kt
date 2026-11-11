package cl.vitairos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import cl.vitairos.app.ui.AppRoot
import cl.vitairos.app.ui.theme.Vitairos
import cl.vitairos.app.ui.theme.VitairosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Vitairos.VerdeNoche.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Vitairos.Blanco.toArgb(), Vitairos.VerdeNoche.toArgb()),
        )
        super.onCreate(savedInstanceState)
        setContent {
            VitairosTheme {
                AppRoot()
            }
        }
    }
}

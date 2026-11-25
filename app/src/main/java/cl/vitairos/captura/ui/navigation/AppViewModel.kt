package cl.vitairos.captura.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.vitairos.captura.AppContainer
import cl.vitairos.captura.VitairosApp

/** Crea ViewModels inyectando el [AppContainer] y el SavedStateHandle (argumentos de navegación). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as VitairosApp).container
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(container, createSavedStateHandle()) }
        },
    )
}

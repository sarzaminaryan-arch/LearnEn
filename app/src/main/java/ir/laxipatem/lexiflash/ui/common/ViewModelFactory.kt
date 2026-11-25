package ir.laxipatem.lexiflash.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.laxipatem.lexiflash.AppContainer

/**
 * ViewModel construction without Hilt.
 *
 * Each screen declares the arguments it needs from the container and the
 * navigation back stack; this factory turns them into a `viewModelFactory { }`
 * that the Compose `viewModel(...)` call accepts. Because arguments are captured
 * per screen rather than read from a global graph, a ViewModel's dependencies are
 * visible at its call site.
 */
object ViewModelFactories {

    /** Builds a factory for a ViewModel that only needs the container. */
    inline fun <reified VM : ViewModel> containerFactory(
        container: AppContainer,
        crossinline create: (AppContainer) -> VM
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer { create(container) }
    }

    /** Builds a factory for a ViewModel that also needs navigation arguments. */
    inline fun <reified VM : ViewModel> argsFactory(
        container: AppContainer,
        crossinline create: (AppContainer, CreationExtras) -> VM
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer { create(container, this) }
    }
}

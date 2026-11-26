package ir.laxipatem.lexiflash

import android.app.Application

/**
 * Application entry point.
 *
 * Owns the single [AppContainer] instance. Nothing here touches the disk on the
 * main thread: the container is built lazily and the warm-up work is dispatched
 * to the IO scope.
 */
class LexiFlashApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
        container = AppContainer(this)
        container.warmUp()
    }

    override fun onTerminate() {
        container.shutdown()
        super.onTerminate()
    }
}

/** Convenience accessor used by Activities and ViewModel factories. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as LexiFlashApp).container

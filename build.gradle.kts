// Root build file. Plugins are declared here with `apply false` so that the
// app module can apply them by alias without duplicating version numbers.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}

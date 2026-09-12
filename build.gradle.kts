plugins {
    // AGP 9 menyediakan dukungan Kotlin secara bawaan (built-in Kotlin); plugin
    // org.jetbrains.kotlin.android sudah tidak diperlukan dan justru MENGGAGALKAN
    // build bila tetap diterapkan ("no longer required for Kotlin support since AGP 9.0").
    alias(libs.plugins.android.application) apply false
}

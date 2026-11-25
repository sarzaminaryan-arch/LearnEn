# ---------------------------------------------------------------------------
# LexiFlash R8 / ProGuard rules
# The release build uses proguard-android-optimize.txt so that
# -assumenosideeffects below actually strips logging.
# ---------------------------------------------------------------------------

# Strip all Log calls from release builds. With the "optimize" variant of the
# default rules the optimiser is allowed to remove the calls entirely.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** isLoggable(...);
}

-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# --- Room ------------------------------------------------------------------
# Room ships consumer rules, but the generated implementations and the schema
# classes must survive shrinking for the database to open correctly.
-keep class ir.laxipatem.lexiflash.data.db.** { *; }
-keepclassmembers class ir.laxipatem.lexiflash.data.db.** { *; }

# --- ML Kit (online flavour only) ------------------------------------------
# The translate model is loaded reflectively by the SDK; keep the SDK intact.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# --- kotlinx.coroutines ----------------------------------------------------
# ServiceLoader entries for the main dispatcher must be kept.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- org.json --------------------------------------------------------------
# Used directly for deck.json and catalogue parsing.
-dontwarn org.json.**

# ProGuard rules for Bismuth

# Rive Android Runtime & JNI
-keep class app.rive.runtime.kotlin.** { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Lottie Compose
-keep class com.airbnb.lottie.** { *; }

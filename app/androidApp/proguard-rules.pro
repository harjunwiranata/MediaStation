# MediaStation ProGuard Rules

# LibVLC (Fixes release crash due to JNI)
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.interfaces.** { *; }
-keep class org.videolan.libvlc.util.** { *; }
-keepclasseswithmembers class * {
    native <methods>;
}

# Preserve ViewModels and their constructors for reflection-based creation
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Kotlin Serialization
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembers class ** {
    *** Companion;
    *** $serializer;
}
-keep class id.storia.media.data.model.** { *; }
-keep class id.storia.media.Route** { *; }

# Redundant library rules (Coil, Coroutines, Compose, Kotlinx Serialization)
# have been removed as they provide their own consumer keep rules in modern versions.

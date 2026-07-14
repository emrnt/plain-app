-keepattributes *Annotation*
-keep class com.ismartcoding.plain.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class * implements java.io.Serializable { *; }

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.aicouples.therapy.**$$serializer { *; }
-keepclassmembers class com.aicouples.therapy.** {
    *** Companion;
}
-keepclasseswithmembers class com.aicouples.therapy.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class io.github.jan.supabase.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**

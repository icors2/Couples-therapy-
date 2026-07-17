-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.aicouples.therapy.data.model.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**

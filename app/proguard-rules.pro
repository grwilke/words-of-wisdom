# Keep kotlinx.serialization serializers & DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.wordsofwisdom.core.**$$serializer { *; }
-keepclassmembers class com.wordsofwisdom.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.wordsofwisdom.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor CIO + Coroutines
-dontwarn io.ktor.**
-dontwarn kotlinx.atomicfu.**
-dontwarn org.slf4j.**
-keep class io.ktor.** { *; }

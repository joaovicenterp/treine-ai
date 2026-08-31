# MediaPipe carrega classes e bibliotecas nativas por reflexão.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn autovalue.shaded.**
-dontwarn javax.lang.model.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.treineai.app.data.** {
    *** Companion;
    <fields>;
}
-keepclasseswithmembers class com.treineai.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

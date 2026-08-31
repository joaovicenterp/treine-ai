# MediaPipe carrega classes e bibliotecas nativas por reflexão.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn autovalue.shaded.**
-dontwarn javax.lang.model.**

# Dependências transitivas do MediaPipe arrastam processadores de anotação
# (auto-value) que referenciam classes de javax.annotation.processing — usadas
# só em tempo de compilação, nunca em execução. Não vão para o app; o R8 só
# precisa ser instruído a ignorar essas referências ausentes.
-dontwarn javax.annotation.processing.**
-dontwarn javax.annotation.**
-dontwarn com.google.auto.value.**
-dontwarn com.google.auto.common.**

# Anotações de ferramentas que o Guava (dependência transitiva) referencia
# e que também não existem em execução no Android.
-dontwarn com.google.errorprone.**
-dontwarn com.google.j2objc.**
-dontwarn org.checkerframework.**
-dontwarn javax.inject.**

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

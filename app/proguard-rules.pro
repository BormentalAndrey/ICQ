# Сохраняем все нативные методы (JNI) и классы, в которых они находятся
-keepclasseswithmembernames class * {
    native <methods>;
}

# Запрещаем обфускацию классов, к которым мы обращаемся из C++ ядра
-keep class com.icq.mobile.** { *; }

# Правила для WebRTC (предотвращают крэши при релизной сборке)
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Оставляем аннотации JNI
-keep @interface androidx.annotation.Keep
-keep @androidx.annotation.Keep class *
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <init>(...);
}

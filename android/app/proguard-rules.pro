# Capacitor / WebView
-keep class com.getcapacitor.** { *; }
-keep class com.deciphertahiro.app.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn com.getcapacitor.**

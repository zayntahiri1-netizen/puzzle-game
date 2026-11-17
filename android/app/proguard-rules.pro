# ════════════════════════════════════════════════════════════════════
#  قواعد R8 — Decipher Tahiro
#
#  المبدأ: نحفظ فقط ما يُنادى بالاسم أو بالانعكاس من كودنا ومن طبقة
#  Capacitor. ولا نحفظ مكتبات Google: play-services-ads و UMP و Firebase
#  تشحن قواعدها الخاصة داخل ملفات AAR (consumer rules) ويطبّقها R8
#  تلقائياً. الحفظ الشامل لها (-keep class com.google.** { *; }) كان
#  يمنع R8 من تحسين الجزء الأكبر من DEX — وهو سبب نسبة 1% في Play Console.
# ════════════════════════════════════════════════════════════════════

# ── ① كود التطبيق والجسر مع JavaScript ──────────────────────────────
# الجسور الثلاثة تُنادى بأسماء دوالّها من www/index.html:
#   AndroidAds / AndroidConsent / AndroidLinks
# كود التطبيق صغير، فنحفظه كاملاً: لا مكسب يُذكر من تشويشه، والخطر كبير.
-keep class com.deciphertahiro.app.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── ② Capacitor — الجسر والإضافات تُحمَّل بالانعكاس ──────────────────
# صغير الحجم، ويعتمد على الأسماء في المراسلة مع JavaScript. يبقى محفوظاً.
-keep class com.getcapacitor.** { *; }
-keep class com.capacitorjs.** { *; }
-dontwarn com.getcapacitor.**
-dontwarn com.capacitorjs.**

# ── ③ Cordova — يحمّله Capacitor بالانعكاس ولو لم تُستعمل إضافة ─────
-keep class org.apache.cordova.** { *; }
-dontwarn org.apache.cordova.**

# ── ④ مكتبات Google — لا حفظ شامل ────────────────────────────────────
# play-services-ads و user-messaging-platform و firebase-* تأتي بقواعدها.
# نُسكت التحذيرات فقط عن أصناف اختيارية قد لا تكون في مسار البناء.
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**
-dontwarn com.google.android.ump.**

# ── ⑤ تقارير الأعطال ────────────────────────────────────────────────
# أرقام الأسطر تبقى، فتُفكّ تقارير الأعطال بملف mapping.txt الذي يُضمَّن
# في ملف .aab تلقائياً ويرفعه Play معه. واسم الملف المصدر يُستبدل.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

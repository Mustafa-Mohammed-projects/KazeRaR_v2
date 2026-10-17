# Kaze RAR

تطبيق أندرويد (Kotlin + Jetpack Compose) لفك ضغط الملفات بصيغ متعددة.
An Android archive extractor written in Kotlin with Jetpack Compose.

**Formats:** ZIP (jar/apk/cbz) · RAR (v4) · 7Z · TAR · TAR.GZ/TGZ · TAR.BZ2 · TAR.XZ · GZ · BZ2 · XZ
**Passwords:** RAR and 7Z. **Not supported:** RAR5, password-protected ZIP.

## البناء عبر GitHub / Build on GitHub

1. أنشئ مستودعاً جديداً وارفع محتويات هذا المجلد كما هي.
2. (اختياري) ضع صورتك `presplash.png` في جذر المستودع بجانب `build.gradle.kts`؛ يستبدلها الـ workflow تلقائياً وتظهر 3 ثوانٍ عند فتح التطبيق.
3. افتح تبويب **Actions** ← **Build Kaze RAR** ← **Run workflow** (أو أي push إلى `main`).
4. عند اكتمال البناء نزّل `Kaze-RAR-apk` من قسم **Artifacts** وثبّت `Kaze-RAR.apk`.

## Local build

```
gradle assembleRelease      # Gradle 8.9+, JDK 17, Android SDK 35
```

## Notes

- الحد الأدنى: Android 10 (API 29). تُحفظ الملفات في `Download/Kaze RAR/<اسم الملف>/` بدون أي صلاحيات تخزين.
- Minimum Android 10 (API 29). Output goes to `Download/Kaze RAR/<archive name>/` via MediaStore, so no storage permission is needed.
- يدعم الواجهة العربية (RTL) والإنجليزية تلقائياً حسب لغة الجهاز.

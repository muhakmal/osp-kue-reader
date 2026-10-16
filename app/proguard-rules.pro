# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# IronSource / LevelPlay mediation SDK — required so release (minified) builds
# don't silently break ad loading/rendering.
-keep class com.ironsource.** { *; }
-keep interface com.ironsource.** { *; }
-dontwarn com.ironsource.**
-keep class com.google.android.gms.ads.identifier.** { *; }
-dontwarn com.google.android.gms.ads.identifier.**
# Google User Messaging Platform (CMP consent). Kelasnya dipanggil lewat callback
# dari luar kode kita, jadi harus selamat dari minifikasi.
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# Google Play In-App Review
-keep class com.google.android.play.core.review.** { *; }

# Google Play In-App Updates
-keep class com.google.android.play.core.appupdate.** { *; }
-keep class com.google.android.play.core.install.** { *; }
-dontwarn com.google.android.play.core.**

# Entitas Room diakses lewat refleksi oleh kode generated.
-keep class com.baskom.pembacakue.data.ScanHistoryEntity { *; }
-keep class com.baskom.pembacakue.data.CardProfile { *; }

# Model Firestore di-deserialisasi lewat refleksi (toObject).
-keep class com.baskom.pembacakue.data.TerminalMapping { *; }
-keepclassmembers class com.baskom.pembacakue.data.TerminalMapping {
    <init>(...);
    public *** get*();
    public void set*(***);
}

# TileService & AppWidgetProvider dirujuk dari manifest, bukan dari kode.
-keep class com.baskom.pembacakue.quick.** { *; }

# Entitas transaksi kartu (Room, sejak skema versi 3).
-keep class com.baskom.pembacakue.data.CardTransactionEntity { *; }

# ViewModel dibuat lewat refleksi oleh SavedStateViewModelFactory, jadi konstruktor
# (Application, SavedStateHandle) harus selamat dari minifikasi.
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

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
# --- NFC Share ---
# Shizuku + HiddenApiBypass talk to the system through binder/reflection; keep them intact.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn rikka.shizuku.**
# The NFC library persists the emulated data by enum/class name; keep names stable.
-keep class com.luigivampa92.ndefemulation.** { *; }

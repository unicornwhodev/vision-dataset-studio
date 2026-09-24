# Add project specific ProGuard rules here.
# Moshi's enum adapter reflects on persisted constant names (for example LOCALIZED).
# Keep application enum constants stable across minification and app upgrades.
-keep enum com.unicornwhodev.visiondatasetstudio.** { *; }
# Keep the exact external call boundary used by the separately mapped test APK.
# The same rules apply to the APK we distribute; there is no less-optimized QA variant.
# Declared separately in proguardFiles so Gradle tracks every API rule change.
# Test lambdas subclass this runtime base. TraceReferences sees call sites, but
# not implicit overrides: R8 must not reorder these virtual method parameters.
-keep class kotlin.coroutines.jvm.internal.BaseContinuationImpl {
    public <methods>;
    protected <methods>;
}
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

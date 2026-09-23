# Error Prone's compile-time annotation refers to the JDK compiler model.
# Android does not use that annotation at runtime. Scope this to the test APK.
-dontwarn javax.lang.model.element.Modifier

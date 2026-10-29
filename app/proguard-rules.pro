# Keep JavaScript interface methods if used
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

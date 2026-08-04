# ParcelPal uses no reflection-based model serializer. Keep only the ZXing activity
# names referenced by its merged manifest.
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**

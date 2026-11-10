# yt-dlp wrapper (uses Jackson reflection for VideoInfo)
-keep class com.yausername.** { *; }
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn org.apache.commons.compress.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
# app models (kotlinx.serialization)
-keep class com.lyra.player.data.** { *; }

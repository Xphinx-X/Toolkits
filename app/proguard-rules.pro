# 7-Zip-JBinding
-keep class net.sf.sevenzipjbinding.** { *; }
-dontwarn net.sf.sevenzipjbinding.**

# Libarchive
-keep class me.zhanghai.android.libarchive.** { *; }
-dontwarn me.zhanghai.android.libarchive.**

# Zip4j
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# Apache Commons Compress
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

# Zstd
-keep class com.github.luben.zstd.** { *; }
-dontwarn com.github.luben.zstd.**

# junrar / logging facades pulled in by archive libs (no android binding shipped)
-dontwarn org.slf4j.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.log4j.**

# Skylark ProGuard/R8 rules

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers,allowshrinking class kotlinx.serialization.** { *; }

# Room 生成的实现类
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# sing-box libbox (JNI 绑定，后续接入内核时启用)
# -keep class io.nekohasekai.libbox.** { *; }

# kotlinx.serialization: keep generated serializers and companions (wire payloads use protobuf/json).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class edu.cit.resqmesh.**$$serializer { *; }
-keepclassmembers class edu.cit.resqmesh.** { *** Companion; }
-keepclasseswithmembers class edu.cit.resqmesh.** { kotlinx.serialization.KSerializer serializer(...); }

# Room: Room.databaseBuilder instantiates AppDatabase_Impl reflectively. R8 full mode
# (AGP 9 default) drops its no-arg constructor under Room's own `-keep class` rule.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Keep line numbers for crash traces.
-keepattributes SourceFile,LineNumberTable

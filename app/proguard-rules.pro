# R8 rules for ConvoTrack release builds.
#
# Keep this file narrow: package-wide keeps (-keep class com.foo.**) switch off obfuscation and
# optimisation for everything inside, which is exactly what Play vitals' DEX code optimization
# score penalises. Most libraries here (Retrofit, OkHttp, kotlinx.serialization, Clerk, Facebook
# SDK, Billing, ML Kit, Maps) ship their own consumer rules, so only the gaps are listed.

# Readable crash stacks in Play / Crashlytics-style tooling.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: the plugin generates Companion.serializer() and $$serializer classes that
# the library finds reflectively. Library consumer rules cover most of it; this keeps our own
# @Serializable models' serializers when they are looked up by name.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.danglingmind.convotrack.** {
    *** Companion;
}
-keepclasseswithmembers class com.danglingmind.convotrack.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit service interfaces are proxied reflectively; keep their annotated methods.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

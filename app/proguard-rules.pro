# Retrofit avec R8 full mode : conserver les signatures génériques des
# interfaces et des continuations suspend.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Plateformes TLS optionnelles référencées par OkHttp mais absentes
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization : ceinture et bretelles sur nos modèles
# (les règles consumer des libs couvrent les sérialiseurs générés)
-keep class fr.easter.brewhome.data.** { *; }

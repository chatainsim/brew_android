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

# Widgets Glance : Glance retrouve les widgets posés par le nom de classe de leur
# GlanceAppWidget. Sans cette règle, R8 fusionnait les 4 classes de widget en une seule :
# updateAll() de BrewWidget (à chaque ouverture de l'appli) redessinait alors aussi le
# widget « Brassins en cours » avec « cave + échéance ».
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidget

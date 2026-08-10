# R8 rules for the release build.
#
# The only thing here that is genuinely load-bearing is kotlinx.serialization. The shared
# core's persisted model is reflected over by generated `$$serializer` classes and `Companion`
# objects that nothing references by name, so R8 is free to strip or rename them — and the
# failure is silent and total: `CyclePersistence.decode` starts returning null, the app looks
# like a fresh install, and the user's cycle history appears to have vanished.
#
# kotlinx-serialization ships consumer rules of its own; these narrow ones cover the app's
# own @Serializable model explicitly rather than relying on that.

-keepattributes *Annotation*, InnerClasses

# Keep every generated serializer for the persisted model.
-if @kotlinx.serialization.Serializable class app.cycluna.core.**
-keepclassmembers class app.cycluna.core.** {
    *** Companion;
}
-keepclasseswithmembers class app.cycluna.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.cycluna.core.**$$serializer { *; }

# Compose and AndroidX ship their own consumer rules; nothing to add.

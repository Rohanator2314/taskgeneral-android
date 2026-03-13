# Add project specific ProGuard rules here.

# UniFFI native library preservation
# Keep all native methods and JNI related classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve UniFFI generated bindings
-keep class uniffi.** { *; }
-keep class uniffi.taskgeneral_core.** { *; }

# Preserve enums (used in UniFFI)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

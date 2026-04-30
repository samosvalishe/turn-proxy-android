# ── Stacktraces ───────────────────────────────────────────────────────────────
# Сохраняем имена файлов и номера строк для читаемых крэш-репортов
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── JSch ──────────────────────────────────────────────────────────────────────
# JSch загружает криптографические алгоритмы через reflection по имени класса.
# Без этого правила R8 удалит/переименует нужные классы и SSH не будет работать.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# ── EdDSA (Ed25519) ───────────────────────────────────────────────────────────
# JSch обращается к net.i2p.crypto.eddsa через Security Provider (reflection).
# R8 стрипает эти классы в release → Ed25519-ключи не работают.
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.i2p.crypto.eddsa.**

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Android Security Crypto (EncryptedSharedPreferences) ──────────────────────
-keep class androidx.security.crypto.** { *; }

# ── DataStore ─────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ── Tink (транзитивная зависимость security-crypto) ───────────────────────────
# Аннотационные библиотеки не включены в runtime, предупреждения безопасно подавить
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy

# ── Stacktraces ───────────────────────────────────────────────────────────────
# Сохраняем имена файлов и номера строк для читаемых крэш-репортов
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── JSch ──────────────────────────────────────────────────────────────────────
# JSch загружает криптографические алгоритмы через reflection по имени класса.
# Без этого правила R8 удалит/переименует нужные классы и SSH не будет работать.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# ── Bouncy Castle (Ed25519 / curve25519 / chacha20) ───────────────────────────
# mwiede/jsch 2.x подгружает алгоритмы BC через reflection. R8 стрипает эти
# классы в release -> Ed25519-ключи и современный KEX не работают.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# ── Compile-only аннотации (errorprone/javax) не включены в runtime ───────────
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy

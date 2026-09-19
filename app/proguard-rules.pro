# ── Stacktraces ───────────────────────────────────────────────────────────────
# Сохраняем имена файлов и номера строк для читаемых крэш-репортов
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── JSch ──────────────────────────────────────────────────────────────────────
-keep class * implements com.jcraft.jsch.Cipher { <init>(); }
-keep class * implements com.jcraft.jsch.Compression { <init>(); }
-keep class * implements com.jcraft.jsch.DH { <init>(); }
-keep class * implements com.jcraft.jsch.ECDH { <init>(); }
-keep class * implements com.jcraft.jsch.GSSContext { <init>(); }
-keep class * implements com.jcraft.jsch.HASH { <init>(); }
-keep class * implements com.jcraft.jsch.KDF { <init>(); }
-keep class * implements com.jcraft.jsch.KEM { <init>(); }
-keep class * extends com.jcraft.jsch.KeyExchange { <init>(); }
-keep class * implements com.jcraft.jsch.KeyPairGenDSA { <init>(); }
-keep class * implements com.jcraft.jsch.KeyPairGenECDSA { <init>(); }
-keep class * implements com.jcraft.jsch.KeyPairGenEdDSA { <init>(); }
-keep class * implements com.jcraft.jsch.KeyPairGenRSA { <init>(); }
-keep class * implements com.jcraft.jsch.MAC { <init>(); }
-keep class * implements com.jcraft.jsch.Random { <init>(); }
-keep class * implements com.jcraft.jsch.Signature { <init>(); }
-keep class * extends com.jcraft.jsch.UserAuth { <init>(); }
-keep class * implements com.jcraft.jsch.XDH { <init>(); }
-dontwarn com.jcraft.jsch.**

# ── Bouncy Castle ─────────────────────────────────────────────────────────────
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# ── Ядро (gomobile) ───────────────────────────────────────────────────────────
# Go зовёт Java по именам классов и методов через Seq, дефолтного правила на
# native <methods> мало: без keep release падает в рантайме на Seq.
-keep class com.freeturn.core.** { *; }
-keep class go.** { *; }

# ── Compile-only аннотации (errorprone/javax) не включены в runtime ───────────
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy

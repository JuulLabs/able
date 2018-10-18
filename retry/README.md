# Retry

`Retry` wraps a `Gatt` to add I/O retry functionality and on-demand connection establishment.

```kotlin
val gatt = device
    .connectGattOrThrow(context, autoConnect = false)
    .withRetry(1L, MINUTES)
```

# Setup

## Gradle

[![JitPack version](https://jitpack.io/v/JUUL-OSS/able.svg)](https://jitpack.io/#JUUL-OSS/able)

```groovy
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    implementation "com.github.JUUL-OSS.able:retry:$version"
}
```

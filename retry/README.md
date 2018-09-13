# Retry

`Retry` wraps a `Gatt` to add I/O retry functionality and on-demand connection establishment.

```kotlin
val gatt = device
    .connectGattOrThrow(context, autoConnect = false)
    .withRetry(1L, MINUTES)
```

# Setup

## Gradle

[![JitPack version](https://jitpack.io/v/JuulLabs/able.svg)](https://jitpack.io/#JuulLabs/able)

```groovy
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    implementation "com.github.JuulLabs.able:retry:$version"
}
```

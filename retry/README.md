# Retry

`Retry` wraps a `Gatt` to add I/O retry functionality and on-demand connection establishment.

```kotlin
val gatt = device
    .connectGattOrThrow(context, autoConnect = false)
    .withRetry(1L, MINUTES)
```

# Setup

## Gradle

![Maven Central](https://img.shields.io/maven-central/v/com.juul.able/retry.svg?label=version)

```groovy
dependencies {
    implementation "com.juul.able:retry:$version"
}
```

# Retry

`Retry` wraps a `Gatt` to add I/O retry functionality and on-demand connection establishment.

```kotlin
val gatt = device
    .connectGattOrThrow(context, autoConnect = false)
    .withRetry(1L, MINUTES)
```

# Setup

## Gradle

```groovy
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    implementation "com.github.JuulLabs.able:retry:$version"
}
```

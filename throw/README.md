# Throw

Adds extension functions that `throw` exceptions on failures for various BLE operations.

```kotlin
fun connect(context: Context, device: BluetoothDevice) = launch {
    val gatt = device.connectGattOrThrow(context)

    try {
        gatt.discoverServicesOrThrow()

        val characteristic = gatt
            .getService("F000AA80-0451-4000-B000-000000000000".toUuid())!!
            .getCharacteristic("F000AA83-0451-4000-B000-000000000000".toUuid())
        val value = gatt.readCharacteristicOrThrow(characteristic)
        println("value = $value")
    } finally {
        gatt.disconnect(timeout = 30_000L)
    }
}

private suspend fun Gatt.disconnect(timeout: Long) {
    withContext(NonCancellable) {
        withTimeoutOrNull(timeout) {
            disconnect()
        }
    }
}
```

# Setup

## Gradle

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.juul.able/throw/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.juul.able/throw)

```groovy
dependencies {
    implementation "com.juul.able:throw:$version"
}
```

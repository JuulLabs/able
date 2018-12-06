# Throw

Adds extension functions that `throw` exceptions on failures for various BLE operations.

```kotlin
fun connect(context: Context, device: BluetoothDevice) = launch {
    device.connectGattOrThrow(context, autoConnect = false).use { gatt ->
        gatt.discoverServicesOrThrow()

        val characteristic = gatt
            .getService("F000AA80-0451-4000-B000-000000000000".toUuid())!!
            .getCharacteristic("F000AA83-0451-4000-B000-000000000000".toUuid())
        val value = gatt.readCharacteristicOrThrow(characteristic).value
        println("value = $value")

        gatt.disconnect()
    }
}
```

# Setup

## Gradle

[![JitPack version](https://jitpack.io/v/JuulLabs-OSS/able.svg)](https://jitpack.io/#JuulLabs-OSS/able)

```groovy
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    implementation "com.github.juullabs-oss.able:throw:$version"
}
```

# Device

Provides [`BluetoothDevice`] extension functions to simplify Android Bluetooth Low Energy usage.
Allows [`BluetoothDevice`] to be used as the single access point for connectivity and communication
without having to manage underlying [`BluetoothGatt`] objects (_error handling omitted for
simplicity_):

```kotlin
fun connectAndReadEveryMinute(
    context: Context,
    bluetoothDevice: BluetoothDevice,
    characteristic: BluetoothGattCharacteristic
): Job = launch {
    while (isActive) {
        bluetoothDevice.connect(context)
        bluetoothDevice.discoverServices()

        val value = bluetoothDevice.readCharacteristic(characteristic).value

        try {
            bluetoothDevice.disconnect()
        } finally {
            bluetoothDevice.close()
        }

        delay(60_000L)
    }
}
```

Under the hood, [`BluetoothDevice`]s are wrapped and stored in a `ConcurrentMap` so that connection
and characteristic observation `Channel`s can be used across connections (_error handling omitted
for simplicity_):

```kotlin
fun connectionStateExample(context: Context, bluetoothDevice: BluetoothDevice) {
    launch {
        // The same `Channel` will persist across connections, no need to resubscribe on reconnect.
        bluetoothDevice.onConnectionStateChange.consumeEach {
            println("Connection state changed to $it for $bluetoothDevice")
        }
    }

    launch {
        bluetoothDevice.connect(context)
        bluetoothDevice.discoverServices()

        delay(10_000L)

        bluetoothDevice.disconnect()

        delay(5_000L)

        bluetoothDevice.connect()

        delay(10_000L)

        try {
            bluetoothDevice.disconnect()
        } finally {
            bluetoothDevice.close()
        }
    }
}
```

When a [`BluetoothDevice`] is no longer needed, it should be disposed from the underlying
`ConcurrentMap`:

```kotlin
// Will close underlying [`BluetoothGatt`] and close connection state and character change Channels.
CoroutinesGattDevices -= bluetoothDevice
```

# Setup

## Gradle

[![JitPack version](https://jitpack.io/v/JUUL-OSS/able.svg)](https://jitpack.io/#JUUL-OSS/able)

```groovy
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    implementation "com.github.JUUL-OSS.able:device:$version"
}
```


[`BluetoothDevice`]: https://developer.android.com/reference/android/bluetooth/BluetoothDevice
[`BluetoothGatt`]: https://developer.android.com/reference/android/bluetooth/BluetoothGatt

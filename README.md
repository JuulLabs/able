# Able

Provides a [Coroutines] powered interaction with Android's Bluetooth Low Energy (BLE) framework.

## API

When feasible, the API closely matches the Android bluetooth API, replacing methods that traditionally rely on
[`BluetoothGattCallback`] calls with [suspension functions].

The primary entry point is the `BluetoothDevice.connectGatt(context: Context, autoConnect: Boolean): ConnectGattResult`.
This method acts as a replacement for Android's
[`BluetoothDevice.connectGatt(context: Context, autoConnect: Boolean, callback: BluetoothCallback): BluetoothGatt?`]
method (which relies on a [`BluetoothGattCallback`]).

Upon connection success, the return will contain a `Gatt` object that provides suspension function versions of the
[`BluetoothGatt`] that it wraps.

## Example

As an example, the following code snippet makes a connection to a [`BluetoothDevice`], reads a characteristic, then
disconnects:

```kotlin
fun connect(context: Context, device: BluetoothDevice) = launch(UI) {
    val serviceUuid = "F000AA80-0451-4000-B000-000000000000".toUuid()
    val characteristicUuid = "F000AA83-0451-4000-B000-000000000000".toUuid()

    val connectResult = device.connectGatt(context, autoConnect = false)
    val gatt = (connectResult as? ConnectGattSuccess)?.gatt
    if (gatt == null) {
        // connect failed
    }

    if (gatt.discoverServices() != BluetoothGatt.GATT_SUCCESS) {
        // discover services failed
    }

    val characteristic = gatt.getService(serviceUuid).getCharacteristic(characteristicUuid)

    val result = gatt.readCharacteristic(characteristic)
    if (result.status == BluetoothGatt.GATT_SUCCESS) {
        toast("Data: ${result.value}")
    } else {
        // read characteristic failed
    }

    gatt.disconnect()
    gatt.close()
}
```

# Setup

## Gradle

[![JitPack version](https://jitpack.io/v/JuulLabs/able.svg)](https://jitpack.io/#JuulLabs/able)

To use `able` in your Android project, setup your `build.gradle` as follows:

```groovy
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    implementation "com.github.JuulLabs.able:core:$version"
}
```

# License

```
Copyright 2018 JUUL Labs

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```


[`BluetoothGatt`]: https://developer.android.com/reference/android/bluetooth/BluetoothGatt.html
[`BluetoothGattCallback`]: https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback.html
[`BluetoothDevice`]: https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html
[`BluetoothDevice.connectGatt(context: Context, autoConnect: Boolean, callback: BluetoothCallback): BluetoothGatt?`]: https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html#connectGatt(android.content.Context,%20boolean,%20android.bluetooth.BluetoothGattCallback)
[Coroutines]: https://kotlinlang.org/docs/reference/coroutines.html
[suspension functions]: https://kotlinlang.org/docs/reference/coroutines.html#suspending-functions

# Able

Provides a Kotlin [Coroutines] powered interaction with **A**ndroid's **B**luetooth **L**ow
**E**nergy (BLE) framework.

See [Recipes] page for usage examples.

## API

When feasible, the API closely matches the Android Bluetooth Low Energy API, replacing methods that
traditionally rely on [`BluetoothGattCallback`] calls with [suspension functions].

<table>
<tr>
<td align="center">Android <a href="https://developer.android.com/reference/android/bluetooth/BluetoothDevice"><code>BluetoothDevice</code></a></td>
<td align="center">Able <code>Device</code></td>
</tr>
<tr>
<td><pre><code>fun connectGatt(
    context: Context,
    autoConnect: Boolean,
    callback: BluetoothGattCallback
): BluetoothGatt</code></pre></td>
<td><pre><code>suspend fun connectGatt(
    context: Context,
    autoConnect: Boolean = false
): ConnectGattResult</code><sup>1</sup></pre></td>
</tr>
</table>

<sup>1</sup> _Suspends until `STATE_CONNECTED` or non-`GATT_SUCCESS` is received, then returns
`ConnectGattResult`:_

```kotlin
sealed class ConnectGattResult {
    data class Success(val gatt: Gatt) : ConnectGattResult()
    data class Canceled(val cause: CancellationException) : ConnectGattResult()
    data class Failure(val cause: Throwable) : ConnectGattResult()
}
```

<table>
<tr>
<td align="center">Android <a href="https://developer.android.com/reference/android/bluetooth/BluetoothGatt"><code>BluetoothGatt</code></a></td>
<td align="center">Able <code>Gatt</code></td>
</tr>
<tr>
<td><pre><code>fun connect(): Boolean</code></pre></td>
<td><pre><code>suspend fun connect(): Boolean</code><sup>1</sup></pre></td>
</tr>
<tr>
<td><pre><code>fun disconnect(): Boolean</code></pre></td>
<td><pre><code>suspend fun disconnect(): Unit</code><sup>2</sup></pre></td>
</tr>
<tr>
<td><pre><code>fun discoverServices(): Boolean</code></pre></td>
<td><pre><code>suspend fun discoverServices(): GattStatus</code><sup>3</sup></pre></td>
</tr>
<tr>
<td><pre><code>fun getServices(): List<BluetoothGattService></code></pre></td>
<td><pre><code>val services: List<BluetoothGattService></code></pre></td>
</tr>
<tr>
<td><pre><code>fun getService(uuid: UUID): BluetoothGattService</code></pre></td>
<td><pre><code>fun getService(uuid: UUID): BluetoothGattService</code></pre></td>
</tr>
<tr>
<td><pre><code>fun readCharacteristic(
    characteristic: BluetoothGattCharacteristic
): Boolean</code></pre></td>
<td><pre><code>suspend fun readCharacteristic(
    characteristic: BluetoothGattCharacteristic
): OnCharacteristicRead</code><sup>3</sup></pre></td>
</tr>
<tr>
<td><pre><code>fun writeCharacteristic(
    characteristic: BluetoothGattCharacteristic
): Boolean</code></pre></td>
<td><pre><code>suspend fun writeCharacteristic(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    writeType: WriteType
): OnCharacteristicWrite</code><sup>3</sup></pre></td>
</tr>
<tr>
<td><pre><code>fun writeDescriptor(
    descriptor: BluetoothGattDescriptor
): Boolean</code></pre></td>
<td><pre><code>suspend fun writeDescriptor(
    descriptor: BluetoothGattDescriptor,
    value: ByteArray
): OnDescriptorWrite</code><sup>3</sup></pre></td>
</tr>
<tr>
<td><pre><code>fun requestMtu(mtu: Int): Boolean</code></pre></td>
<td><pre><code>suspend fun requestMtu(mtu: Int): OnMtuChanged</code><sup>3</sup></pre></td>
</tr>
<tr>
<td><pre><code>fun setCharacteristicNotification(
    characteristic: BluetoothGattCharacteristic,
    enable: Boolean
): Boolean</code></pre></td>
<td><pre><code>fun setCharacteristicNotification(
    characteristic: BluetoothGattCharacteristic,
    enable: Boolean
): Boolean</code></pre></td>
</tr>
</table>

<sup>1</sup> _Suspends until `STATE_CONNECTED` or non-`GATT_SUCCESS` is received._<br/>
<sup>2</sup> _Suspends until `STATE_DISCONNECTED` or non-`GATT_SUCCESS` is received._<br/>
<sup>3</sup> _Throws [`RemoteException`] if underlying [`BluetoothGatt`] call returns `false`._<br/>
<sup>3</sup> _Throws `GattClosed` if `Gatt` is closed while method is executing._<br/>
<sup>3</sup> _Throws `GattConnectionLost` if `Gatt` is disconnects while method is executing._

### Details

The primary entry point is the
`BluetoothDevice.connectGatt(context: Context, autoConnect: Boolean): ConnectGattResult` extension
function. This extension function acts as a replacement for Android's
[`BluetoothDevice.connectGatt(context: Context, autoConnect: Boolean, callback: BluetoothCallback): BluetoothGatt?`]
method (which relies on a [`BluetoothGattCallback`]).

### Prerequisites

**Able** expects that Android Bluetooth Low Energy is supported
([`BluetoothAdapter.getDefaultAdapter()`] returns non-`null`) and usage prerequisites
(e.g. [bluetooth permissions]) are satisfied prior to use; failing to do so will result in
[`RemoteException`] for most **Able** methods.

## Structured Concurrency

Kotlin Coroutines `0.26.0` introduced [structured concurrency].

When establishing a connection (e.g. via
`BluetoothDevice.connectGatt(context: Context, autoConnect: Boolean): ConnectGattResult` extension
function), if the Coroutine is cancelled then the in-flight connection attempt will be cancelled and
corresponding [`BluetoothGatt`] will be closed:

```kotlin
fun connect(context: Context, device: BluetoothDevice) {
    val deferred = async {
        device.connectGatt(context, autoConnect = false)
    }

    launch {
        delay(1000L) // Assume, for this example, that BLE connection takes more than 1 second.

        // Cancels the `async` Coroutine and automatically closes the underlying `BluetoothGatt`.
        deferred.cancel()
    }

    val result = deferred.await() // `result` will be `ConnectGattResult.Canceled`.
}
```

Note that in the above example, if the BLE connection takes less than 1 second, then the
**established** connection will **not** be cancelled (and `Gatt` will not be closed), and `result`
will be `ConnectGattResult.Success`.

### `Gatt` Coroutine Scope

**Able**'s `Gatt` provides a [`CoroutineScope`], allowing any Coroutine builders to be scoped to the
`Gatt` instance. For example, you can continually read a characteristic and the Coroutine will
automatically cancel when the `Gatt` is closed (_error handling omitted for simplicity_):

```kotlin
fun continuallyReadCharacteristic(gatt: Gatt, serviceUuid: UUID, characteristicUuid: UUID) {
    val characteristic = gatt.getService(serviceUuid)!!.getCharacteristic(characteristicUuid)!!

    // This Coroutine will automatically cancel when `gatt.close()` is called.
    gatt.launch {
        while (isActive) {
            println("value = ${gatt.readCharacteristic(characteristic).value}")
        }
    }
}
```

# Setup

## Gradle

To use **Able** in your Android project, setup your `build.gradle` as follows:

```groovy
dependencies {
    implementation "com.juul.able:core:0.7.0"
}
```

## Packages

**Able** provides a number of packages to help extend it's functionality:

| Package           | Functionality                                                                                                   |
|-------------------|-----------------------------------------------------------------------------------------------------------------|
| [`processor`]     | A `Processor` adds the ability to process (and optionally modify) GATT data<br/>pre-write or post-read.         |
| [`retry`]         | `Retry` wraps a `Gatt` to add I/O retry functionality and on-demand connection<br/>establishment.               |
| [`throw`]         | Adds extension functions that `throw` exceptions on failures for various BLE<br/>operations.                    |
| [`timber-logger`] | Routes **Able** logging through [Timber](https://github.com/JakeWharton/timber).                                |
| [`device`]        | Provides `BluetoothDevice` extension functions as a single access point for<br/>connectivity and communication. |

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


[Coroutines]: https://kotlinlang.org/docs/reference/coroutines.html
[Recipes]: documentation/RECIPES.md
[`BluetoothGattCallback`]: https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback.html
[suspension functions]: https://kotlinlang.org/docs/reference/coroutines.html#suspending-functions
[`RemoteException`]: https://developer.android.com/reference/android/os/RemoteException
[`BluetoothGatt`]: https://developer.android.com/reference/android/bluetooth/BluetoothGatt.html
[`BluetoothDevice.connectGatt(context: Context, autoConnect: Boolean, callback: BluetoothCallback): BluetoothGatt?`]: https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html#connectGatt(android.content.Context,%20boolean,%20android.bluetooth.BluetoothGattCallback)
[`BluetoothAdapter.getDefaultAdapter()`]: https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#getDefaultAdapter()
[bluetooth permissions]: https://developer.android.com/guide/topics/connectivity/bluetooth#Permissions
[structured concurrency]: https://medium.com/@elizarov/structured-concurrency-722d765aa952
[`CoroutineScope`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/
[`processor`]: processor
[`retry`]: retry
[`throw`]: throw
[`timber-logger`]: timber-logger
[`device`]: device

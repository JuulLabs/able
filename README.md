[![codecov](https://codecov.io/gh/JuulLabs/able/branch/master/graph/badge.svg)](https://codecov.io/gh/JuulLabs/able)

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
    context: Context
): ConnectGattResult</code><sup>1</sup></pre></td>
</tr>
</table>

<sup>1</sup> _Suspends until `STATE_CONNECTED` or non-`GATT_SUCCESS` is received, or `connectGatt`
returns `null`, then returns `ConnectGattResult`:_

```kotlin
sealed class ConnectGattResult {

    data class Success(val gatt: Gatt) : ConnectGattResult()

    sealed class Failure : ConnectGattResult() {

        /** Connection could not be established (e.g. device is out of range). */
        data class Connection(val cause: Exception) : Failure()

        /** Android's `BluetoothDevice.connectGatt` returned `null` (e.g. BLE off or unsupported). */
        data class Rejected(val cause: Exception) : Failure()
    }
}
```

<table>
<tr>
<td align="center">Android <a href="https://developer.android.com/reference/android/bluetooth/BluetoothGatt"><code>BluetoothGatt</code></a></td>
<td align="center">Able <code>Gatt</code></td>
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
<td><pre><code>fun readRemoteRssi(): Boolean</code></pre></td>
<td><pre><code>suspend fun readRemoteRssi(): OnReadRemoteRssi</code><sup>3</sup></pre></td>
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

<sup>2</sup> _Suspends until `STATE_DISCONNECTED` or non-`GATT_SUCCESS` is received, then calls `close()` on underlying [`BluetoothGatt`]._<br/>
<sup>3</sup> _Throws [`RemoteException`] if underlying [`BluetoothGatt`] call returns `false`._<br/>
<sup>3</sup> _Throws `GattResponseFailure` if an error occurs while waiting for response (e.g. connection is lost)._<br/>

### Details

The primary entry point is the
`BluetoothDevice.connectGatt(context: Context): ConnectGattResult` extension function. This
extension function acts as a replacement for Android's
[`BluetoothDevice.connectGatt(context: Context, autoConnect: Boolean, callback: BluetoothCallback): BluetoothGatt?`]
method (which relies on a [`BluetoothGattCallback`]). The `autoConnect` parameter is not
configurable (and is always `false`).

### Prerequisites

**Able** expects that Android Bluetooth Low Energy is supported
([`BluetoothAdapter.getDefaultAdapter()`] returns non-`null`) and usage prerequisites
(e.g. [bluetooth permissions]) are satisfied prior to use; failing to do so will result in
[`RemoteException`] for most **Able** methods.

## [Structured Concurrency]

During the `connectGatt` and `disconnect` process, **Able** will ensure that connections are cleaned
up (i.e. `close` will always be called on the underlying [`BluetoothGatt`]) in the event of failure
or Coroutine cancellation:

```kotlin
fun connect(context: Context, device: BluetoothDevice) {
    val job = launch {
        device.connectGatt(context)
    }

    launch {
        delay(1_000L) // Assume, for this example, that BLE connection takes more than 1 second.

        // Cancels the `launch` Coroutine and automatically closes the underlying `BluetoothGatt`.
        job.cancel()
    }
}
```

Note that in the above example, if the BLE connection takes less than 1 second, then the
**established** connection will **not** be cancelled and `result` will be
`ConnectGattResult.Success`.

Once a connection is established (`connectGatt` returns `ConnectGattResult.Success`) then it will
remain connected until `disconnect()` is called. It is the responsibility of the caller to clean up
the connection when no longer needed (via `disconnect`).

# Setup

## Gradle

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.juul.able/core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.juul.able/core)

To use **Able** in your Android project, setup your `build.gradle` as follows:

```groovy
repositories {
    jcenter() // or mavenCentral()
}

dependencies {
    implementation "com.juul.able:core:$version"
}
```

## Packages

**Able** provides a number of packages to help extend it's functionality:

| Package                          | Functionality                                                                                           |
|----------------------------------|---------------------------------------------------------------------------------------------------------|
| [`keep-alive`](keep-alive)       | Provides a keep-alive GATT (which automatically reconnects when connection is lost).                    |
| [`processor`](processor)         | A `Processor` adds the ability to process (and optionally modify) GATT data<br/>pre-write or post-read. |
| [`throw`](throw)                 | Adds extension functions that `throw` exceptions on failures for various BLE<br/>operations.            |
| [`timber-logger`](timber-logger) | Routes **Able** logging through [Timber](https://github.com/JakeWharton/timber).                        |

# License

```
Copyright 2020 JUUL Labs, Inc.

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
[Recipes]: documentation/recipes.md
[`BluetoothGattCallback`]: https://developer.android.com/reference/android/bluetooth/BluetoothGattCallback.html
[suspension functions]: https://kotlinlang.org/docs/reference/coroutines.html#suspending-functions
[`RemoteException`]: https://developer.android.com/reference/android/os/RemoteException
[`BluetoothGatt`]: https://developer.android.com/reference/android/bluetooth/BluetoothGatt.html
[`BluetoothDevice.connectGatt(context: Context, autoConnect: Boolean, callback: BluetoothCallback): BluetoothGatt?`]: https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html#connectGatt(android.content.Context,%20boolean,%20android.bluetooth.BluetoothGattCallback)
[`BluetoothAdapter.getDefaultAdapter()`]: https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#getDefaultAdapter()
[bluetooth permissions]: https://developer.android.com/guide/topics/connectivity/bluetooth#Permissions
[Structured Concurrency]: https://medium.com/@elizarov/structured-concurrency-722d765aa952
[`CoroutineScope`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/

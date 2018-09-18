# Able

Provides a Kotlin [Coroutines] powered interaction with **A**ndroid's **B**luetooth **L**ow
**E**nergy (BLE) framework.

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
    data class ConnectGattSuccess(val gatt: Gatt) : ConnectGattResult()
    data class ConnectGattCanceled(val cause: CancellationException) : ConnectGattResult()
    data class ConnectGattFailure(val cause: Throwable) : ConnectGattResult()
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
<td><pre><code>suspend fun discoverServices(): GattStatus?</code><sup>3</sup></pre></td>
</tr>
<tr>
<td><pre><code>fun getServices(): List<BluetoothGattService></code></pre></td>
<td><pre><code>val services: List<BluetoothGattService></code></pre></td>
</tr>
<tr>
<td><pre><code>fun getService(uuid: UUID): BluetoothGattService?</code></pre></td>
<td><pre><code>fun getService(uuid: UUID): BluetoothGattService?</code></pre></td>
</tr>
<tr>
<td><pre><code>fun readCharacteristic(
    characteristic: BluetoothGattCharacteristic
): Boolean</code></pre></td>
<td><pre><code>suspend fun readCharacteristic(
    characteristic: BluetoothGattCharacteristic
): OnCharacteristicRead?</code><sup>3</sup></pre></td>
</tr>
<tr>
<td><pre><code>fun writeCharacteristic(
    characteristic: BluetoothGattCharacteristic
): Boolean</code></pre></td>
<td><pre><code>suspend fun writeCharacteristic(
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray,
    writeType: WriteType
): OnCharacteristicWrite?</code><sup>3</sup></pre></td>
</tr>
<tr>
<td><pre><code>fun writeDescriptor(
    descriptor: BluetoothGattDescriptor
): Boolean</code></pre></td>
<td><pre><code>suspend fun writeDescriptor(
    descriptor: BluetoothGattDescriptor,
    value: ByteArray
): OnDescriptorWrite?</code><sup>3</sup></pre></td>
</tr>
<tr>
<td><pre><code>fun requestMtu(mtu: Int): Boolean</code></pre></td>
<td><pre><code>suspend fun requestMtu(mtu: Int): OnMtuChanged?</code><sup>3</sup></pre></td>
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
<sup>3</sup> _Returns `null` if underlying `BluetoothGatt` call returns `false`._

### Details

The primary entry point is the
`BluetoothDevice.connectGatt(context: Context, autoConnect: Boolean): ConnectGattResult` extension
function. This extension function acts as a replacement for Android's
[`BluetoothDevice.connectGatt(context: Context, autoConnect: Boolean, callback: BluetoothCallback): BluetoothGatt?`]
method (which relies on a [`BluetoothGattCallback`]).

## Example

The following code snippet makes a connection to a [`BluetoothDevice`], reads a characteristic,
then disconnects (_error handling omitted for simplicity_):

```kotlin
val serviceUuid = "F000AA80-0451-4000-B000-000000000000".toUuid()
val characteristicUuid = "F000AA83-0451-4000-B000-000000000000".toUuid()

fun fetchCharacteristic(context: Context, device: BluetoothDevice) = launch {
    device.connectGatt(context, autoConnect = false).let { (it as Success).gatt }.use { gatt ->
        gatt.discoverServices()
        val characteristic = gatt.getService(serviceUuid)!!.getCharacteristic(characteristicUuid)
        println("value = ${gatt.readCharacteristic(characteristic)?.value}")
        gatt.disconnect()
    }
}
```

See the [Recipes] page for more usage examples.

# Setup

## Gradle

[![JitPack version](https://jitpack.io/v/JuulLabs/able.svg)](https://jitpack.io/#JuulLabs/able)

To use **Able** in your Android project, setup your `build.gradle` as follows:

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
[Recipes]: documentation/RECIPES.md

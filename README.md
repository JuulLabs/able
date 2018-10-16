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
<sup>3</sup> _Throws [`RemoteException`] if underlying [`BluetoothGatt`] call returns `false`._

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
class ExampleActivity : AppCompatActivity(), CoroutineScope {

    protected lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()

        val bluetoothDevice: BluetoothDevice = TODO("Retrieve a `BluetoothDevice` from a scan.")

        findViewById<Button>(R.id.connect_button).setOnClickListener {
            launch {
                // If Activity is destroyed during connection attempt, then `result` will contain
                // `ConnectGattResult.Canceled`.
                val result = bluetoothDevice.connectGatt(this@ExampleActivity, autoConnect = false)

                // ...
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
```

In the above example, the connection process is tied to the Activity lifecycle. If the Activity is
destroyed (e.g. due to device rotation or navigating away from the Activity) then the connection
attempt will be cancelled. If it is desirable that a connection attempt proceed beyond the Activity
lifecycle then the [`launch`] can be executed in the global scope via `GlobalScope.launch`, in which
case the [`Job`] that [`launch`] returns can be used to manually cancel the connection process (when
desired).

Alternatively, if (for example) an app has an Activity specifically designed to handle the
connection process, then Android Architecture Component's [`ViewModel`] can be scoped (via
`CoroutineScope` interface) allowing connection attempts to be tied to the `ViewModel`'s lifecycle:

```kotlin
class ExampleViewModel(application: Application) : AndroidViewModel(application), CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    fun connect(bluetoothDevice: BluetoothDevice) {
        launch {
            // If ViewModel is destroyed during connection attempt, then `result` will contain
            // `ConnectGattResult.Canceled`.
            val result = bluetoothDevice.connectGatt(getApplication(), autoConnect = false)

            // ...
        }
    }

    override fun onCleared() {
        job.cancel()
    }
}
```

Similar to the connection process, after a connection has been established, if a Coroutine is
cancelled then any `Gatt` operation executing within the Coroutine will be cancelled.

However, unlike the `connectGatt` cancellation handling, an established `Gatt` connection will
**not** automatically disconnect or close when the Coroutine executing a `Gatt` operation is
canceled. Special care must be taken to ensure that Bluetooth Low Energy connections are properly
closed when no longer needed, for example:

```kotlin
val gatt: Gatt = TODO("Acquire Gatt via `BluetoothDevice.connectGatt` extension function.")

launch {
    try {
        gatt.discoverServices()
        // todo: Assign desired characteristic to `characteristic` variable.
        val value = gatt.readCharacteristic(characteristic).value
        gatt.disconnect()
    } finally {
        gatt.close()
    }
}
```

The `Gatt` interface adheres to [`Closeable`] which can simplify the above example by using [`use`]:

```kotlin
val gatt: Gatt = TODO("Acquire Gatt via `BluetoothDevice.connectGatt` extension function.")

gatt.use { // Will close `gatt` if any failures or cancellation occurs.
    gatt.discoverServices()
    // todo: Assign desired characteristic to `characteristic` variable.
    val value = gatt.readCharacteristic(characteristic).value
    gatt.disconnect()
}
```

It may be desirable to manage Bluetooth Low Energy connections entirely manually. In which case,
Coroutine [`GlobalScope`] can be used for both the connection process and operations performed while
connected. In which case, the returned `Gatt` object (after successful connection) can be stored and
later used to disconnect **and** close the underlying `BluetoothGatt`.

## Example

The following code snippet makes a connection to a [`BluetoothDevice`], reads a characteristic,
then disconnects (_error handling omitted for simplicity_):

```kotlin
val serviceUuid = "F000AA80-0451-4000-B000-000000000000".toUuid()
val characteristicUuid = "F000AA83-0451-4000-B000-000000000000".toUuid()

fun fetchCharacteristic(context: Context, device: BluetoothDevice) = launch {
    device.connectGatt(context, autoConnect = false).let { (it as Success).gatt }.use { gatt ->
        gatt.discoverServices()
        val characteristic = gatt.getService(serviceUuid)!!.getCharacteristic(characteristicUuid)!!
        println("value = ${gatt.readCharacteristic(characteristic).value}")
        gatt.disconnect()
    }
}
```

See the [Recipes] page for more usage examples.

# Setup

## Gradle

[![JitPack version](https://jitpack.io/v/JUUL-OSS/able.svg)](https://jitpack.io/#JUUL-OSS/able)

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
[`RemoteException`]: https://developer.android.com/reference/android/os/RemoteException
[Coroutines]: https://kotlinlang.org/docs/reference/coroutines.html
[`Closeable`]: https://docs.oracle.com/javase/7/docs/api/java/io/Closeable.html
[`GlobalScope`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-global-scope/
[suspension functions]: https://kotlinlang.org/docs/reference/coroutines.html#suspending-functions
[structured concurrency]: https://medium.com/@elizarov/structured-concurrency-722d765aa952
[bluetooth permissions]: https://developer.android.com/guide/topics/connectivity/bluetooth#Permissions
[`Job`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/index.html
[`launch`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/launch.html
[`ViewModel`]: https://developer.android.com/topic/libraries/architecture/viewmodel
[`use`]: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.io/use.html
[`BluetoothAdapter.getDefaultAdapter()`]: https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#getDefaultAdapter()
[Recipes]: documentation/RECIPES.md

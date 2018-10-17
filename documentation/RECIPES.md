# Recipes

## Basic Usage Example

The following code snippet makes a connection to a [`BluetoothDevice`], reads a characteristic, then
disconnects:

```kotlin
val serviceUuid = "F000AA80-0451-4000-B000-000000000000".toUuid()
val characteristicUuid = "F000AA83-0451-4000-B000-000000000000".toUuid()

fun connect(context: Context, device: BluetoothDevice) = launch {
    val gatt = device.connectGatt(context, autoConnect = false).let { result ->
        when (result) {
            is Success -> result.gatt
            is Canceled -> throw IllegalStateException("Connection canceled.", result.cause)
            is Failure -> throw IllegalStateException("Connection failed.", result.cause)
        }
    }

    if (gatt.discoverServices() != BluetoothGatt.GATT_SUCCESS) {
        // discover services failed
    }

    val characteristic = gatt.getService(serviceUuid).getCharacteristic(characteristicUuid)

    val result = gatt.readCharacteristic(characteristic)
    if (result.status == BluetoothGatt.GATT_SUCCESS) {
        println("result.value = ${result.value}")
    } else {
        // read characteristic failed
    }

    gatt.disconnect()
    gatt.close()
}
```

## Coroutine Scopes

### Android `Activity`

In the following example, the connection process is tied to the `Activity` lifecycle. If the
`Activity` is destroyed (e.g. due to device rotation or navigating away from the `Activity`) then
the connection attempt will be canceled (i.e. `BluetoothDevice.connectGatt` extension function will
return `ConnectGattResult.Canceled`). If it is desirable that a connection attempt proceed beyond
the `Activity` lifecycle, then the [`launch`] can be executed using [`GlobalScope`], in which case
the [`Job`] that [`launch`] returns can be used to manually cancel the connection process (when
desired).

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

### Android `ViewModel`

Alternative to the Android `Activity` scope described above, if (for example) an app has an
`Activity` specifically designed to handle the connection process, then Android Architecture
Component's [`ViewModel`] can be scoped (via `CoroutineScope` interface) allowing connection
attempts to be tied to the `ViewModel`'s lifecycle:

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

## `Gatt` Operation Cancellation

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

launch {
    gatt.use { // Will close `gatt` if any failures or cancellation occurs.
        gatt.discoverServices()
        // todo: Assign desired characteristic to `characteristic` variable.
        val value = gatt.readCharacteristic(characteristic).value
        gatt.disconnect()
    }
}
```

It may be desirable to manage Bluetooth Low Energy connections entirely manually. In which case,
Coroutine [`GlobalScope`] can be used for the connection process. In which case, the returned `Gatt`
object (after successful connection) can be stored and later used to disconnect **and** close the
underlying `BluetoothGatt`.


[`BluetoothDevice`]: https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html
[`launch`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/launch.html
[`GlobalScope`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-global-scope/
[`Job`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/index.html
[`ViewModel`]: https://developer.android.com/topic/libraries/architecture/viewmodel
[`Closeable`]: https://docs.oracle.com/javase/7/docs/api/java/io/Closeable.html
[`use`]: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.io/use.html

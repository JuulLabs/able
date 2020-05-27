# Recipes

## Basic Usage Example

The following code snippet makes a connection to a [`BluetoothDevice`], reads a characteristic, then
disconnects:

```kotlin
val serviceUuid = "F000AA80-0451-4000-B000-000000000000".toUuid()
val characteristicUuid = "F000AA83-0451-4000-B000-000000000000".toUuid()

fun connect(context: Context, device: BluetoothDevice) = launch {
    val gatt = when (val result = device.connectGatt(context)) {
        is Success -> result.gatt
        is Failure -> throw result.cause
    }

    try {
        val characteristic = gatt.getService(serviceUuid).getCharacteristic(characteristicUuid)

        val result = gatt.readCharacteristic(characteristic)
        if (result.status == BluetoothGatt.GATT_SUCCESS) {
            println("result.value = ${result.value}")
        } else {
            // read characteristic failed
        }
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

## Coroutine Scopes

### Android `Activity`

In the following example, the connection process is tied to the `Activity` lifecycle. If the
`Activity` is destroyed (e.g. due to device rotation or navigating away from the `Activity`) then
the connection attempt will be canceled. If it is desirable that a connection attempt proceed beyond
the `Activity` lifecycle, then the [`launch`] can be executed using [`GlobalScope`], in which case
the [`Job`] that [`launch`] returns can be used to manually cancel the connection process (when
desired).

```kotlin
class ExampleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothDevice: BluetoothDevice = TODO("Retrieve a `BluetoothDevice` from a scan.")

        findViewById<Button>(R.id.connect_button).setOnClickListener {
            lifecycleScope.launch {
                val result = bluetoothDevice.connectGatt(this@ExampleActivity)

                // ...
            }
        }
    }
}
```

### Android `ViewModel`

Alternative to the Android `Activity` scope described above, if (for example) an app has an
`Activity` specifically designed to handle the connection process, then Android Architecture
Component's [`ViewModel`]'s scope can be used, allowing connection attempts to be tied to the
`ViewModel`'s lifecycle:

```kotlin
class ExampleViewModel(application: Application) : AndroidViewModel(application) {

    fun connect(bluetoothDevice: BluetoothDevice) {
        viewModelScope.launch {
            val result = bluetoothDevice.connectGatt(getApplication())

            // ...
        }
    }
}
```

## `Gatt` Operation Cancellation

Similar to the connection process, after a connection has been established, if a Coroutine is
cancelled then any `Gatt` operation executing within the Coroutine will be cancelled.

However, unlike the `connectGatt` cancellation handling, an established `Gatt` connection will
**not** automatically disconnect when the Coroutine executing a `Gatt` operation is cancelled. Be
sure and `disconnect` your `Gatt` connection when no longer needed:

```kotlin
launch {
    val gatt: Gatt = TODO("Acquire Gatt via `BluetoothDevice.connectGatt` extension function.")
    // todo: Assign desired characteristic to `characteristic` variable.
    val value = try {
        gatt.readCharacteristic(characteristic).value
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

It may be desirable to manage Bluetooth Low Energy connections manually. In which case, your own
[`CoroutineScope`] or [`GlobalScope`] can be used for the connection process. The returned `Gatt`
object (after successful connection) can be stored and later used to disconnect.


[`BluetoothDevice`]: https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html
[`launch`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/launch.html
[`CoroutineScope`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/
[`GlobalScope`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-global-scope/
[`Job`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-job/index.html
[`ViewModel`]: https://developer.android.com/topic/libraries/architecture/viewmodel

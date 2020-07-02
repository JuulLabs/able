# Keep-alive

Provides keep-alive (reconnects if connection drops) GATT communication.

## Structured Concurrency

A keep-alive GATT is created by calling the `keepAliveGatt` extension function on
[`CoroutineScope`], which has the following signature:

```kotlin
fun CoroutineScope.keepAliveGatt(
    androidContext: Context,
    bluetoothDevice: BluetoothDevice,
    disconnectTimeoutMillis: Long,
    eventHandler: EventHandler? = null
): KeepAliveGatt
```

| Parameter                 | Description                                                                                                               |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `androidContext`          | The Android `Context` for establishing Bluetooth Low-Energy connections.                                                  |
| `bluetoothDevice`         | `BluetoothDevice` to maintain a connection with.                                                                          |
| `disconnectTimeoutMillis` | Duration (in milliseconds) to wait for connection to gracefully spin down (after `disconnect`) before forcefully closing. |
| `eventHandler`            | Actions to perform on various events within the KeepAliveGatt's lifecycle (e.g. Connected, Disconnected, Rejected).       |

For example, to create a `KeepAliveGatt` as a child of Android's `viewModelScope`:

```kotlin
class ExampleViewModel(application: Application) : AndroidViewModel(application) {

    private const val MAC_ADDRESS = ...

    private val gatt = viewModelScope.keepAliveGatt(
        application,
        bluetoothAdapter.getRemoteDevice(MAC_ADDRESS),
        disconnectTimeoutMillis = 5_000L // 5 seconds
    )

    fun connect() {
        gatt.connect()
    }
}
```

When the parent [`CoroutineScope`] (`viewModelScope` in the above example) cancels, the
`KeepAliveGatt` also cancels (and disconnects).

When cancelled, a `KeepAliveGatt` will end in a `Cancelled` state. Once a `KeepAliveGatt` is
`Cancelled` it **cannot** be reconnected (calls to `connect` will throw `IllegalStateException`); a
new `KeepAliveGatt` must be created.

## Connection Handling

A `KeepAliveGatt` will start in a `Disconnected` state. When `connect` is called, `KeepAliveGatt`
will attempt to establish a connection (`Connecting`). If the connection is rejected (e.g. BLE is
turned off), then `KeepAliveGatt` will settle at `Disconnected` state. The `connect` function can be
called again to re-attempt to establish a connection:

![Connection rejected](artwork/connect-reject.png)

If a connection cannot be established (e.g. BLE device out-of-range) then `KeepAliveGatt` will retry
indefinitely:

![Connection failure](artwork/connect-failure.png)

Once `Connected`, if the connection drops, then `KeepAliveGatt` will automatically reconnect:

![Reconnect on connection drop](artwork/connection-drop.png)

_To disconnect an established connection or cancel an in-flight connection attempt, `disconnect` can
be called (it will suspend until underlying [`BluetoothGatt`] has disconnected)._

## Status

The status of a `KeepAliveGatt` can be monitored via either `Event`s or `State`s. The major
distinction between the two is:

> **`State`**: `State`s are propagated over conflated data streams. If states are changing quickly,
> then some `State`s may be missed (skipped over). For this reason, they're useful for informing a
> user of the current state of the connection; as missing a state is acceptable since subsequent
> states will overwrite the currently reflected state anyways. `State`s should **not** be used if a
> specific condition (e.g. `Connected`) needs to trigger an action (use `Event` instead).

> **`Event`**: `Event`s allow a developer to integrate actions into the connection process. When an
> `Event` is triggered, the connection process is paused (suspended) until processing of the `Event`
> is complete.

`State`s and `Event`s occur in the following order:

![State and event flow](artwork/state-and-event-flow.png)

### Events

`Event`s are configured via the `eventHandler` argument of the `keepAliveGatt` extension function,
for example:

```kotlin
val gatt = viewModelScope.keepAliveGatt(...) { event ->
    event.onConnected {
        // Actions to perform on initial connect *and* subsequent reconnects:
        discoverServicesOrThrow()
    }
    event.onDisconnected {
        // todo: retry strategy (e.g. exponentially increasing delay)
    }
}
```

Any uncaught Exceptions in the event handler are propagated up the Coroutine scope that
`keepAliveGatt` extension function was called on (`viewModelScope` in the example above), and cause
the `KeepAliveGatt` to disconnect and **not** reconnect (`connect` can be called to have
`KeepAliveGatt` attempt to reach a Connected state again).

### State

Connection state can be monitored via the `state` [`Flow`] property:

```kotlin
val gatt = scope.keepAliveGatt(...)
gatt.state.collect { println("State: $it") }
```

## I/O

If a Gatt operation (e.g. `discoverServices`, `writeCharacteristic`, `readCharacteristic`, etc) is
unable to be performed due to a GATT connection being unavailable (i.e. current `State` is **not**
`Connected`), then it will immediately throw `NotReady`.

It is the responsibility of the caller to handle retrying, for example:

```kotlin
class GattCancelled : Exception()

suspend fun KeepAliveGatt.readCharacteristicWithRetry(
    characteristic: BluetoothGattCharacteristic,
    retryCount: Int = Int.MAX_VALUE
): OnCharacteristicRead {
    repeat(retryCount) {
        suspendUntilConnected()
        try {
            return readCharacteristicOrThrow(characteristic)
        } catch (exception: Exception) {
            // todo: retry strategy (e.g. exponentially increasing delay)
        }
    }
    error("Failed to read characteristic $characteristic")
}

private suspend fun KeepAliveGatt.suspendUntilConnected() {
    state
        .onEach { if (it is Cancelled) throw GattCancelled() }
        .first { it == Connected }
}
```

### Characteristic Changes

When a `KeepAliveGatt` is created, it immediately provides a [`Flow`] for incoming characteristic
changes (`onCharacteristicChange` property). The [`Flow`] is a hot stream, so characteristic change
events emitted before subscribers have subscribed are dropped. To prevent characteristic change
events from being lost, be sure to setup subscribers **before** calling `KeepAliveGatt.connect`, for
example:

```kotlin
val gatt = scope.keepAliveGatt(...)

fun connect() {
    // `CoroutineStart.UNDISPATCHED` executes within `launch` up to the `collect` (then suspends),
    // before allowing continued execution of `gatt.connect()` (below).
    launch(start = CoroutineStart.UNDISPATCHED) {
        gatt.onCharacteristicChange.collect {
            println("Characteristic changed: $it")
        }
    }

    gatt.connect()
}
```

If the underlying [`BluetoothGatt`] connection is dropped, the characteristic change event stream
remains open (and all subscriptions will continue to `collect`). When a new [`BluetoothGatt`]
connection is established, all it's characteristic change events are automatically routed to the
existing subscribers of the `KeepAliveGatt`.

## Error Handling

When connection failures occur, the corresponding `Exception`s are propagated to `KeepAliveGatt`'s
parent [`CoroutineScope`] and can be inspected via [`CoroutineExceptionHandler`]:

```kotlin
val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    println(throwable)
}
val scope = CoroutineScope(Job() + exceptionHandler)
val gatt = scope.keepAliveGatt(...)
```

# Setup

## Gradle

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.juul.able/keep-alive/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.juul.able/keep-alive)

```groovy
repositories {
    jcenter() // or mavenCentral()
}

dependencies {
    implementation "com.juul.able:keep-alive:$version"
}
```


[`BluetoothDevice.connectGatt`]: https://developer.android.com/reference/android/bluetooth/BluetoothDevice#connectGatt(android.content.Context,%20boolean,%20android.bluetooth.BluetoothGattCallback)
[`BluetoothGatt`]: https://developer.android.com/reference/android/bluetooth/BluetoothGatt
[`CoroutineScope`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/
[`CoroutineContext`]: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.coroutines/-coroutine-context/
[`Flow`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/
[`CoroutineExceptionHandler`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-exception-handler/

# Keep-alive

A keep-alive GATT may be created via the `KeepAliveGatt` constructor, which has the following
signature:

```kotlin
KeepAliveGatt(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    androidContext: Context,
    bluetoothDevice: BluetoothDevice,
    disconnectTimeoutMillis: Long,
    onConnectAction: ConnectAction? = null
)
```

| Parameter                 | Description                                                                                                                         |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `parentCoroutineContext`  | The [`CoroutineContext`] that `KeepAliveGatt` shall operate in (see [Structured Concurrency](#structured-concurrency) for details). |
| `androidContext`          | The Android `Context` for establishing Bluetooth Low-Energy connections.                                                            |
| `bluetoothDevice`         | `BluetoothDevice` to maintain a connection with.                                                                                    |
| `disconnectTimeoutMillis` | Duration (in milliseconds) to wait for connection to gracefully spin down (after `disconnect`) before forcefully cancelling.        |
| `onConnectAction`         | Actions to perform upon connection. `Connected` state is propagated _after_ `onConnectAction` completes.                            |

## Connection Handling

A `KeepAliveGatt` will start in a `Disconnected` state. When `connect` is called, `KeepAliveGatt`
will attempt to establish a connection (`Connecting`). If a connection cannot be established then
`KeepAliveGatt` will retry indefinitely (unless either the underlying
[`BluetoothDevice.connectGatt`] returns `null` or `disconnect` is called on the `KeepAliveGatt`; see
[Error Handling](#error-handling) for details regarding failure states).

To disconnect an established connection or cancel an in-flight connection attempt, `disconnect` can
be called (it will suspend until underlying [`BluetoothGatt`] has disconnected and closed).

### Connection State

`KeepAliveGatt` can be in one of the following `State`s:

- `Disconnected`
- `Connecting`
- `Connected`
- `Disconnecting`
- `Closed`

The state can be monitored via the `state` [`Flow`] property:

```kotlin
val gatt = KeepAliveGatt(...)
gatt.state.collect { println("State: $it") }
```

## I/O

If a Gatt operation (e.g. `discoverServices`, `writeCharacteristic`, `readCharacteristic`, etc) is
unable to be performed due to a GATT connection being unavailable (i.e. current `State` is **not**
`Connected`), then it will immediately throw `NotReady`.

It is the responsibility of the caller to handle retrying, for example:

```kotlin
class GattClosed : Exception()

suspend fun KeepAliveGatt.readCharacteristicWithRetry(
    characteristic: BluetoothGattCharacteristic,
    retryCount: Int = Integer.MAX_VALUE
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
        .onEach { if (it is Closed) throw GattClosed() }
        .first { it == Connected }
}
```

### Characteristic Changes

When a `KeepAliveGatt` is created, it immediately provides a [`Flow`] for incoming characteristic
changes (`onCharacteristicChange` property). The [`Flow`] is a hot stream, so characteristic change
events emitted before subscribers have subscribed are dropped. To prevent characteristic change
events from being dropped, be sure to setup subscribers **before** calling `KeepAliveGatt.connect`,
for example:

```kotlin
val gatt = KeepAliveGatt(...)

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
existing `KeepAliveGatt` subscribers.

## Structured Concurrency

The `CoroutineScope.keepAliveGatt` extension function may be used to create a `KeepAliveGatt` that
is a child of the current [`CoroutineScope`], for example:

```kotlin
class ExampleViewModel(application: Application) : AndroidViewModel(application) {

    private const val MAC_ADDRESS = ...

    private val gatt = viewModelScope.keepAliveGatt(
        application,
        bluetoothAdapter.getRemoteDevice(MAC_ADDRESS),
        disconnectTimeoutMillis = 5_000L // 5 seconds
    ) {
        // Actions to perform on initial connect *and* subsequent reconnects:
        discoverServicesOrThrow()
    }

    fun connect() {
        gatt.connect()
    }
}
```

When the parent [`CoroutineScope`] (`viewModelScope` in the above example) cancels, the
`KeepAliveGatt` automatically disconnects. You may _optionally_ manually `disconnect`.

## Error Handling

When connection failures occur, the corresponding `Exception`s are propagated to `KeepAliveGatt`'s
parent [`CoroutineContext`] and can be inspected via [`CoroutineExceptionHandler`]:

```kotlin
val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    println(throwable)
}
val scope = CoroutineScope(Job() + exceptionHandler)
val gatt = scope.keepAliveGatt(...)
```

When a failure occurs during the connection sequence, `KeepAliveGatt` will disconnect/close the
in-flight connection and reconnect. If a connection attempt results in `GattConnectResult.Rejected`,
then the failure is considered unrecoverable and `KeepAliveGatt` will disconnect/close the in-flight
connection and finish in a `Closed` `State`. Once a `KeepAliveGatt` is `Closed` it cannot be
reconnected (calls to `connect` will throw `IllegalStateException`).

# Setup

## Gradle

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.juul.able/keep-alive/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.juul.able/keep-alive)

```groovy
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

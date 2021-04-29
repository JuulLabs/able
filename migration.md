This document provides guidance for the migration from Able to the [Kable] library. Although the [Kable] library offers
multiplatform support, this guide will focus on the Android support that [Kable] provides.

## Scanning

Able does not offer any scanning capabilities, it was expected that Android provided BLE scanning or 3rd party libraries
were used to acquire a [`BluetoothDevice`].

[Kable] on the other hand does provide built-in BLE scanning support via the [Scanner]. Using the [Kable] [Scanner] is
optional, whereas Android provided BLE scanning or 3rd party libraries may continue to be used with [Kable]. Once you've
acquired a `BluetoothDevice`, the `CoroutineScope.peripheral` extension function (which takes a [`BluetoothDevice`] as
an argument) may be used.

## Connecting

To establish a connection in Able, the `BluetoothDevice.connect(Context)` extension function was used. This function
would suspend until either failure or successful connection, returning a `ConnectGattResult` providing the outcome of
the connection attempt.

To cancel a connection attempt, the encompassing `CoroutineScope` would need to be cancelled, for example:

```kotlin
val job = launch {
    bluetoothDevice.connect(context)
}
job.cancel() // Cancels the connection attempt.
```

In Able, a [`BluetoothDevice`] was used to establish a connection _then_ provide a `Gatt` in a connected state. A
connect would be executed via a `BluetoothDevice` and disconnect via a `Gatt`. The `Gatt` also provided functions for
performing I/O operations.

If a `Gatt` lost connection, then you'd need to go back to the `BluetoothDevice` to establish a new connection (and
obtain a new `Gatt` object).

In [Kable], this has been simplified to a unified [`Peripheral`], which allows both connection management
(connecting/disconnect) as well as performing I/O operations. This allows for holding a reference to a single object
representing the remote peripheral (whether it be in a pre-connected or connected state).

To [establish a connection] in [Kable], you must first obtain a [`Peripheral`] object, either via an advertisement (from
scanning) or from a [`BluetoothDevice`]:

```kotlin
// See https://github.com/JuulLabs/kable#scanning for an example of obtaining an `Advertisement`.
val peripheral: Peripheral = scope.peripheral(advertisement)

// or

val peripheral: Peripheral = scope.peripheral(bluetoothDevice)
```

The [`Peripheral`] object is initially in a disconnected state. To establish a connection, simply call the [`connect`]
function. The [`connect`] function suspends until connected or failure occurs (exception is thrown on connect failure).
To cancel an in-flight connection attempt, call the `disconnect` function. If a connection has already been established,
then the `disconnect` function will close the connection. _Similar to Able, cancelling the Coroutine scope that the
connect operation is being performed in, will cancel the connection attempt._

```kotlin
// `launch` used for illustrative purposes, to allow for `connect` to happen asynchronously so that
// `disconnect` can be called soon after.
val job = launch {
    peripheral.connect()
}

// Cancels in-flight connection attempt, or closes connection if connection is already established.
peripheral.disconnect()

// or: job.cancel() will also cancel the in-flight connection attempt.
```

Because `peripheral.connect()` suspends until a connection is established, you can perform BLE operations in a
sequential manner, for example:

```kotlin
peripheral.connect() // Suspends until connected (or failure occurs, in which case an exception is thrown).
peripheral.read(...) // Will not execute until connection is established.
peripheral.write(...) // Will not execute until previous read is complete.
```

_In [Kable], service discovery is performed automatically (upon connection), so there is no longer a need to explicitly
invoke a `discoverServices` function, as was needed with Able._

## I/O

With Able, to perform an I/O operation (such as writing a characteristic), you would need to first connect, then
retrieve the list of services via `services: List` property of the `Gatt`, or use the `getService(UUID)` function. Then
iterate over the services to find the appropriate characteristic. Once found, the characteristic object would be passed
to the `readCharacteristic` or `writeCharacteristic` functions.

With [Kable], characteristics and descriptors are defined ahead of time (can be defined before a connection is
established), for example:

```kotlin
val characteristic = characteristicOf(
    service = "00001815-0000-1000-8000-00805f9b34fb",
    characteristic = "00002a56-0000-1000-8000-00805f9b34fb"
)
```

_[`Characteristic`] and [`Descriptor`] objects (returned by the [`characteristicOf`] and [`descriptorOf`] functions,
respectively) can be reused. In other words, you can use the same [`Characteristic`] object to both `read`, `write` and
`observe` the same characteristic._

When executing an I/O operation, in Able a result object would be returned. The result object would need to be inspected
to check the GATT status (to ensure successful operation). When successful, the `value` would need to be retrieved from
the result object. If a failure occurred an exception would be thrown. With [Kable], the API has been simplified,
whereas a read operation (e.g. [`read`]) returns the `ByteArray` value of the characteristic when successful and an
exception is thrown on failure (including non-success response).

## State

With Able, the connection state could be monitored by collecting the `onConnectionStateChange` [`Flow`]. If the
connection was lost/dropped, then collection of `onConnectionStateChange` would need to be cancelled, a new connection
would need to be established and the new `Gatt`'s `onConnectionStateChange` could be collected.

Since the same [`Peripheral`] in [Kable] can be reused (connected, disconnect, and reconnected to again) its `state`
[`Flow`] can be collected across connections. It can also be used for reconnection handling (e.g. executing [`connect`]
on `Disconnected` state — and optionally, with an exponential backoff).

## Observation

To observe a characteristic in Able, it was a manual process that involved:

- Connecting to remote peripheral (acquiring a `Gatt` object)
- Discovering services
- Iterating over services to find characteristic of interest
- Enable notifications
- Write appropriate notification/indication descriptor
- Collect `onCharacteristicChanged` Flow

If connection was lost/dropped, the process would need to be repeated to establish a new characteristic change [`Flow`].

In [Kable], observation is as simple as collecting the [`Flow`] returned by [`observe`]. Observations can be collected
even before a connection is established. The [`Flow`] will remain active even on disconnect, and simply resume
observation (emission of changes) upon reconnection.

_Be aware that the [`Flow`] returned by [`observe`] is **not** meant to be shared (i.e. it is **not** meant to have
multiple collectors). If you wish to have multiple subscribers, then you can call [`observe`] again to obtain another
[`Flow`] or use the [`shareIn`] operator._

## Structured Concurrency

[Kable] leverages structured concurrency by making [`Peripheral`]s children of the [`CoroutineScope`] that they are
created with (via [`CoroutineScope.peripheral`] extension function). When the [`CoroutineScope`] is cancelled, then all
[`Peripheral`]s created under that scope will disconnect and be disposed. This means [`Peripheral`]s have a lifecycle
not longer than the [`CoroutineScope`] they are created under.

Able did not have the notion of a parent/child relationship (connections being tied to a [`CoroutineScope`]). To have
a similar behavior in [Kable], you could use [`GlobalScope`] to create [`Peripheral`]s. Though, it is advised to
determine the appropriate lifecycle of a [`Peripheral`] and use or create an appropriate [`CoroutineScope`]. This
ensures that all [`Peripheral`]s under the designated [`CoroutineScope`] are properly disposed (prevent leaking of
connections). Using [`GlobalScope`] is simply a means of forfeiting that safety and managing the connection yourself. In
other words, if [`GlobalScope`] is used, be sure that when the [`Peripheral`] connection is no longer needed that
[`disconnect`] is called.


[Kable]: https://github.com/JuulLabs/kable
[Scanner]: https://github.com/JuulLabs/kable#scanning
[`CoroutineScope.peripheral`]: https://juullabs.github.io/kable/core/core/com.juul.kable/peripheral.html
[`BluetoothDevice`]: https://developer.android.com/reference/android/bluetooth/BluetoothDevice
[`Peripheral`]: https://juullabs.github.io/kable/core/core/com.juul.kable/-peripheral/index.html
[establish a connection]: https://github.com/JuulLabs/kable#connectivity
[`connect`]: https://juullabs.github.io/kable/core/core/com.juul.kable/-peripheral/connect.html
[`disconnect`]: https://juullabs.github.io/kable/core/core/com.juul.kable/-peripheral/disconnect.html
[`CoroutineScope`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/
[`GlobalScope`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-global-scope/
[`read`]: https://juullabs.github.io/kable/core/core/com.juul.kable/-peripheral/read.html
[`Flow`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/
[`observe`]: https://juullabs.github.io/kable/core/core/com.juul.kable/-peripheral/observe.html
[`shareIn`]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/share-in.html
[`Characteristic`]: https://juullabs.github.io/kable/core/core/com.juul.kable/-characteristic/index.html
[`Descriptor`]: https://juullabs.github.io/kable/core/core/com.juul.kable/-descriptor/index.html
[`characteristicOf`]: https://juullabs.github.io/kable/core/core/com.juul.kable/characteristic-of.html
[`descriptorOf`]: https://juullabs.github.io/kable/core/core/com.juul.kable/descriptor-of.html

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

    if (gatt.discoverServices().status != BluetoothGatt.GATT_SUCCESS) {
        // discover services failed
    }

    val service = gatt.getService(serviceUuid) ?: error("Service $serviceUuid not found.")
    val characteristic = service.getCharacteristic(characteristicUuid)
        ?: error("Characteristic $characteristicUuid not found.")

    val result = gatt.readCharacteristic(characteristic)
    if (result?.status == BluetoothGatt.GATT_SUCCESS) {
        println("result.value = ${result.value}")
    } else {
        // read characteristic failed
    }

    gatt.disconnect()
    gatt.close()
}

```


[`BluetoothDevice`]: https://developer.android.com/reference/android/bluetooth/BluetoothDevice.html

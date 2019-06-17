# Processor

A `Processor` adds the ability to process (and optionally modify) GATT data pre-write or post-read.

```kotlin
class SecureProcessor : Processor {

    override fun readCharacteristic(characteristic: BluetoothGattCharacteristic, value: ByteArray): ByteArray {
        // todo: decrypt `value` to `newValue`
        return newValue
    }

    override fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: WriteType
    ): ByteArray {
        // todo: encrypt `value` to `newValue`
        return newValue
    }

    override fun writeDescriptor(descriptor: BluetoothGattDescriptor, value: ByteArray): ByteArray {
        // todo: encrypt `value` to `newValue`
        return newValue
    }
}

val gatt = device
    .connectGattOrThrow(context, autoConnect = false)
    .withProcessors(SecureProcessor(), LoggingProcessor())
```

# Setup

## Gradle

```groovy
dependencies {
    implementation "com.juul.able:processor:0.7.1"
}
```

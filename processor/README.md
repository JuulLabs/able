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

[![JitPack version](https://jitpack.io/v/JUUL-OSS/able.svg)](https://jitpack.io/#JUUL-OSS/able)

```groovy
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    implementation "com.github.JUUL-OSS.able:processor:$version"
}
```

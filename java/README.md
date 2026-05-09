# ashid (Java)

There is no separate Java module. The Java publish ships from the **Kotlin module** (`kotlin/`) — Kotlin/JVM compiles to plain JVM bytecode, so Java consumers depend on the same Maven coordinate and use the API directly.

## Install

```kotlin
implementation("agency.wilde:ashid:1.0.0")
```

```xml
<dependency>
  <groupId>agency.wilde</groupId>
  <artifactId>ashid</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Example

See [`examples/BasicUsage.java`](./examples/BasicUsage.java) for a runnable demo covering creation, parsing, time-sortability, validation, normalization, and the entity-prefix pattern.

The equivalent Kotlin demo lives at [`../kotlin/examples/BasicUsage.kt`](../kotlin/examples/BasicUsage.kt) — same API, idiomatic Kotlin syntax.

## Source

Implementation lives in [`../kotlin/src/main/kotlin/agency/wilde/ashid/`](../kotlin/src/main/kotlin/agency/wilde/ashid/). The classes you'll use from Java are `agency.wilde.ashid.Ashid` and `agency.wilde.ashid.EncoderBase32Crockford`.

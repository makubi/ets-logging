A logging framework that brings compile-time safety to your log statements.
Specify which data is encodable and how exactly the encoding should take place.
Then the compiler prevents you from creating invalid log statements.

_WARNING_
This library is currently in the prototyping phase.
Don't expect working code and don't even try to use it!


# Basic Idea
Add this to your `build.sbt`:
```scala
libraryDependencies += "de.kaufhof.ets" %% "ets-logging-core" % "0.1.0-SNAPSHOT"
```

Given some domain objects:
```scala
sealed trait Epic extends Product
object Epic {
  case object FeatureA extends Epic
  case object FeatureB extends Epic
}
case class VariantId(value: String)
case class Variant(id: VariantId, name: String)
case class TestClass(a: Int, b: String)
```

Start defining a set of Keys you wish to use within your logs events:
```scala
import de.kaufhof.ets.logging._
import de.kaufhof.ets.logging.syntax._

import java.time.Instant
import java.util.UUID

object StringKeys extends LogKeysSyntax[String] with DefaultStringEncoders {
  val Logger:        Key[Class[_]] =      Key("logger")      .withImplicitEncoder
  val Level:         Key[Level] =         Key("level")       .withImplicitEncoder
  val Message:       Key[String] =        Key("msg")         .withImplicitEncoder
  val Timestamp:     Key[Instant] =       Key("ts")          .withExplicit(Encoder.fromToString)
  val VariantId:     Key[VariantId] =     Key("variantid")   .withExplicit(Encoder[VariantId].by(_.value))
  val VariantName:   Key[String] =        Key("variantname") .withImplicitEncoder
  val SomeUUID:      Key[UUID] =          Key("uuid")        .withImplicitEncoder
  val RandomEncoder: Key[Random] =        Key("randenc")     .withExplicit(Encoder[Random].by(_.nextInt(100)))
  val RandomEval:    Key[Int] =           Key("randeval")    .withImplicitEncoder
  val Epic:          Key[Epic] =          Key("epic")        .withExplicit(Encoder[Epic].by(_.productPrefix))
}
```

`LogKeysSyntax[String]` contains a small DSL to setup keys.
That includes all neccessary syntax to specify `Key` s and associate `Encoder` s to it.
`Key` instances require an `id` of type string and an `Encoder`.
`Encoder` s are used to encode values into `String` as indicated by the type parameter in this case.
`DefaultStringEncoders` is a set of `implicit` `Encoder` definitions for the most common types.
Some of the most common types are; `Int`, `Long`, `Double`, `UUID` and so forth.
These most common type `Encoders` facilitate selecting an `Encoder` for the domain specific `Key` s.
Existing `Encoder` instances can be used to derive new `Encoder` instances, like depicted above.

Next provide a configuration combining the previously defined `Keys` and an optional set of `Decomposer` instances.
```scala
object StringLogConfig extends DefaultLogConfig[String, Unit] with DefaultStringEncoders {
  override type Combined = String
  override def combiner: LogEventCombiner[String, String] = StringLogEventCombiner
  override def appender: Appender = StdOutStringLogAppender
  override def rootLevel: Level = Level.Info

  object Decomposers extends Decomposer2DecomposedImplicits[String] {
    import syntax._
    implicit lazy val variantDecomposer: Decomposer[Variant] = variant =>
      Decomposed(
        Keys.VariantId ~> variant.id,
        Keys.VariantName ~> variant.name
    )
    implicit lazy val epicDecomposer: Decomposer[Epic] = epic => Decomposed(Keys.Epic ~> epic)
  }

  val syntax = ConfigSyntax(StringKeys, Decomposers)
  override def predefKeys: PredefKeys = syntax.Keys
}
```

Provide a logger instance called `log` via mixin into your `class`/`trait`/`object` using the above config.
Then use it to log different attributes in combination with a message:
```scala
object Main extends StringLogConfig.LogInstance {
  val variant: Variant = Variant(VariantId("VariantId"), "VariantName")
  val uuid: UUID = java.util.UUID.fromString("723f03f5-13a6-4e46-bdac-3c66718629df")

  // use standard log methods with severity for the log level and a message
  log.info("test234")
  log.debug("test123") // will be omitted due to configured rootLevel
  // provide additional information with an arbitrary amount of key value pairs, called attributes
  log.error("test345", Keys.VariantId ~> variant.id, Keys.SomeUUID -> uuid)
  // use the generic event method to construct arbitrary log events without any predefined attributes
  log.event(Keys.VariantId ~> variant.id, Keys.SomeUUID -> uuid, Keys.Timestamp ~> Instant.MIN)
  // inputs to encoders are contravariant and therefore directly accept instances of the key-types's subtypes
  log.info("""yay \o/""", Keys.Epic -> Epic.FeatureA)
  // or pass any amount of decomposable objects
  // this requires an implicit decomposer to be in scope
  // then the decomposer will decompose the available attributes for you
  import encoding.string.StringLogConfig.syntax.decomposers._
  log.event(variant)
  // inputs to decomposers are contravariant as well and therefore accept instances of the input-types's subtypes
  log.info("""yay \o/""",  Epic.FeatureA)
}
```

Also take look into the short self-contained complete compliable example under:
[test/Main.scala](ets-logging-usage/src/main/scala/de/kaufhof/ets/logging/test/Main.scala)

Possible output for string encoding as shown above could then look like this:
```
level -> Info | logger -> de.kaufhof.ets.logging.test.Main$README$ | msg -> test234 | ts -> 2019-01-25T19:39:52.594Z
level -> Error | logger -> de.kaufhof.ets.logging.test.Main$README$ | msg -> test345 | ts -> 2019-01-25T19:39:52.629Z | uuid -> 723f03f5-13a6-4e46-bdac-3c66718629df | variantid -> VariantId
logger -> de.kaufhof.ets.logging.test.Main$README$ | ts -> -1000000000-01-01T00:00:00Z | uuid -> 723f03f5-13a6-4e46-bdac-3c66718629df | variantid -> VariantId
epic -> FeatureA | level -> Info | logger -> de.kaufhof.ets.logging.test.Main$README$ | msg -> yay \o/ | ts -> 2019-01-25T19:39:52.636Z
logger -> de.kaufhof.ets.logging.test.Main$README$ | ts -> 2019-01-25T19:39:52.643Z | variantid -> VariantId | variantname -> VariantName
epic -> FeatureA | level -> Info | logger -> de.kaufhof.ets.logging.test.Main$README$ | msg -> yay \o/ | ts -> 2019-01-25T19:39:52.650Z
level -> Info | logger -> de.kaufhof.ets.logging.test.Main$Slf4j$ | msg -> test234 | ts -> 2019-01-25T19:39:52.707Z
level -> Info | logger -> test | msg -> Log with slf4j | ts -> 2019-01-25T19:39:52.723Z
level -> Info | logger -> de.kaufhof.ets.logging.test.Main$Configurable$ | message -> test-configurable | timestamp -> 2019-01-25T19:39:52.924Z
```


## Configurable
In some situations it is necessary to encode into different types depending on the runtime configuration.
As a developer it would be convenient to see a compact string output but during local development.
However, in production it is often required to log JSON instead of string.
The `de.kaufhof.ets.logging.test.encoding.configurable.TupledLogConfig` show cases this at
[test/encoding/configurable.scala](ets-logging-usage/src/main/scala/de/kaufhof/ets/logging/test/encoding/configurable.scala).
It makes use of the `DefaultPairEncoders[String, Json]` to encode into a tuple `(String, Json)`.
The used encoders help to make sure that attributes given to the logger do only compile if both encoders are available.
Encoders are passed into a combiner which actually evaluates the values and applies the encoding.
The used `TupleToEitherCombiner` allows to resort to only one of both alternatives at runtime.
The interesting part to decide this is the `takeLeft(e: LogEvent[(String, Json)]): Boolean` method.
Implement an appropriate expression to decide which alternative the combiner should keep.
```scala
override def combiner: EventCombiner = new TupleToEitherCombiner[String, Json] {
  // setup logic to either take left or right
  override def takeLeft(e: LogEvent[(String, Json)]): Boolean = true

  override def combiner1: LogEventCombiner[String, String] = StringLogEventCombiner
  override def combiner2: LogEventCombiner[Json, Json] = JsonLogEventCombiner
}
```


# Module Layout
The project is organized as a maven multi module project.
The idea is that the `ets-logging-core` doesn't deliver any dependencies.
Code fragments that require any dependencies are delivered as sub modules.
Any sub module requires the `ets-logging-core` to work properly.
Not all planned sub modules are already available yet.
Artificats available only during prototyping will disappear once the project is stable.

```
<repository-root>
├── ets-logging-apisandbox       !!! only during prototyping !!! organized with sbt
├── ets-logging-parent           parent pom project to share configs between sub modules
├── ets-logging-core             general api without dependencies
├── ets-logging-actor            akka.actor.Actor specific predefined keys and encoders
├── ets-logging-playjson         play.JsValue encoders
├── ets-logging-circejson        circe.Json encoders
├── ets-logging-catsio           cats.effect.IO appender
├── ets-logging-slf4j            org.slf4j.spi.SLF4JServiceProvider implementation to gather events from sfl4j
├── ets-logging-logstash         !!! not available yet !!! slf4j.Marker encoders
├── ets-logging-mdc              !!! not available yet !!! derive decomposer from case classes
├── ets-logging-shapeless        !!! not available yet !!! derive decomposer from case classes
└── ets-logging-usage            example usage combining all modules
```


# `ets-logging-apisandbox`
This is the place to lay down the target api.
Furthermore, it is used to test some api aspects.
Aspects include: General API Look And Feel, Type inference, Performance, Physical Module Layout, Example Configuration.
To cover all aspects, all required dependencies are available at once in a single sbt build.
Aspects are covered in separate packages as single scala files.
Physical modules that emerge are developed as objects within those files.
Names are still very volatile.


# `ets-logging-slf4j`
This module helps to integrate emitted log4j log events into the `ets-logging` framework.


Necessary steps:
* Implement an `EtsSlf4jSpiProvider`
* Setup [ServiceLoader](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html) SPI binding

The simplest setup just takes a previously created `LogConfig` instance:
```scala
class Slf4jProvider extends EtsSlf4jSpiProvider[String, Unit] {
  override def config: DefaultLogConfig[String, Unit] = StringLogConfig
}
```

Override some default settings by overriding the respective methods:
```scala
class Slf4jProvider extends EtsSlf4jSpiProvider[String, Unit] {
  override def config: DefaultLogConfig[String, Unit] = StringLogConfig

  override def logMarker: EtsSlf4jSpiProvider.LogAttributeResolver[E] = {
   case m: ExtendedTestMarker => StringKeys.ExtendedTestMarker -> m
   case m: TestMarker => StringKeys.TestMarker -> m
  }

  override def levelForLogger(name: String, marker: Marker): Level = {
    if (marker.isInstanceOf[ExtendedTestMarker] && name == "myLogger") {
      Level.Debug
    } else {
      super.levelForLogger(name, marker)
    }
  }
}
```

To register a valid `ServiceLoader` SPI binding create a file called `org.slf4j.spi.SLF4JServiceProvider`.
Put the fully-qualified class name of the class' implementation into it.
Place the file on the classpath under `META-INF/services`.
These commands illustrate the necessary steps for a maven like project residing in a directory called `<project>`.
```
mkdir -p <project>/src/main/resources/META-INF/services/
cd <project>/src/main/resources/META-INF/services/
echo "de.kaufhof.ets.logging.test.Slf4jProvider" > org.slf4j.spi.SLF4JServiceProvider
```

There is a runnable version of this example at
[test/encoding/slf4j.scala](ets-logging-usage/src/main/scala/de/kaufhof/ets/logging/test/encoding/slf4j.scala).
Take `de.kaufhof.ets.logging.test.Main.Slf4j` as a reference to how it is being used.

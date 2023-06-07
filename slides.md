---
title: Zionomicon chapters 33 - 40
description:
author: Jim Weinert
keywords: scala
url: https://github.com/jwcarvana
marp: true
---

# Chapter 33: ZIO Tests

Tests leveraging the power of ZIO

---

# Tests as effects

```scala mdoc:silent
import zio.*
import zio.test.*

type ZTest[-R, +E] = ZIO[R, TestFailure[E], TestSuccess]
```

---


# TestSuccess Types

TestSuccess.Succeeded
TestSuccess.Ignored

---

# TestFailure Types

TestFailure.Assertion
TestFailure.Runtime

---

# Why?

```scala mdoc:silent
import org.scalatest.*

def unsafeRun[E, A](zio: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(zio).getOrThrowFiberFailure()
    }

class ScalaTestSpec extends FunSuite {
    test("addition works") {
        assert(unsafeRun(ZIO.succeed(1)) === 2)
    }
}
```

---

# Why?

```scala mdoc:silent
import org.scalatest.*

def unsafeRun[E, A](zio: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(zio).getOrThrowFiberFailure()
    }

class ScalaTestSpec extends FunSuite {
    test("addition works") {
        assert(unsafeRun(ZIO.succeed(1)) === 2) // Is this a typo?
    }
}
```

---

# Why?

* Easily mix effectual and non-effectual code
* Timeout tests (Futures are not interruptible)
* acquireRelease operator

---

# Building Blocks

```scala mdoc:silent
sealed trait BoolAlgebra[+A]

final case class And[+A](left: BoolAlgebra[A], right: BoolAlgebra[A]) extends BoolAlgebra[A]
final case class Or[+A](left: BoolAlgebra[A], right: BoolAlgebra[A]) extends BoolAlgebra[A]

final case class Not[+A](result: BoolAlgebra[A] ) extends BoolAlgebra[A]
final case class Success[+A](value: A ) extends BoolAlgebra[A]

type TestResult = BoolAlgebra[FailureDetails]

type ZTest = ZIO[R, E, TestResult]
```

---

# Specs are a collection of tests (simplified code)

```scala mdoc:silent
sealed trait ZSpec[-R, +E]

final case class Suite[-R, +E](
    label: String,
    specs: ZIO[R with Scope, TestFailure[E], Vector[ZSpec[R, E]]]
) extends ZSpec[R, E]

final case class Test[-R, +E](
  label: String,
  test: ZIO[R, TestFailure[E], TestSuccess]
)
```

---

# Specs are a collection of tests (simplified code)

```scala mdoc:silent
sealed trait ZSpec[-R, +E]

final case class Suite[-R, +E](
    label: String,
    specs: ZIO[R with Scope, TestFailure[E], Vector[ZSpec[R, E]]] // Note scope
) extends ZSpec[R, E]

final case class Test[-R, +E](
  label: String,
  test: ZIO[R, TestFailure[E], TestSuccess]
)
```

---

# Chapter 34: Assertions

TODO

---

# Chapter 35: The Test Environment

By default every test has its own TestEnvironment.

* Clock
* Console
* Random
* System

---

```scala mdoc:silent
import zio.*
import zio.test.*

test("tests use test implementations of standard services") {
    for {
        _ <- ZIO.sleep(1.second)
    } yield assertCompletes
}
```

---

```scala mdoc:silent
import zio.test.Assertion.*

suite("system suite")(
    test("set a system variable") {
        for {
            _ <- TestSystem.putEnv("key", "value")
        } yield assertCompletes
    },
    test("get a system variable") {
        for {
            value <- System.env("key")
        } yield assert(value)(isSome(equalTo("value")))
    }
)
```

---

# TestClock

```scala mdoc:silent
import java.time.*

trait TestClock {
    def adjust(duration: Duration): UIO[Unit]
    def setDateTime(dateTime: OffsetDateTime): UIO[Unit] def setTime(duration: Duration): UIO[Unit]
    def setTimeZone(zone: ZoneId): UIO[Unit]
    def sleep: UIO[List[Duration]]
    def timeZone: UIO[ZoneId]
}
```

---

# TestClock: How to

1. Fork the effect involving the passage of time that we want to test
2. Adjust the TestClock to the time we expect the effect to be completed by
3. Verify that the expected results from the effect being tested have occurred

---

# TestClock: Example

```scala mdoc:silent
test("we can test effects involving time") {
    for {
        ref <- Ref.make(false)
        _ <- ref.set(true).delay(1.hour).fork
        _     <- TestClock.adjust(1.hour)
        value <- ref.get
    } yield assert(value)(isTrue)
}
```

---

# TestClock: Bad Example

Hangs indefinitely eventually timing out

```scala mdoc:silent
test("we can test effects involving time") {
    for {
        ref   <- Ref.make(false)
        _     <- ref.set(true).delay(1.hour)
        _     <- TestClock.adjust(1.hour)
        value <- ref.get
    } yield assert(value)(isTrue)
}
```

---

# TestClock: Another Bad Example

Hangs indefinitely eventually timing out

```scala mdoc:silent
test("we can test effects involving time") {
    for {
        ref   <- Ref.make(false)
        _     <- TestClock.adjust(1.hour)
        _     <- ref.set(true).delay(1.hour).fork
        value <- ref.get
    } yield assert(value)(isTrue)
}
```

---

# TestClock: Testing a schedule

```scala mdoc:silent
test("testing a schedule") {
    for {
        latch <- Promise.make[Nothing, Unit]
        ref   <- Ref.make(3)

        countdown = ref
                    .updateAndGet(_ - 1)
                    .flatMap(n => latch.succeed(()).when(n == 0))

        _ <- countdown
            .repeat(Schedule.fixed(2.seconds))
            .delay(1.second)
            .fork
        _ <- TestClock.adjust(5.seconds)
        _ <- latch.await
    } yield assertCompletes // Get your assertTrue(true) out of here
}
```

---

# TestConsole

```scala mdoc:silent
trait TestConsole extends Restorable {
    def clearInput: UIO[Unit]
    def clearOutput: UIO[Unit]
    def feedLines(lines: String*): UIO[Unit]
    def output: UIO[Vector[String]]
    def outputErr: UIO[Vector[String]]
}
```

---

# TestConsole

```scala mdoc:silent
trait TestConsole extends Restorable {
    def clearInput: UIO[Unit]
    def clearOutput: UIO[Unit]
//////////////////////////////////////////////
    def feedLines(lines: String*): UIO[Unit]
    def output: UIO[Vector[String]]
//////////////////////////////////////////////
    def outputErr: UIO[Vector[String]]
}
```

---

# TestConsole: Example

```scala mdoc:silent
import zio.Console.*

import java.io.IOException

val myConsoleProgram: ZIO[Any, IOException, Unit] =
    for {
        _    <- printLine("What's your name?")
        name <- readLine
        _    <- printLine(s"Hi $name! Welcome to ZIO!")
    } yield ()

test("testing a console program deterministically") {
    for {
        _      <- TestConsole.feedLines("Jane")
        _      <- myConsoleProgram
        output <- TestConsole.output
    } yield assert(output)(
        equalTo(
            Vector(
                    "What's your name",
                    "Hi Jane! Welcome to ZIO!\n"
            )
        )
    )
}
```

---

# TestConsole: Helpful aspects

```scala mdoc:silent
trait TestConsole {
    def debug[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
    def silent[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
}
```

---

# TestRandom

Two modes
* Works like Random
* Works like TestConsole

---

# TestRandom: Using it like Random

```scala mdoc:silent
for {
    _      <- TestRandom.setSeed(42L)
    first  <- Random.nextLong
    _      <- TestRandom.setSeed(42L)
    second <- Random.nextLong
} yield assert(first)(equalTo(second))
```

---

# TestRandom: Feeding it lines

```scala mdoc:silent
trait TestRandom {
    def clearBooleans: UIO[Unit]
    def clearBytes: UIO[Unit]
    def clearChars: UIO[Unit]
    def clearDoubles: UIO[Unit]
    def clearFloats: UIO[Unit]
    def clearInts: UIO[Unit]
    def clearLongs: UIO[Unit]
    def clearStrings: UIO[Unit]
    def feedBooleans(booleans: Boolean*): UIO[Unit]
    def feedBytes(bytes: Chunk[Byte]*): UIO[Unit]
    def feedChars(chars: Char*): UIO[Unit]
    def feedDoubles(doubles: Double*): UIO[Unit]
    def feedFloats(floats: Float*): UIO[Unit]
    def feedInts(ints: Int*): UIO[Unit]
    def feedLongs(longs: Long*): UIO[Unit]
    def feedStrings(strings: String*): UIO[Unit]
    def getSeed: UIO[Long]
    def setSeed(seed: Long): UIO[Unit]
}
```

---

# TestRandom: Feeding it lines

```scala mdoc:silent
for {
    _ <- TestRandom.feedInts(1, 2, 3)
    x <- Random.nextInt
    y <- Random.nextInt
    z <- Random.nextInt
} yield assert((x, y, z))(equalTo((1, 2, 3)))
```

---

# TestRandom: Returns the same value no matter the method called

```scala mdoc:silent
for {
    _ <- TestRandom.feedInts(42)
    n <- Random.nextIntBounded(10)
} yield assert(n)(equalTo(42))
```

---

# TestRandom: Works normally when not fed

```scala mdoc:silent
for {
    _ <- TestRandom.feedInts(42)
    x <- Random.nextInt
    y <- Random.nextInt
    z <- Random.nextInt
} yield assert((x, y, z))(equalTo((42, Any, Any)))
```

# TestSystem

```scala mdoc:silent
trait TestSystem {
    def putEnv(name: String, value: String): UIO[Unit]
    def putProperty(name: String, value: String): UIO[Unit]
    def setLineSeparator(lineSep: String): UIO[Unit]
    def clearEnv(variable: String): UIO[Unit]
    def clearProperty(prop: String): UIO[Unit]
}
```

# What's a System.property?

Java command-line options

```
-Dname=value
```

---

# Accessing the Live Environment using the ZIO built-ins

* withLiveClock
* withLiveConsole
* withLiveRandom
* withLiveSystem
* withLiveEnvironment

---

# Accessing the Live Environment using the ZIO built-ins

```scala mdoc:silent
test("TestLiveClock") { ... } @@ withLiveClock
```

---

# Overriding the Live Environment with your cursed implementation

```scala mdoc:silent
import zio.*

object MyClockLive extends Clock {
  ...
}

ZIO.withClock(MyClockLive)(effect)
```

---

# Chapter 36: Test Aspects

```scala mdoc:silent
TestAspect.ignore
TestAspect.nonFlaky
TestAspect.timeout
```

---

# Chapter 36: Test Aspects

---

# Chapter 36: Test Aspects

Aspect vs Operator

---

# Chapter 36: Test Aspects

* What we want to test
* How we want to test it

---

wat?

```scala mdoc:silent
import zio.*
import zio.test.*
import zio.test.Assertion.*

test("foreachPar preserves ordering") {
    val zio = ZIO
        .foreach(1 to 100) { _ =>
            ZIO.foreachPar(1 to 100)(ZIO.succeed(_)).map(_ == (1 to 100))
        }
        .map(_.forall(identity))

    assertZIO(zio)(isTrue)
}
```

---

Better!

```scala mdoc:silent
import zio.*
import zio.test.*
import zio.test.Assertion.*

test("foreachPar preserves ordering") {
    for {
        values <- ZIO.foreachPar(1 to 100)(ZIO.succeed(_))
    } yield assert(values)(equalTo(1 to 100))
} @@ TestAspect.nonFlaky(100),
```

---

Notice the test body does not change.

```scala mdoc:silent
test("foreachPar preserves ordering") {
    assertZIO(ZIO.foreachPar(1 to 100)(ZIO.succeed(_)))(
        equalTo(1 to 100),
    )
} @@ TestAspect.nonFlaky(100) @@ TestAspect.jvmOnly @@ TestAspect.timeout(60.seconds),
```

---

# Polymorphism!

```scala mdoc:silent
trait ZSpec[-R, +E]

trait TestAspect {
    def apply[R, E](spec: ZSpec[R, E]): ZSpec[R, E]
}
```
---

# Polymorphism!

This works for nonFlaky, but...

```scala mdoc:silent
trait ZSpec[-R, +E]

trait TestAspect {
    def apply[R, E](spec: ZSpec[R, E]): ZSpec[R, E]
}
```

---

# Polymorphism!

We need something that requries Clock and returns a timeout error

```scala mdoc:silent
trait ZSpec[-R, +E]

trait TestAspect[-LowerR, +UpperR, -LowerE, +UpperE] {
  def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE](
    spec: ZSpec[R, E]
  ): ZSpec[R, E]
}
```

---

# Polymorphism!

We need something that requries Clock and returns a tmeout error

```scala mdoc:silent
trait ZSpec[-R, +E]

trait TestAspect[-LowerR, +UpperR, -LowerE, +UpperE] {
  def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE](
    spec: ZSpec[R, E]
  ): ZSpec[R, E]
}

val timeout: TestAspect[Nothing, Clock, Nothing, Any] =
  new TestAspect {
    def apply[R <: Clock, E](spec: ZSpec[R, E]): ZSpec[R, E] =
      ???
}
```

---

# Common Test Aspects

* before and after
* flaky and nonFlaky
* ignore

---

# Uncommon Test Aspects

diagnose: runs each test on its own fiber to investigate deadlocks

---

# Uncommon Test Aspects

Platform specific aspects

```scala mdoc:silent
exceptJS
exceptJVM
exceptNative
jsOnly
jvmOnly
nativeOnly
```

```scala mdoc:silent
exceptDotty
exceptScala2
exceptScala211
exceptScala212
exceptScala213
dottyOnly
scala2Only
scala211Only
scala212Only
scala213Only
```
---

# Uncommon Test Aspects

Platform specific aspects

```scala mdoc:silent
dotty
js
jvm
native
scala2
scala211
scala212
scala213
```

---

# Chapter 36: Test Aspects

* What we want to test (use assertions)
* How we want to test it (use a TestAspect)

---

How we test is secretly modular

---

# Chapter 37: Using Resources in Tests

---

# Chapter 37: Using Resources in Tests

NOT FOUND

---

# Chapter 38: Property Based Testing

"In property based testing the test framework generates a large number of values and tests each of them, identifying either a counterexample to the assertion or reporting that no counterexample was found." AKA fuzzing

---

# Chapter 38: Property Based Testing

```scala mdoc:silent
test("integer addition is associative") {
    check(Gen.int, Gen.int, Gen.int) { (x, y, z) =>
        val left  = (x + y) + z
        val right = x + (y + z)
        assert(left)(equalTo(right))
    }
},
```

---

# Chapter 38: Property Based Testing

```scala mdoc:silent
test("integer addition is associative") {
    check(Gen.int, Gen.int, Gen.int) { (x, y, z) =>
        val left  = (x + y) + z
        val right = x + (y + z)
        assert(left)(equalTo(right))
    }
} @@ TestAspect.samples(100),
```
---

# Limitations of Property Based Testing

* Does not identify specific counter examples.
* Defaults to 200 sample sizes. Integers have billions of possible values.
* Complex types increase the possible permutations.
* The generator needs to match your domain.

---

# Considering Bounds

* Int.MinValue, Int.MaxValue
* ASCII vs Unicode
* List(2, 1).sorted == List(1, 2)

---

# Parts of a property based test

check(Gen)(assertion)

* check - controls number of executions, parallel executions, and effects
* Gen - distribution of potential values
* assertion - property we're testing

---

# Generators

```scala mdoc:silent
import zio.stream.*

final case class Gen[-R, +A](
    sample: ZStream[R, Nothing, Sample[R, A]]
)

final case class Sample[-R, +A](
    value: A,
    shrinks: ZStream[R, Nothing, Sample[R, A]]
)
```

---

```scala mdoc:silent
final case class Stock(ticker: String, price: Double, currency: Currency)

sealed trait Currency

case object USD extends Currency
case object EUR extends Currency
case object JPY extends Currency

lazy val genTicker: Gen[Any, String] = ??

lazy val genPrice: Gen[Any, Double] = ???
lazy val genCurrency: Gen[Any, Currency] = ???

lazy val genStock: Gen[Any, Stock] = for {
    ticker   <- genTicker
    price    <- genPrice
    currency <- genCurrency
} yield Stock(ticker, price, currency)
```


---

```scala mdoc:silent
final case class Stock( ticker: String, price: Double, currency: Currency)

sealed trait Currency

case object USD extends Currency
case object EUR extends Currency
case object JPY extends Currency

lazy val genTicker: Gen[Any, String] = Gen.asciiString

lazy val genPrice: Gen[Any, Double] = ???
lazy val genCurrency: Gen[Any, Currency] = ???

lazy val genStock: Gen[Any, Stock] = for {
    ticker   <- genTicker
    price    <- genPrice
    currency <- genCurrency
} yield Stock(ticker, price, currency)
```

---

```scala mdoc:silent
final case class Stock( ticker: String, price: Double, currency: Currency)

sealed trait Currency

case object USD extends Currency
case object EUR extends Currency
case object JPY extends Currency

lazy val genTicker: Gen[Any, String] = Gen.asciiString

lazy val genPrice: Gen[Any, Double] = ???

lazy val genUSD: Gen[Any, Currency] = ???
lazy val genEUR: Gen[Any, Currency] = ???
lazy val genJPY: Gen[Any, Currency] = ???
lazy val genCurrency: Gen[Random, Currency] = Gen.oneOf(genUSD, genJPY, genEUR)

lazy val genStock: Gen[Any, Stock] = for {
    ticker   <- genTicker
    price    <- genPrice
    currency <- genCurrency
} yield Stock(ticker, price, currency)
```

---

```scala mdoc:silent
final case class Stock( ticker: String, price: Double, currency: Currency)

sealed trait Currency

case object USD extends Currency
case object EUR extends Currency
case object JPY extends Currency

lazy val genTicker: Gen[Any, String] = Gen.asciiString

lazy val genPrice: Gen[Any, Double] = ???

lazy val genUSD: Gen[Any, Currency] = Gen.const(USD)
lazy val genEUR: Gen[Any, Currency] = Gen.const(EUR)
lazy val genJPY: Gen[Any, Currency] = Gen.const(JPY)
lazy val genCurrency: Gen[Random, Currency] = Gen.oneOf(genUSD, genJPY, genEUR)

lazy val genStock: Gen[Any, Stock] = for {
    ticker   <- genTicker
    price    <- genPrice
    currency <- genCurrency
} yield Stock(ticker, price, currency)
```

---

```scala mdoc:silent
final case class Stock( ticker: String, price: Double, currency: Currency)

sealed trait Currency

case object USD extends Currency
case object EUR extends Currency
case object JPY extends Currency

lazy val genTicker: Gen[Any, String] = Gen.asciiString

lazy val genPrice: Gen[Any, Double] = ???

lazy val genUSD: Gen[Any, Currency] = Gen.const(USD)
lazy val genEUR: Gen[Any, Currency] = Gen.const(EUR)
lazy val genJPY: Gen[Any, Currency] = Gen.const(JPY)
lazy val genCurrency: Gen[Random, Currency] = Gen.oneOf(genUSD, genJPY, genEUR)

lazy val genStock: Gen[Any, Stock] = for {
    ticker   <- genTicker
    price    <- genPrice
    currency <- genCurrency
} yield Stock(ticker, price, currency)
```

---

# Prefer Transforming over Discarding

```scala mdoc:silent
val ints: Gen[Random, Int] =
    Gen.int(1, 100)

val evens: Gen[Random, Int] =
    ints.map(n => if (n % 2 == 0) n else n + 1)
```

---

# Transforming Generators

```scala mdoc:silent
trait Gen[-R, +A] {
    def mapZIO[R1 <: R, B](f: A => ZIO[R1, Nothing, B]): Gen[R1, B]
    def flatMap[R1 <: R, B](f: A => Gen[R1, B]): Gen[R1, B]
    def crossWith[R, A, B, C]( left: Gen[R, A], right: Gen[R, B])(f: (A, B) => C): Gen[R, C]
    def chunkOfN[R, A](n: Int)(gen: Gen[R, A]): Gen[R, Chunk[A]]
    def listOfN[R, A](n: Int)(gen: Gen[R, A]): Gen[R, List[A]]
    def mapOfN[R, A, B]( n: Int)(key: Gen[R, A], value: Gen[R, B]): Gen[R, Map[A, B]]
    def setOfN[R, A](n: Int)(gen: Gen[R, A]): Gen[R, Set[A]]
}
```

---

# Choosing Generators

Sample your success and your failure!

```scala mdoc:silent
trait Gen[-R, +A] {
    def either[R, A, B]( left: Gen[R, A], right: Gen[R, B]): Gen[R, Either[A, B]]
    def oneOf[R <: Random, A](gens: Gen[R, A]*): Gen[R, A]
    def elements[A](as: A*): Gen[Any, A]
    def weighted[R <: Random, A](gs: (Gen[R, A], Double)*): Gen[R, A]
}
```

---

# Filtering Generators

```scala mdoc:silent
trait Gen[-R, +A] {
    def collect[B](pf: PartialFunction[A, B]): Gen[R, B]
    def filter(f: A => Boolean): Gen[R, A]
    def filterNot(f: A => Boolean): Gen[R, A]
}
```

---

# Running Generators

It's just a stream!

```scala mdoc:silent
trait Gen[-R, +A] {
    def runCollectN(n: Int): ZIO[R, Nothing, List[A]]
}
```

---

# Deterministic Generators

```scala mdoc:silent
trait Gen[-R, +A] {
    def elements[A](as: A*): Gen[Random, A]           // random
    def fromIterable[A](as: Iterable[A]): Gen[Any, A] // in order stream
}
```

---

```scala mdoc:silent
test("every boolean permutation") {
    val booleans: Gen[Any, Boolean] =
        Gen.fromIterable(List(true, false))

    checkAll(booleans, booleans, booleans) { (x, y, z) =>
        val left  = (x && y) && z
        val right = x && (y && z)
        assert(left)(equalTo(right))
    }
}
```

---

# Samples and Shrinking

1. Generate values "smaller" than the original.
2. Ensure "smaller" satisfies the original generator.

---

# Samples and Shrinking

```scala mdoc:silent
import zio.stream.*

final case class Gen[-R, +A](
    sample: ZStream[R, Nothing, Sample[R, A]]
)

final case class Sample[-R, +A](
    value: A,
    shrinks: ZStream[R, Nothing, Sample[R, A]]
)
```

---

# Samples and Shrinking

* A sample is a value and a tree of all possible "shrinkings".
* The root is the original value.
* Each layer is a stream. Allows lazy loading.


```scala mdoc:silent
final case class Sample[-R, +A](
    value: A,
    shrinks: ZStream[R, Nothing, Sample[R, A]]
)
```

---

# Shrink Tree Rules

1. Within any given level, values to the “left”, that is earlier in the stream, must be “smaller” than values that are later in the stream.
2. All children of a value in the tree must be "smaller" than their parents.

---

* The default shrink for Integers moves the Gen closer to zero.
* Complexity explodes when using map and flatMap to combin generators.

```scala mdoc:silent
trait Gen[-R, +A] {
    def noShrink: Gen[R, A]
    def reshrink[R1 <: R, B](f: A => Sample[R1, B]): Gen[R1, B]
}
```

---

Example: a generator (0.0 to 1.0)

---

# Chapter 39: Test Annotations

---

# Chapter 39: Test Annotations

NOT FOUND

---

# Chapter 40: Reporting

---

# Chapter 40: Reporting

NOT FOUND

---

# Refresher: Variance

```scala mdoc:silent
class Foo[+A] // A covariant class
class Bar[-A] // A contravariant class
class Baz[A]  // An invariant class
```

---

# Refresher: Variance

```scala mdoc:silent
class Foo[+A] // A covariant class produces
class Bar[-A] // A contravariant class acts on
class Baz[A]  // An invariant class is mutable
```
---

# Refresher: Variance

https://youtu.be/aUmj7jnXet4

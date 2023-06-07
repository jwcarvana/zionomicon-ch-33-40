package com.carvana.examples

import zio.*
import zio.Console.*
import zio.test.*
import zio.test.Assertion.*

import java.io.IOException

object TestExamplesSpec extends ZIOSpecDefault {

  val myConsoleProgram: ZIO[Any, IOException, Unit] =
    for {
      _    <- printLine("What's your name?")
      name <- readLine
      _    <- printLine(s"Hi $name! Welcome to ZIO!")
    } yield ()

  def spec = suite("TestExamplesSpec")(
    suite("Chapter 35: The Test Environment")(
      suite("system suite")(
        test("set a system variable") {
          for {
            _ <- TestSystem.putEnv("key", "value")
          } yield assertCompletes
        },
        test("get a system variable") {
          for {
            value <- System.env("key")
          } yield assert(value)(isNone)
        },
      ),
      test("tests use test implementations of standard services -- FAILS") {
        for {
          _ <- ZIO.sleep(1.second)
        } yield assertCompletes
      } @@ TestAspect.ignore,
      test("we can test effects involving time") {
        for {
          ref   <- Ref.make(false)
          _     <- ref.set(true).delay(1.hour).fork
          _     <- TestClock.adjust(1.hour)
          value <- ref.get
        } yield assert(value)(isTrue)
      },
      test("we can test effects involving time -- FAILS") {
        for {
          ref   <- Ref.make(false)
          _     <- ref.set(true).delay(1.hour)
          _     <- TestClock.adjust(1.hour)
          value <- ref.get
        } yield assert(value)(isTrue)
      } @@ TestAspect.ignore,
      test("we can test effects involving time -- FAILS") {
        for {
          ref   <- Ref.make(false)
          _     <- TestClock.adjust(1.hour)
          _     <- ref.set(true).delay(1.hour).fork
          value <- ref.get
        } yield assert(value)(isTrue)
      } @@ TestAspect.ignore,
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
      },
      test("testing a console program deterministically") {
        for {
          _      <- TestConsole.feedLines("Jane")
          _      <- myConsoleProgram
          output <- TestConsole.output
        } yield assert(output)(
          equalTo(
            Vector(
              "What's your name?\n",
              "Hi Jane! Welcome to ZIO!\n",
            ),
          ),
        )
      },
      test("using TestRandom to test random number generation") {
        for {
          _      <- TestRandom.setSeed(42L)
          first  <- Random.nextLong
          _      <- TestRandom.setSeed(42L)
          second <- Random.nextLong
        } yield assert(first)(equalTo(second))
      },
      test("using TestRandom to feed Ints") {
        for {
          _ <- TestRandom.feedInts(1, 2, 3)
          x <- Random.nextInt
          y <- Random.nextInt
          z <- Random.nextInt
        } yield assert((x, y, z))(equalTo((1, 2, 3)))
      },
      test("TestRandom returns its value no matter the input") {
        for {
          _ <- TestRandom.feedInts(42)
          n <- Random.nextIntBounded(10)
        } yield assert(n)(equalTo(42))
      },
      test("TestRandom returns random values when not fed -- FAILS") {
        for {
          _ <- TestRandom.feedInts(42)
          x <- Random.nextInt
          y <- Random.nextInt
          z <- Random.nextInt
        } yield assert((x, y, z))(equalTo((42, 42, 42)))
      } @@ TestAspect.ignore,
      test("foreachPar preserves ordering") {
        val zio = ZIO
          .foreach(1 to 100) { _ =>
            ZIO.foreachPar(1 to 100)(ZIO.succeed(_)).map(_ == (1 to 100))
          }
          .map(_.forall(identity))
        assertZIO(zio)(isTrue)
      },
      test("foreachPar preserves ordering") {
        for {
          values <- ZIO.foreachPar(1 to 100)(ZIO.succeed(_))
        } yield assert(values)(equalTo(1 to 100))
      } @@ TestAspect.nonFlaky(100),
      test("foreachPar preserves ordering") {
        assertZIO(ZIO.foreachPar(1 to 100)(ZIO.succeed(_)))(
          equalTo(1 to 100),
        )
      } @@ TestAspect.nonFlaky(100) @@ TestAspect.jvmOnly @@ TestAspect.timeout(60.seconds),
      test("integer addition is associative") {
        check(Gen.int, Gen.int, Gen.int) { (x, y, z) =>
          val left  = (x + y) + z
          val right = x + (y + z)
          assert(left)(equalTo(right))
        }
      } @@ TestAspect.samples(100),
      test("every boolean permutation") {
        val booleans: Gen[Any, Boolean] =
          Gen.fromIterable(List(true, false))

        checkAll(booleans, booleans, booleans) { (x, y, z) =>
          val left  = (x && y) && z
          val right = x && (y && z)
          assert(left)(equalTo(right))
        }
      },
    ),
  )

}

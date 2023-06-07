package com.carvana

import zio.*

object Main extends ZIOAppDefault {

  private val program = for {
    _ <- ZIO.logInfo("Starting server")
    _ <- ZIO.never
  } yield ExitCode.success

  override def run: ZIO[Any, Throwable, ExitCode] =
    program

}

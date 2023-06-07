import sbt.Keys._
import sbt.{Def, _}

object Dependencies {

  object Versions {
    val carvanaZio           = "2.2.33"
    val zioLogging           = "2.1.9"
    val caliban              = "2.1.0"
    val logback              = "1.4.5"
    val logbackEncoder       = "7.3"
    val tapir                = "1.2.12"
    val zio                  = "2.0.13"
    val zioHttp              = "0.0.5"
    val zioMetricsConnectors = "2.0.7"
  }

  object Compile {

    val carvanaZio: Seq[ModuleID] = Seq(
      "com.carvana" %% "zio-core"          % Versions.carvanaZio,
      "com.carvana" %% "zio-core-internal" % Versions.carvanaZio,
      "com.carvana" %% "zio-core-db"       % Versions.carvanaZio,
    )

    val caliban: Seq[ModuleID] = Seq(
      "com.github.ghostdogpr" %% "caliban"            % Versions.caliban,
      "com.github.ghostdogpr" %% "caliban-federation" % Versions.caliban,
      "com.github.ghostdogpr" %% "caliban-tapir"      % Versions.caliban,
      "com.github.ghostdogpr" %% "caliban-zio-http"   % Versions.caliban,
    )

    val tapir: Seq[ModuleID] = Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-json-zio"          % Versions.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"      % Versions.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % Versions.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server"   % Versions.tapir,
      "com.softwaremill.sttp.tapir" %% "tapir-zio-metrics"       % Versions.tapir,
    )

    val logging: Seq[ModuleID] = Seq(
      "ch.qos.logback"       % "logback-classic"          % Versions.logback,
      "net.logstash.logback" % "logstash-logback-encoder" % Versions.logbackEncoder,
    )

    val zioTest: Seq[ModuleID] = Seq(
      "dev.zio" %% "zio-test" % Versions.zio % "it, test",
      "dev.zio" %% "zio-test-sbt" % Versions.zio % "it, test",
      "dev.zio" %% "zio-test-magnolia" % Versions.zio % "it, test",
    )

  }

  import Compile._

  lazy val allDependencies: Def.Setting[Seq[ModuleID]] =
    libraryDependencies ++= carvanaZio ++ caliban ++ tapir ++ logging ++ zioTest

}

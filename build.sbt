inThisBuild(
  List(
    organization      := "com.carvana",
    scalaVersion      := "2.13.10",
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Ymacro-annotations", "-Xsource:3", "-Ywarn-unused"),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
  ),
)

Test / run / fork := true

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    name                 := "scala-project-template",
    Defaults.itSettings,
    Dependencies.allDependencies,
    executableScriptName := "server",
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  )
  .enablePlugins(JavaAppPackaging)

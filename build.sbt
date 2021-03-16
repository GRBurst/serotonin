name := "serotonin"

scalaVersion in ThisBuild := "2.13.4"

enablePlugins(ScalaJSBundlerPlugin)
scalaJSUseMainModuleInitializer := true

useYarn := true

resolvers += ("jitpack" at "https://jitpack.io")

libraryDependencies ++= (
  "com.github.fdietze.vectory" %%% "vectory" % "7780239164" ::
  "org.akka-js" %%% "akkajsactor" % "2.2.6.9" ::
  "com.github.fdietze.scala-js-d3v4" %%% "scala-js-d3v4" % "be15edec" ::
  Nil
)

scalacOptions in ThisBuild ++= (
  "-unchecked" ::
  "-deprecation" ::
  "-feature" ::
  "-language:_" ::
  Nil
)

Global / onChangedBuildSource := ReloadOnSourceChanges

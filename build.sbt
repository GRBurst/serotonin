name := "serotonin"

scalaVersion in ThisBuild := "2.12.0"

lazy val root = project.in(file(".")).
  aggregate(serotoninJS, serotoninJVM).
  settings(
    publish := {},
    publishLocal := {}
  )

lazy val serotonin = crossProject.in(file(".")).
  settings(
    name := "serotonin",
    version := "0.1-SNAPSHOT",
    libraryDependencies ++= (
      "com.github.fdietze" %%% "pharg" % "0.1.0-SNAPSHOT" ::
      "com.github.fdietze" %%% "vectory" % "0.1.0-SNAPSHOT" ::
      Nil
    )
  )
  .jvmSettings(
    libraryDependencies ++= (
      "org.scala-js" %% "scalajs-stubs" % "0.6.13" % "provided" ::
      "com.typesafe.akka" %% "akka-actor" % "2.4.12" ::
      Nil
    )
  )
  .jsSettings(
    libraryDependencies ++= (
      "eu.unicredit" %%% "akkajsactor" % "0.2.4.12" ::
      "org.singlespaced" %%% "scalajs-d3" % "0.3.4" ::
      Nil
    ),
    persistLauncher := true
  )

lazy val serotoninJVM = serotonin.jvm
lazy val serotoninJS = serotonin.js.enablePlugins(WorkbenchPlugin)

scalacOptions in ThisBuild ++= (
  "-unchecked" ::
  "-deprecation" ::
  "-feature" ::
  "-language:_" ::
  Nil
)

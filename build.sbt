name := "serotonin"

scalaVersion := "2.12.0"

libraryDependencies ++= (
  "com.typesafe.akka" %% "akka-actor" % "2.4.12" ::
  "com.github.fdietze" %% "pharg" % "0.1.0-SNAPSHOT" ::
  Nil
)

// ctrl+c only cancels run
// cancelable in Global := true

scalacOptions ++= (
  "-unchecked" ::
  "-deprecation" ::
  "-feature" ::
  "-language:_" ::
  Nil
)

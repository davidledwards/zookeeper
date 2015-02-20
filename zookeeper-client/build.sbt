name := "zookeeper-client"

organization := "com.loopfor.zookeeper"

version := "1.3"

description := "Scala API for ZooKeeper"

homepage := Some(url("https://github.com/davidledwards/zookeeper"))

licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

scmInfo := Some(ScmInfo(
  url("https://github.com/davidledwards/zookeeper/tree/release-" + version.value + "/zookeeper-client"),
  "scm:git:https://github.com/davidledwards/zookeeper.git",
  Some("scm:git:https://github.com/davidledwards/zookeeper.git")
))

scalaVersion := "2.11.5"

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-encoding", "UTF-8"
)

javacOptions ++= Seq(
  "-source", "1.6",
  "-target", "1.6"
)

// Compile dependencies.
libraryDependencies ++= Seq(
  "org.apache.zookeeper" % "zookeeper" % "3.4.6"
    exclude("jline", "jline"),
  "log4j" % "log4j" % "1.2.16"
    exclude("javax.jms", "jms")
)

// Test dependencies.
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

publishMavenStyle := true

publishArtifact in Test := false

publishTo := Some(
  if (version.value endsWith "SNAPSHOT")
    "Sonatype Nexus Snapshot Repository" at "https://oss.sonatype.org/content/repositories/snapshots/"
  else
    "Sonatype Nexus Release Repository" at "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
)

// Ensures that published POM has no repository dependencies.
pomIncludeRepository := { _ => false }

pomExtra := (
  <developers>
    <developer>
      <id>davidledwards</id>
      <name>David Edwards</name>
      <email>david.l.edwards@gmail.com</email>
    </developer>
  </developers>
)

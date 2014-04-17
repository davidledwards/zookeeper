name := "zookeeper-cli"

organization := "com.loopfor.zookeeper"

version := "1.2"

description := "ZooKeeper CLI"

homepage := Some(url("https://github.com/davidledwards/zookeeper"))

licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

scmInfo := Some(ScmInfo(
  url("https://github.com/davidledwards/zookeeper/tree/release-" + version.value + "/zookeeper-cli"),
  "scm:git:https://github.com/davidledwards/zookeeper.git",
  Some("scm:git:https://github.com/davidledwards/zookeeper.git")
))

scalaVersion := "2.10.4"

// Disable since distributable artifact is a final assembly.
crossPaths := false

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-encoding", "UTF-8"
)

javacOptions ++= Seq(
  "-source", "1.7",
  "-target", "1.7"
)

// Compile dependencies.
libraryDependencies ++= Seq(
  "com.loopfor.zookeeper" %% "zookeeper-client" % "1.2",
  "com.loopfor.scalop" %% "scalop" % "1.1",
  "jline" % "jline" % "2.11",
  "log4j" % "log4j" % "1.2.16" % "runtime"
    exclude("javax.jms", "jms")
)

// Test dependencies.
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.1.3" % "test"
)

// Merges sbt-pack settings.
packSettings

packMain := Map("zk" -> "com.loopfor.zookeeper.cli.CLI")

publishMavenStyle := true

publishArtifact in Test := false

// Publishes the final assembly.
artifact in (Compile, packArchive) := Artifact(name.value, "tar", "tar.gz")

addArtifact(artifact in (Compile, packArchive), packArchive)

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

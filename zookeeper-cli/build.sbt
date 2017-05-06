lazy val compilerSettings = Seq(
  scalaVersion := "2.12.2",
  scalacOptions ++= Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-unchecked",
    "-feature",
    "-encoding", "UTF-8"
  )
)

lazy val dependencySettings = Seq(
  libraryDependencies ++= Seq(
    "com.loopfor.zookeeper" %% "zookeeper-client" % "1.4",
    "com.loopfor.scalop" %% "scalop" % "2.2",
    "jline" % "jline" % "2.14.2",
    "log4j" % "log4j" % "1.2.16" % "runtime"
      exclude("javax.jms", "jms"),
    "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  )
)

lazy val packageSettings = packSettings ++ Seq(
  crossPaths := false,
  packMain := Map("zk" -> "com.loopfor.zookeeper.cli.CLI"),
  artifact in (Compile, packArchive) := Artifact(name.value, "tar", "tar.gz")
)

lazy val publishSettings = publishPackArchives ++ Seq(
  pomIncludeRepository := { _ => false },
  pomExtra :=
    <developers>
      <developer>
        <id>davidledwards</id>
        <name>David Edwards</name>
        <email>david.l.edwards@gmail.com</email>
      </developer>
    </developers>,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  publishTo := Some(
    if (version.value endsWith "SNAPSHOT")
      "Sonatype Nexus Snapshot Repository" at "https://oss.sonatype.org/content/repositories/snapshots/"
    else
      "Sonatype Nexus Release Repository" at "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
  )
)

lazy val eclipseSettings = {
  import EclipseKeys._
  Seq(
    executionEnvironment := Some(EclipseExecutionEnvironment.JavaSE18)
  )
}

lazy val rootProject = (project in file(".")).
  settings(
    name := "zookeeper-cli",
    organization := "com.loopfor.zookeeper",
    version := "1.4",
    description := "ZooKeeper CLI",
    homepage := Some(url("https://github.com/davidledwards/zookeeper")),
    licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo := Some(ScmInfo(
      url("https://github.com/davidledwards/zookeeper/tree/release-" + version.value + "/zookeeper-cli"),
      "scm:git:https://github.com/davidledwards/zookeeper.git",
      Some("scm:git:https://github.com/davidledwards/zookeeper.git")
    ))
  ).
  settings(compilerSettings: _*).
  settings(dependencySettings: _*).
  settings(packageSettings: _*).
  settings(publishSettings: _*).
  settings(eclipseSettings: _*)

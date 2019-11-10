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
    "org.apache.zookeeper" % "zookeeper" % "3.4.10"
      exclude("jline", "jline"),
    "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  )
)

lazy val docSettings = Seq(
  scalacOptions in (Compile, doc) ++= Seq("-no-link-warnings"),
  autoAPIMappings := true,
  apiURL := Some(url("https://davidedwards.io/zookeeper/api/1.4/"))
)

lazy val publishSettings = Seq(
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
    name := "zookeeper-client",
    organization := "com.loopfor.zookeeper",
    version := "1.4.1",
    description := "Scala API for ZooKeeper",
    homepage := Some(url("https://github.com/davidledwards/zookeeper")),
    licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo := Some(ScmInfo(
      url("https://github.com/davidledwards/zookeeper/tree/release-" + version.value + "/zookeeper-client"),
      "scm:git:https://github.com/davidledwards/zookeeper.git",
      Some("scm:git:https://github.com/davidledwards/zookeeper.git")
    ))
  ).
  settings(compilerSettings: _*).
  settings(dependencySettings: _*).
  settings(docSettings: _*).
  settings(publishSettings: _*).
  settings(eclipseSettings: _*)

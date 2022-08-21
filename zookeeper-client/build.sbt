/*
 * Copyright 2020 David Edwards
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
lazy val compilerSettings = Seq(
  scalaVersion := "2.13.8",
  scalacOptions ++= Seq(
    "-target:11",
    "-deprecation",
    "-unchecked",
    "-feature",
    "-encoding", "UTF-8"
  )
)

lazy val dependencySettings = Seq(
  libraryDependencies ++= Seq(
    // Compile
    "org.apache.zookeeper" % "zookeeper" % "3.8.0" exclude("jline", "jline"),

    // Test
    "org.scalatest" %% "scalatest" % "3.2.13" % "test",
    "io.dropwizard.metrics" % "metrics-core" % "3.2.5" % "test" exclude("org.slf4j", "slf4j-api"),
    "org.xerial.snappy" % "snappy-java" % "1.1.7"
  )
)

lazy val docSettings = Seq(
  Compile / doc / scalacOptions ++= Seq("-no-link-warnings"),
  autoAPIMappings := true,
  apiURL := Some(url(s"https://davidedwards.io/zookeeper/api/${version.value}/"))
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
  Test / publishArtifact := false,
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
    executionEnvironment := Some(EclipseExecutionEnvironment.JRE11)
  )
}

lazy val rootProject = (project in file(".")).
  settings(
    name := "zookeeper-client",
    organization := "com.loopfor.zookeeper",
    version := "1.6-SNAPSHOT",
    description := "Scala API for ZooKeeper",
    homepage := Some(url("https://github.com/davidledwards/zookeeper")),
    licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo := Some(ScmInfo(
      url(s"https://github.com/davidledwards/zookeeper/tree/release-${version.value}/zookeeper-client"),
      "scm:git:https://github.com/davidledwards/zookeeper.git",
      Some("scm:git:https://github.com/davidledwards/zookeeper.git")
    ))
  ).
  settings(compilerSettings: _*).
  settings(dependencySettings: _*).
  settings(docSettings: _*).
  settings(publishSettings: _*).
  settings(eclipseSettings: _*)

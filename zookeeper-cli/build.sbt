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
enablePlugins(PackPlugin)
import xerial.sbt.pack.PackPlugin._

lazy val compilerSettings = Seq(
  scalaVersion := "3.2.0",
  scalacOptions ++= Seq(
    "-release:11",
    "-deprecation",
    "-unchecked",
    "-feature",
    "-encoding", "UTF-8"
  )
)

lazy val dependencySettings = Seq(
  libraryDependencies ++= Seq(
    // Compile
    "com.loopfor.zookeeper" %% "zookeeper-client" % "1.6.1",
    "com.loopfor.scalop" %% "scalop" % "2.3.1",
    "jline" % "jline" % "2.14.6",

    // Test
    "org.scalatest" %% "scalatest" % "3.2.13" % "test"
  )
)

lazy val packageSettings = packSettings ++ Seq(
  crossPaths := false,
  packMain := Map("zk" -> "com.loopfor.zookeeper.cli.CLI"),
  Compile / packArchive / artifact := Artifact(name.value, "tar", "tar.gz")
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
  Test / publishArtifact := false,
  publishTo := Some(
    if (version.value endsWith "SNAPSHOT")
      "Sonatype Nexus Snapshot Repository" at "https://oss.sonatype.org/content/repositories/snapshots/"
    else
      "Sonatype Nexus Release Repository" at "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
  )
)

lazy val rootProject = (project in file(".")).
  settings(
    name := "zookeeper-cli",
    organization := "com.loopfor.zookeeper",
    version := "1.6",
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
  settings(publishSettings: _*)

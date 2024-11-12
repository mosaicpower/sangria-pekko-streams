import xerial.sbt.Sonatype.sonatypeCentralHost

name := "sangria-pekko-streams"
version := "0.1.0"
versionScheme := Some("semver-spec")
organization := "com.mosaicpower"
description := "Sangria pekko-streams integration"
homepage := Some(url("https://sangria-graphql.github.io/"))
licenses := Seq(
  "Apache License, ASL Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
scmInfo := Some(
  ScmInfo(
    browseUrl = url("https://github.com/mosaicpower/sangria-pekko-streams"),
    connection = "scm:git@github.com:mosaicpower/sangria-pekko-streams.git"
  ))

developers :=
  List(
    Developer("OlegIlyenko", "Oleg Ilyenko", "", url("https://github.com/OlegIlyenko")),
    Developer(
      "bmironenko",
      "Basil Mironenko",
      "basilm@mosaicpower.com",
      url("https://mosaicpower.com/")
    )
  )

crossScalaVersions := Seq("2.13.15", "3.5.2")
scalaVersion := crossScalaVersions.value.last

sonatypeProfileName := "com.mosaicpower"
sonatypeCredentialHost := sonatypeCentralHost
publishConfiguration := publishConfiguration.value.withOverwrite(true)
publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
publishTo := sonatypePublishToBundle.value
publishArtifact := true
publishMavenStyle := true

Compile / scalacOptions := scalaVersion.map { v: String =>
  val default = Seq("-deprecation", "-unchecked", "-feature")
  if (v.startsWith("3")) default else default ++ Seq("-Ywarn-dead-code", "-Xsource:3")
}.value

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria-streaming-api" % "1.0.3",
  "org.apache.pekko" %% "pekko-stream" % "1.1.2",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

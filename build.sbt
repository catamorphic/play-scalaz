import bintray.Keys._

releaseSettings

scalaVersion := "2.10.3"

bintrayPublishSettings

repository in bintray := "public"

organization := "com.catamorphic"

name := "playz"

bintrayOrganization in bintray := Some("catamorphic")

libraryDependencies ++= Seq(
      "org.scalaz" % "scalaz-core_2.10" % "7.0.0",
      "org.scalaz" % "scalaz-concurrent_2.10" % "7.0.0",
      "com.typesafe.play" %% "play" % "2.2.1"
      )      

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Catamorphic Public" at "http://dl.bintray.com/catamorphic/public",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
)

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
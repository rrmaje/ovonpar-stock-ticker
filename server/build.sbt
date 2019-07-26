name := "parity-ticker"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.4"

val parityVersion = "0.7.1-SNAPSHOT"
val nassauVersion = "0.13.0"

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
  "com.paritytrading.foundation" % "foundation"    % "0.2.1",
  "com.paritytrading.parity"     % "parity-net"    % parityVersion,
  "com.paritytrading.parity"     % "parity-book"   % parityVersion,
  "com.paritytrading.parity"     % "parity-util"   % parityVersion,
  "com.paritytrading.nassau"     % "nassau-util"   % nassauVersion,
  "org.jvirtanen.config"         % "config-extras" % "0.1.0"
)

libraryDependencies += guice

dockerBaseImage := "openjdk:jre"
lazy val root = (project in file(".")).enablePlugins(PlayScala)

name := "parity-ticker"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.8"

val parityVersion = "0.7.1-SNAPSHOT"
val nassauVersion = "0.13.0"

updateOptions := updateOptions.value.withLatestSnapshots(false)

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
  "com.paritytrading.foundation" % "foundation"    % "0.2.1",
  "com.paritytrading.parity"     % "parity-net"    % parityVersion,
  "com.paritytrading.parity"     % "parity-book"   % parityVersion,
  "com.paritytrading.parity"     % "parity-util"   % parityVersion,
  "com.paritytrading.nassau"     % "nassau-util"   % nassauVersion,
  "org.jvirtanen.config"         % "config-extras" % "0.1.0",
  "io.seruco.encoding" % "base62" % "0.1.2",
  "org.bouncycastle" % "bcprov-jdk15on" % "1.60"
)

libraryDependencies += guice
libraryDependencies += ws
libraryDependencies ++= Seq(
  jdbc,
  "org.playframework.anorm" %% "anorm" % "2.6.2"
)
libraryDependencies += "org.postgresql" % "postgresql" % "9.4-1200-jdbc41" exclude("org.slf4j", "slf4j-simple")

lazy val root = (project in file(".")).enablePlugins(PlayScala)

name := "spellChecker"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq("org.jsoup" % "jsoup" % "1.11.2",
  "org.apache.pdfbox" % "pdfbox" % "2.0.19",
  "org.apache.httpcomponents" % "httpclient" % "4.5.12",
  "org.skife.com.typesafe.config" % "typesafe-config" % "0.3.0",
  "org.scalatest" %% "scalatest" % "3.1.1" % "test",
  "org.apache.logging.log4j" %% "log4j-api-scala" % "11.0",
  "org.apache.logging.log4j" % "log4j-api" % "2.11.0",
  "org.apache.logging.log4j" % "log4j-core" % "2.11.0" % Runtime,
  "org.scoverage" %% "scalac-scoverage-runtime" % "1.4.1"
)

lazy val commonSettings = Seq(
  version := "0.1-SNAPSHOT",
  organization := "com.example",
  scalaVersion := "2.12.8",
  test in assembly := {}
)

lazy val app = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "fat-jar-Spell-Checker"
  )

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "eclipse.inf") => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
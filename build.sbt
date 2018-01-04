
lazy val root = (project in file("."))
  .aggregate(core, persistenceQuery, sample)
  .settings(skip in publish := true)

lazy val core = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "akka-persistence-sql-async"
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val persistenceQuery = (project in file("persistence-query"))
  .settings(commonSettings: _*)
  .settings(skip in publish := true)
  .settings(
    name := "akka-persistence-query-sql-async",
    libraryDependencies ++= persistenceQueryDependencies
  )
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)

lazy val performanceTest = (project in file("performance-test"))
  .settings(commonSettings: _*)
  .settings(skip in publish := true)
  .settings(
    name := "akka-persistence-sql-async-performance-test"
  )
  .dependsOn(
    core,
    core % "test->test"
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val sample = (project in file("sample"))
  .settings(commonSettings: _*)
  .settings(skip in publish := true)
  .settings(
    name := "akka-persistence-sql-async-sample",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % "2.6.6"
    )
  )
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)

lazy val Scala211 = "2.11.11"
lazy val Scala212 = "2.12.4"

lazy val commonSettings = Seq(
  organization := "com.okumin",
  version := "0.5.1",
  scalaVersion := Scala212,
  crossScalaVersions := Seq(Scala211, Scala212),
  parallelExecution in Test := false,
  libraryDependencies := commonDependencies,
  scalacOptions ++= Seq(
    "-deprecation"
  ),
  // sbt-header settings
  organizationName := "okumin.com",
  startYear := Some(2014),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))
)

val akkaVersion = "2.5.6"
val mauricioVersion = "0.2.21"

lazy val commonDependencies = Seq(
  "com.typesafe.akka"   %% "akka-actor"           % akkaVersion,
  "com.typesafe.akka"   %% "akka-persistence"     % akkaVersion,
  "org.scalikejdbc"     %% "scalikejdbc-async"    % "0.9.0",
  "com.github.mauricio" %% "mysql-async"          % mauricioVersion % "provided",
  "com.github.mauricio" %% "postgresql-async"     % mauricioVersion % "provided",
  "com.typesafe.akka"   %% "akka-persistence-tck" % akkaVersion     % "test",
  "com.typesafe.akka"   %% "akka-slf4j"           % akkaVersion     % "test",
  "com.typesafe.akka"   %% "akka-testkit"         % akkaVersion     % "test",
  "org.slf4j"            % "slf4j-log4j12"        % "1.7.25"        % "test"
)

lazy val persistenceQueryDependencies = Seq(
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := {
    <url>https://github.com/okumin/akka-persistence-sql-async</url>
    <licenses>
      <license>
        <name>Apache 2 License</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:okumin/akka-persistence-sql-async.git</url>
      <connection>scm:git:git@github.com:okumin/akka-persistence-sql-async.git</connection>
    </scm>
    <developers>
      <developer>
        <id>okumin</id>
        <name>okumin</name>
        <url>http://okumin.com/</url>
      </developer>
    </developers>
  }
)

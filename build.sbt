
lazy val root = (project in file("."))
  .aggregate(core, persistenceQuery, sample)
  .settings(commonSettings: _*)
  .settings(publish / skip := true)

lazy val core = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "akka-persistence-sql-async"
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val persistenceQuery = (project in file("persistence-query"))
  .settings(commonSettings: _*)
  .settings(publish / skip := true)
  .settings(
    name := "akka-persistence-query-sql-async",
    libraryDependencies ++= persistenceQueryDependencies
  )
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)

lazy val performanceTest = (project in file("performance-test"))
  .settings(commonSettings: _*)
  .settings(publish / skip := true)
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
  .settings(publish / skip := true)
  .settings(
    name := "akka-persistence-sql-async-sample",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % "2.9.0"
    )
  )
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)

lazy val Scala212 = "2.12.11"
lazy val Scala213 = "2.13.2"

lazy val commonSettings = Seq(
  organization := "com.github.andriimartynov",
  version := "0.6.1",
  scalaVersion := Scala212,
  crossScalaVersions := Seq(Scala212, Scala213),
  parallelExecution in Test := false,
  libraryDependencies := commonDependencies,
  scalacOptions ++= Seq(
    "-deprecation"
  ),
  // sbt-header settings
  startYear := Some(2014),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))
)

val akkaVersion = "2.5.31"
val jasyncVersion = "1.1.3"

lazy val commonDependencies = Seq(
  "com.typesafe.akka"     %% "akka-actor"           % akkaVersion,
  "com.typesafe.akka"     %% "akka-persistence"     % akkaVersion,
  "org.scalikejdbc"       %% "scalikejdbc-async"    % "0.13.0",
  "com.github.jasync-sql" %  "jasync-mysql"         % jasyncVersion   % "provided",
  "com.github.jasync-sql" %  "jasync-postgresql"    % jasyncVersion   % "provided",
  "com.typesafe.akka"     %% "akka-persistence-tck" % akkaVersion     % "test",
  "com.typesafe.akka"     %% "akka-slf4j"           % akkaVersion     % "test",
  "com.typesafe.akka"     %% "akka-testkit"         % akkaVersion     % "test",
  "org.slf4j"             % "slf4j-log4j12"         % "1.7.25"        % "test"
)

lazy val persistenceQueryDependencies = Seq(
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := {
    <url>https://github.com/andriimartynov/akka-persistence-sql-async</url>
      <licenses>
        <license>
          <name>Apache 2 License</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:andriimartynov/akka-persistence-sql-async.git</url>
        <connection>scm:git:git@github.com:andriimartynov/akka-persistence-sql-async.git</connection>
      </scm>
      <developers>
        <developer>
          <id>andriimartynov</id>
          <name>andriimartynov</name>
          <url>https://github.com/andriimartynov</url>
        </developer>
      </developers>
  }
)

credentials += Credentials(
  "GnuPG Key ID",
  "gpg",
  sys.env.getOrElse("GPG_PUBLIC_KEY", ""), // key identifier
  "ignored" // this field is ignored; passwords are supplied by pinentry
)
name := "constellation"
version := "0.1"
scalaVersion := "2.13.12"

scalacOptions ++= Seq(
  "-language:reflectiveCalls",
  "-deprecation",
  "-feature"
)

// SNAPSHOT repositories
libraryDependencies ++= Seq( "edu.berkeley.cs" %% "rocketchip" % "1.6",
    "edu.berkeley.cs" %% "cde" % "1.6",
    "edu.berkeley.cs" %% "rocketmacros" % "1.6",
    "edu.berkeley.cs" %% "chiseltest" % "6.0.0" % "test" ,
    "org.chipsalliance" %% "chisel" % "6.5.0"
)

addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "6.5.0" cross CrossVersion.full)

import Tests._

Test / fork := true
Test / testGrouping := (Test / testGrouping).value.flatMap { group =>
   group.tests.map { test =>
      Group(test.name, Seq(test), SubProcess(ForkOptions()))
   }
}
concurrentRestrictions := Seq(Tags.limit(Tags.ForkedTestGroup, 72))


// ============================================
// PROJECT BASIC CONFIGURATION
// ============================================
ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "2.12.17"
ThisBuild / organization := "edu.krlos"

// ============================================
// SBT PLUGIN CONFIGURATION
// ============================================
lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    // Plugin name and basic configuration
    name := "flatten-code-plugin",
    sbtPlugin := true,
    
    // Plugin metadata
    description := "Plugin to flatten code in Scala projects",
    licenses := Seq("Creative Commons Attribution-NonCommercial-ShareAlike 4.0**" -> url("https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode.txt")),
    
    // Minimum supported SBT version
    pluginCrossBuild / sbtVersion := "1.5.0",
    
    // Publishing configurations
    publishMavenStyle := true,
    Test / publishArtifact := false,
    
    // ============================================
    // DEVELOPMENT AND TESTING DEPENDENCIES
    // ============================================
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.15" % Test
    )
  )

// ============================================
// EXAMPLE USAGE IN CLIENT PROJECTS
// ============================================
// Add this to your project's build.sbt to use the plugin:
//
// flattenInput := file("src/main/scala/Player.scala"),
// flattenOutput := file("target/flattened-output.scala"),
// flattenLogLevel := "DEBUG"  // Optional: DEBUG, INFO, WARN, ERROR (default: INFO)
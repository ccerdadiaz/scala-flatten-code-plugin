import sbt._
import Keys._
import scala.io.Source
import java.io.File
import scala.collection.mutable

object FlattenCode extends AutoPlugin {
  
  // The plugin is enabled automatically in all projects
  override def trigger = allRequirements
  
  // AutoImport keys. Configuration
  object autoImport {
    // Main command that we will execute with ~flattenCode
    val flattenCode = taskKey[Unit]("Flattens code by removing packages and copying dependencies")
    val flattenInput = settingKey[File]("Input file for flattening")
    val flattenOutput = settingKey[File]("Output file for flattened result")
  }
  
  import autoImport._
  
  // Settings that will be applied automatically when using the plugin
  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    
    // Files SBT should watch for 
    flattenCode / watchSources := {
      val baseDir = (ThisBuild / baseDirectory).value
      val srcDir = baseDir / "src" / "main" / "scala"
      if (srcDir.exists()) {
        Seq(WatchSource(srcDir))
      } else {
        Seq.empty
      }
    },
    
    // Implementation of the flattenCode task
    flattenCode := {
      val inputFile = flattenInput.value
      val outputFile = flattenOutput.value
      val log = streams.value.log
      val baseDir = (ThisBuild / baseDirectory).value
      val srcDir = baseDir / "src" / "main" / "scala"
      
      // Verify that the directory exists
      if (!srcDir.exists()) {
        log.warn(s"[FlattenCode] Directory ${srcDir.getAbsolutePath} does not exist")
        log.info(s"[FlattenCode] Creating directory: ${srcDir.getAbsolutePath}")
        srcDir.mkdirs()
      }
      
      // Simply perform the flattening 
      performFlattening(srcDir, inputFile, outputFile, log)
    }
  )
  
  /**
   * Takes all owned source code and put in a
   * a single scala file removing packages and includes
   */
  private def performFlattening(srcDir: File, inputFile: File, outputFile: File, log: Logger): Unit = {
    try {
      if (!inputFile.exists()) {
        throw new RuntimeException(s"[FlattenCode] Input file ${inputFile.getPath} does not exist")
      }
      
      log.info(s"[FlattenCode] from: ${inputFile.getName}")
      
      val flattener = new CodeFlattener(Seq(srcDir), log)
      val result = flattener.flatten(inputFile)
      
      // Ensure output directory exists
      outputFile.getParentFile.mkdirs()
      IO.write(outputFile, result)
      
      log.info(s"[FlattenCode] into: ${outputFile.getName}")
      
    } catch {
      case ex: Exception =>
        log.error(s"[FlattenCode] ❌ Error during flattening: ${ex.getMessage}")
        throw ex
    }
  }
}

class CodeFlattener(sourceDirs: Seq[File], log: Logger) {
  
  private val allSourceFiles = mutable.Map[String, (File, String)]()
  private val localClassNames = mutable.Set[String]()
  
  def flatten(inputFile: File): String = {
    log.debug("Loading all source files...")
    loadAllSourceFiles()
    
    log.debug("Building local class index...")
    buildLocalClassIndex()

    val inputContent = Source.fromFile(inputFile).getLines().mkString("\n")
    val mainContent = removePackagesAndImports(inputContent)

    val output = mutable.ListBuffer[String]()
    val alreadyProcessed = mutable.Set[String]()

    def addDependenciesRecursively(content: String): Unit = {
      val importedClasses = findImportedClasses(content)
      
      importedClasses.foreach { className =>
        if (localClassNames.contains(className)) {
          findFileContainingClass(className) match {
            case Some((file, depContent)) if !alreadyProcessed.contains(file.getPath) =>
              alreadyProcessed += file.getPath
              val cleanContent = removePackagesAndImports(depContent)
              output += cleanContent
              log.debug(s"Added dependency: ${file.getName} for class $className")
              addDependenciesRecursively(depContent)
            case _ =>
          }
        }
      }
    }

    output += mainContent
    alreadyProcessed += inputFile.getPath
    addDependenciesRecursively(inputContent)

    log.debug(s"Flattening complete. Included ${alreadyProcessed.size} files")
    output.mkString("\n\n")
  }
  
  // Public method for testing
  def extractClassDefinitions(content: String): Set[String] = {
    val classes = mutable.Set[String]()
    val lines = content.split("\n")
    
    val patterns = List(
      """case class\s+(\w+)""".r,
      """class\s+(\w+)""".r,
      """object\s+(\w+)""".r,
      """trait\s+(\w+)""".r
    )
    
    lines.foreach { line =>
      val trimmed = line.trim
      if (!trimmed.startsWith("package ") && !trimmed.startsWith("import ")) {
        patterns.foreach { pattern =>
          pattern.findFirstMatchIn(trimmed) match {
            case Some(m) => classes += m.group(1)
            case None =>
          }
        }
      }
    }
    
    classes.toSet
  }
  
  private def loadAllSourceFiles(): Unit = {
    sourceDirs.foreach { dir =>
      if (dir.exists() && dir.isDirectory) {
        val scalaFiles = findScalaFiles(dir)
        log.debug(s"Found ${scalaFiles.length} Scala files in ${dir.getPath}")
        scalaFiles.foreach { file =>
          val content = Source.fromFile(file).getLines().mkString("\n")
          allSourceFiles(file.getPath) = (file, content)
        }
      }
    }
    log.debug(s"Loaded ${allSourceFiles.size} source files")
  }
  
  private def buildLocalClassIndex(): Unit = {
    allSourceFiles.values.foreach { case (_, content) =>
      val classNames = extractClassDefinitions(content)
      localClassNames ++= classNames
    }
    log.debug(s"Local class names found: ${localClassNames.mkString(", ")}")
  }
  
  private def findScalaFiles(dir: File): List[File] = {
    val files = mutable.ListBuffer[File]()
    
    def traverse(f: File): Unit = {
      if (f.isDirectory) {
        Option(f.listFiles()).foreach(_.foreach(traverse))
      } else if (f.getName.endsWith(".scala")) {
        files += f
      }
    }
    
    traverse(dir)
    files.toList
  }
  
  private def removePackagesAndImports(content: String): String = {
    val lines = content.split("\n")
    val filteredLines = lines.filterNot { line =>
      val trimmed = line.trim
      if (trimmed.startsWith("package ")) {
        true
      } else if (trimmed.startsWith("import ")) {
        // Check if this import line contains any local classes
        shouldRemoveImport(trimmed)
      } else {
        false
      }
    }
    filteredLines.mkString("\n")
  }
  
  private def shouldRemoveImport(importLine: String): Boolean = {
    val trimmed = importLine.trim
    log.debug(s"Checking import: $trimmed")
    
    // Check for multiple import pattern FIRST: import com.example.{Class1, Class2}  
    val multiImportPattern = """import\s+[\w.]*\.\{([^}]+)\}""".r
    multiImportPattern.findFirstMatchIn(trimmed) match {
      case Some(m) =>
        val classNames = m.group(1).split(",").map(_.trim)
        log.debug(s"Multiple import classes: ${classNames.mkString(", ")}")
        // Remove import if ANY of the classes is local
        val shouldRemove = classNames.exists(className => {
          val contains = localClassNames.contains(className)
          log.debug(s"Class '$className' is local: $contains")
          contains
        })
        log.debug(s"Multiple import shouldRemove: $shouldRemove")
        shouldRemove
      case None =>
        // Single import pattern: import com.example.ClassName
        val singleImportPattern = """import\s+[\w.]*\.(\w+)""".r
        singleImportPattern.findFirstMatchIn(trimmed) match {
          case Some(m) => 
            val className = m.group(1)
            val shouldRemove = localClassNames.contains(className)
            log.debug(s"Single import '$className' -> shouldRemove: $shouldRemove")
            shouldRemove
          case None => 
            log.debug(s"No pattern matched for: $trimmed")
            false
        }
    }
  }
  
  private def findImportedClasses(content: String): Set[String] = {
    val imports = mutable.Set[String]()
    val lines = content.split("\n")
    
    lines.foreach { line =>
      val trimmed = line.trim
      if (trimmed.startsWith("import ")) {
        // Handle single import: import com.example.ClassName
        val singleImportPattern = """import\s+[\w.]*\.(\w+)""".r
        singleImportPattern.findFirstMatchIn(trimmed) match {
          case Some(m) => imports += m.group(1)
          case None =>
            // Handle multiple imports: import com.example.{Class1, Class2}
            val multiImportPattern = """import\s+[\w.]*\.\{([^}]+)\}""".r
            multiImportPattern.findFirstMatchIn(trimmed) match {
              case Some(m) =>
                val classNames = m.group(1).split(",").map(_.trim)
                imports ++= classNames
              case None =>
            }
        }
      }
    }
    
    imports.toSet
  }
  
  private def findFileContainingClass(className: String): Option[(File, String)] = {
    allSourceFiles.values.foreach { case (file, content) =>
      if (containsClassDefinition(content, className)) {
        return Some((file, content))
      }
    }
    None
  }
  
  private def containsClassDefinition(content: String, className: String): Boolean = {
    val lines = content.split("\n")
    
    val patterns = List(
      s"""\\bcase class\\s+$className\\b""",
      s"""\\bclass\\s+$className\\b""",
      s"""\\bobject\\s+$className\\b""",
      s"""\\btrait\\s+$className\\b"""
    )
    
    lines.exists { line =>
      val trimmed = line.trim
      if (!trimmed.startsWith("package ") && !trimmed.startsWith("import ")) {
        patterns.exists { pattern =>
          val regex = pattern.r
          regex.findFirstIn(trimmed).isDefined
        }
      } else {
        false
      }
    }
  }
}
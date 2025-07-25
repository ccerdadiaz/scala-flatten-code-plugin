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
  // NUEVO: Mapeo de paquete declarado -> archivos que lo contienen
  private val packageToFiles = mutable.Map[String, mutable.Set[File]]()
  
  def flatten(inputFile: File): String = {
    log.debug("Loading all source files...")
    loadAllSourceFiles()
    
    log.debug("Building package index...")
    buildPackageIndex()
    
    log.debug("Building local class index...")
    buildLocalClassIndex()

    val inputContent = Source.fromFile(inputFile).getLines().mkString("\n")
    val mainContent = removePackagesAndImports(inputContent)

    val output = mutable.ListBuffer[String]()
    val alreadyProcessed = mutable.Set[String]()

    // Añadir el archivo principal
    output += mainContent
    alreadyProcessed += inputFile.getPath

    // Procesar dependencias por nombre de clase (método existente)
    addDependenciesRecursively(inputContent, alreadyProcessed, output)
    
    // NUEVO: Procesar dependencias por wildcards
    addWildcardDependencies(inputContent, alreadyProcessed, output)

    log.debug(s"Flattening complete. Included ${alreadyProcessed.size} files")
    output.mkString("\n\n")
  }
  
  /**
   * Carga todos los archivos fuente disponibles
   */
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
  
  /**
   * Encuentra archivos .scala recursivamente
   */
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
  
  /**
   * Construye un índice de paquete -> archivos que declaran ese paquete
   */
  private def buildPackageIndex(): Unit = {
    allSourceFiles.values.foreach { case (file, content) =>
      extractPackageDeclaration(content) match {
        case Some(packageName) =>
          packageToFiles.getOrElseUpdate(packageName, mutable.Set[File]()) += file
          log.debug(s"File ${file.getName} declares package: $packageName")
        case None =>
          log.debug(s"File ${file.getName} has no package declaration")
      }
    }
    
    log.debug(s"Package index built: ${packageToFiles.keys.mkString(", ")}")
  }
  
  /**
   * Construye índice de nombres de clases locales
   */
  private def buildLocalClassIndex(): Unit = {
    allSourceFiles.values.foreach { case (_, content) =>
      val classNames = extractClassDefinitions(content)
      localClassNames ++= classNames
    }
    log.debug(s"Local class names found: ${localClassNames.mkString(", ")}")
  }
  
  /**
   * Extrae la declaración de paquete de un archivo
   * "package a.b.geometry" -> Some("a.b.geometry")
   */
  private def extractPackageDeclaration(content: String): Option[String] = {
    val lines = content.split("\n")
    
    lines.foreach { line =>
      val trimmed = line.trim
      if (trimmed.startsWith("package ")) {
        val packagePattern = """package\s+([\w.]+)""".r
        packagePattern.findFirstMatchIn(trimmed) match {
          case Some(m) => return Some(m.group(1))
          case None =>
        }
      }
    }
    None
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
  
  /**
   * Encuentra imports con wildcards en el contenido
   * Retorna lista de paquetes con wildcard: Set("a.b.geometry", "a.b")
   */
  private def findWildcardImports(content: String): Set[String] = {
    val wildcards = mutable.Set[String]()
    val lines = content.split("\n")
    
    val wildcardPattern = """import\s+([\w.]+)\._""".r
    
    lines.foreach { line =>
      val trimmed = line.trim
      if (trimmed.startsWith("import ")) {
        wildcardPattern.findFirstMatchIn(trimmed) match {
          case Some(m) => 
            val packageName = m.group(1)
            wildcards += packageName
            log.debug(s"Found wildcard import: $packageName._")
          case None =>
        }
      }
    }
    
    wildcards.toSet
  }
  
  /**
   * Encuentra imports específicos de clases
   */
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
  
  /**
   * Encuentra todos los archivos que corresponden a un wildcard import
   * Para "a.b.geometry._" -> archivos que declaren "package a.b.geometry"
   * Para "a.b._" -> archivos que declaren "package a.b.geometry", "package a.b.generic_algo", etc.
   */
  private def findFilesForWildcardPackage(wildcardPackage: String): Set[File] = {
    val matchingFiles = mutable.Set[File]()
    
    packageToFiles.foreach { case (declaredPackage, files) =>
      if (isPackageMatch(declaredPackage, wildcardPackage)) {
        matchingFiles ++= files
        log.debug(s"Package '$declaredPackage' matches wildcard '$wildcardPackage._'")
      }
    }
    
    if (matchingFiles.isEmpty) {
      log.debug(s"No files found for wildcard import: $wildcardPackage._")
    }
    
    matchingFiles.toSet
  }
  
  /**
   * Verifica si un paquete declarado coincide con un wildcard import
   * isPackageMatch("a.b.geometry", "a.b.geometry") -> true (exacto)
   * isPackageMatch("a.b.geometry", "a.b") -> true (sub-paquete)
   * isPackageMatch("a.b.geometry", "a.c") -> false
   */
  private def isPackageMatch(declaredPackage: String, wildcardPackage: String): Boolean = {
    if (declaredPackage == wildcardPackage) {
      // Coincidencia exacta: import a.b.geometry._ con package a.b.geometry
      true
    } else if (declaredPackage.startsWith(wildcardPackage + ".")) {
      // Sub-paquete: import a.b._ con package a.b.geometry
      true
    } else {
      false
    }
  }
  
  /**
   * Incluye todos los archivos referenciados por imports wildcards
   */
  private def addWildcardDependencies(content: String, alreadyProcessed: mutable.Set[String], output: mutable.ListBuffer[String]): Unit = {
    val wildcardPackages = findWildcardImports(content)
    
    wildcardPackages.foreach { wildcardPackage =>
      log.debug(s"Processing wildcard import: $wildcardPackage._")
      
      val matchingFiles = findFilesForWildcardPackage(wildcardPackage)
      
      matchingFiles.foreach { file =>
        if (!alreadyProcessed.contains(file.getPath)) {
          try {
            val fileContent = allSourceFiles(file.getPath)._2
            val cleanContent = removePackagesAndImports(fileContent)
            output += cleanContent
            alreadyProcessed += file.getPath
            log.debug(s"Added wildcard dependency: ${file.getName} from wildcard $wildcardPackage._")
            
            // Procesar recursivamente las dependencias de este archivo
            addDependenciesRecursively(fileContent, alreadyProcessed, output)
            addWildcardDependencies(fileContent, alreadyProcessed, output)
            
          } catch {
            case ex: Exception =>
              log.warn(s"Could not process wildcard dependency ${file.getName}: ${ex.getMessage}")
          }
        }
      }
    }
  }
  
  /**
   * Método auxiliar actualizado para recibir parámetros adicionales
   */
  private def addDependenciesRecursively(content: String, alreadyProcessed: mutable.Set[String], output: mutable.ListBuffer[String]): Unit = {
    val importedClasses = findImportedClasses(content)
    
    importedClasses.foreach { className =>
      if (localClassNames.contains(className)) {
        findFileContainingClass(className) match {
          case Some((file, depContent)) if !alreadyProcessed.contains(file.getPath) =>
            alreadyProcessed += file.getPath
            val cleanContent = removePackagesAndImports(depContent)
            output += cleanContent
            log.debug(s"Added dependency: ${file.getName} for class $className")
            addDependenciesRecursively(depContent, alreadyProcessed, output)
            addWildcardDependencies(depContent, alreadyProcessed, output)
          case _ =>
        }
      }
    }
  }
  
  /**
   * Encuentra el archivo que contiene una clase específica
   */
  private def findFileContainingClass(className: String): Option[(File, String)] = {
    allSourceFiles.values.foreach { case (file, content) =>
      if (containsClassDefinition(content, className)) {
        return Some((file, content))
      }
    }
    None
  }
  
  /**
   * Verifica si el contenido contiene la definición de una clase
   */
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
  
  /**
   * Remueve paquetes e imports, manteniendo externos
   */
  private def removePackagesAndImports(content: String): String = {
    val lines = content.split("\n")
    val filteredLines = lines.filterNot { line =>
      val trimmed = line.trim
      if (trimmed.startsWith("package ")) {
        true
      } else if (trimmed.startsWith("import ")) {
        // Remover imports locales (tanto específicos como wildcards)
        shouldRemoveImport(trimmed)
      } else {
        false
      }
    }
    filteredLines.mkString("\n")
  }
  
  /**
   * Actualizar shouldRemoveImport para manejar wildcards
   */
  private def shouldRemoveImport(importLine: String): Boolean = {
    val trimmed = importLine.trim
    log.debug(s"Checking import: $trimmed")
    
    // Primero verificar si es un wildcard import
    val wildcardPattern = """import\s+([\w.]+)\._""".r
    wildcardPattern.findFirstMatchIn(trimmed) match {
      case Some(m) =>
        val wildcardPackage = m.group(1)
        // Verificar si tenemos archivos que coincidan con este wildcard
        val hasLocalFiles = findFilesForWildcardPackage(wildcardPackage).nonEmpty
        log.debug(s"Wildcard import '$wildcardPackage._' has local files: $hasLocalFiles")
        hasLocalFiles
      case None =>
        // Procesar imports no-wildcard como antes
        shouldRemoveNonWildcardImport(trimmed)
    }
  }
  
  /**
   * Método separado para imports específicos (no wildcards)
   */
  private def shouldRemoveNonWildcardImport(importLine: String): Boolean = {
    // Lógica existente para imports múltiples y únicos
    val multiImportPattern = """import\s+[\w.]*\.\{([^}]+)\}""".r
    multiImportPattern.findFirstMatchIn(importLine) match {
      case Some(m) =>
        val classNames = m.group(1).split(",").map(_.trim)
        log.debug(s"Multiple import classes: ${classNames.mkString(", ")}")
        val shouldRemove = classNames.exists(className => {
          val contains = localClassNames.contains(className)
          log.debug(s"Class '$className' is local: $contains")
          contains
        })
        log.debug(s"Multiple import shouldRemove: $shouldRemove")
        shouldRemove
      case None =>
        val singleImportPattern = """import\s+[\w.]*\.(\w+)""".r
        singleImportPattern.findFirstMatchIn(importLine) match {
          case Some(m) => 
            val className = m.group(1)
            val shouldRemove = localClassNames.contains(className)
            log.debug(s"Single import '$className' -> shouldRemove: $shouldRemove")
            shouldRemove
          case None => 
            log.debug(s"No pattern matched for: $importLine")
            false
        }
    }
  }
}
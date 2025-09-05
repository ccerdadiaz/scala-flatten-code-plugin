import sbt._
import Keys._
import scala.io.Source
import java.io.File
import scala.collection.mutable

object FlattenCode extends AutoPlugin {

  override def trigger = allRequirements

  object autoImport {
    val flattenCode = taskKey[Unit](
      "Flattens code by removing packages and copying dependencies"
    )
    val flattenInput = settingKey[File]("Input file for flattening")
    val flattenOutput = settingKey[File]("Output file for flattened result")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    flattenCode / watchSources := {
      val baseDir = (ThisBuild / baseDirectory).value
      val srcDir = baseDir / "src" / "main" / "scala"
      if (srcDir.exists()) Seq(WatchSource(srcDir)) else Seq.empty
    },
    flattenCode := {
      val inputFile = flattenInput.value
      val outputFile = flattenOutput.value
      val log = streams.value.log
      val baseDir = (ThisBuild / baseDirectory).value
      val srcDir = baseDir / "src" / "main" / "scala"

      if (!srcDir.exists()) {
        log.warn(
          s"[FlattenCode] Directory ${srcDir.getAbsolutePath} does not exist"
        )
        srcDir.mkdirs()
      }

      performFlattening(srcDir, inputFile, outputFile, log)
    }
  )

  private def performFlattening(
      srcDir: File,
      inputFile: File,
      outputFile: File,
      log: Logger
  ): Unit = {
    try {
      if (!inputFile.exists()) {
        throw new RuntimeException(
          s"[FlattenCode] Input file ${inputFile.getPath} does not exist"
        )
      }

      log.info(s"[FlattenCode] Flattening from: ${inputFile.getName}")

      val flattener = new CodeFlattener(srcDir, log)
      val result = flattener.flatten(inputFile)

      outputFile.getParentFile.mkdirs()
      IO.write(outputFile, result)

      log.info(s"[FlattenCode] Flattened into: ${outputFile.getName}")

    } catch {
      case ex: Exception =>
        log.error(s"[FlattenCode] ❌ Error: ${ex.getMessage}")
        throw ex
    }
  }
}

class CodeFlattener(sourceDir: File, log: Logger) {

  // Cache de archivos: nombreClase -> (archivo, contenido, package)
  private val classToFile =
    mutable.Map[String, (File, String, Option[String])]()
  // Cache de packages: nombrePaquete -> Set[nombreClase]
  private val packageToClasses = mutable.Map[String, mutable.Set[String]]()

  def flatten(inputFile: File): String = {
    log.debug("[FlattenCode] Loading source files...")
    loadAllSourceFiles()

    log.debug("[FlattenCode] Starting analysis from main file...")
    val processedFiles = mutable.Set[String]()
    val output = mutable.ListBuffer[String]()

    processFile(inputFile, processedFiles, output)

    log.info(s"[FlattenCode] Included ${processedFiles.size} files")
    output.mkString("\n\n")
  }

  /** Carga todos los archivos .scala y construye índices
    */
  private def loadAllSourceFiles(): Unit = {
    val scalaFiles = findScalaFiles(sourceDir)
    log.debug(s"[FlattenCode] Found ${scalaFiles.length} Scala files")

    scalaFiles.foreach { file =>
      try {
        val content = Source.fromFile(file).getLines().mkString("\n")
        val packageName = extractPackage(content)
        val classNames = extractClasses(content)

        classNames.foreach { className =>
          classToFile(className) = (file, content, packageName)
          packageName.foreach { pkg =>
            packageToClasses.getOrElseUpdate(
              pkg,
              mutable.Set[String]()
            ) += className
          }
        }

        log.debug(
          s"[FlattenCode] ${file.getName}: package=$packageName, classes=[${classNames.mkString(", ")}]"
        )

      } catch {
        case ex: Exception =>
          log.warn(
            s"[FlattenCode] Could not process ${file.getName}: ${ex.getMessage}"
          )
      }
    }
  }

  /** Encuentra archivos .scala recursivamente
    */
  private def findScalaFiles(dir: File): List[File] = {
    def traverse(file: File): List[File] = {
      if (file.isDirectory) {
        Option(file.listFiles()).map(_.toList.flatMap(traverse)).getOrElse(Nil)
      } else if (file.getName.endsWith(".scala")) {
        List(file)
      } else {
        Nil
      }
    }
    traverse(dir)
  }

  /** Procesa un archivo y sus dependencias recursivamente
    */
  private def processFile(
      file: File,
      processedFiles: mutable.Set[String],
      output: mutable.ListBuffer[String]
  ): Unit = {
    val filePath = file.getPath
    if (processedFiles.contains(filePath)) return

    try {
      val content = Source.fromFile(file).getLines().mkString("\n")
      val cleanContent = removePackagesAndImports(content)

      // Añadir contenido limpio
      output += cleanContent
      processedFiles += filePath
      log.debug(s"[FlattenCode] Added: ${file.getName}")

      // Encontrar todas las dependencias
      val dependencies = findDependencies(content, file)

      // Procesar dependencias recursivamente
      dependencies.foreach { depFile =>
        processFile(depFile, processedFiles, output)
      }

    } catch {
      case ex: Exception =>
        log.warn(
          s"[FlattenCode] Error processing ${file.getName}: ${ex.getMessage}"
        )
    }
  }

  /** Encuentra todas las dependencias de un archivo (algoritmo "como IDE")
    */
  private def findDependencies(
      content: String,
      currentFile: File
  ): Set[File] = {
    val dependencies = mutable.Set[File]()
    val currentPackage = extractPackage(content)

    // 1. Same-package visibility: incluir clases del mismo paquete referenciadas
    val referencedClasses = extractDirectReferences(content)
    currentPackage.foreach { pkg =>
      packageToClasses.get(pkg).foreach { classesInPackage =>
        classesInPackage.foreach { className =>
          if (referencedClasses.contains(className)) {
            classToFile.get(className).foreach { case (file, _, _) =>
              if (file != currentFile) { // No incluirse a sí mismo
                dependencies += file
                log.debug(
                  s"[FlattenCode] Same-package dependency: $className from ${file.getName}"
                )
              }
            }
          }
        }
      }
    }

    // 2. Import específicos: import a.b.ClassName
    val specificImports = extractSpecificImports(content)
    specificImports.foreach { className =>
      classToFile.get(className).foreach { case (file, _, _) =>
        dependencies += file
        log.debug(
          s"[FlattenCode] Specific import dependency: $className from ${file.getName}"
        )
      }
    }

    // 3. Import múltiples: import a.b.{Class1, Class2}
    val multipleImports = extractMultipleImports(content)
    multipleImports.foreach { className =>
      classToFile.get(className).foreach { case (file, _, _) =>
        dependencies += file
        log.debug(
          s"[FlattenCode] Multiple import dependency: $className from ${file.getName}"
        )
      }
    }

    // 4. Import wildcards: import a.b._
    val wildcardImports = extractWildcardImports(content)
    wildcardImports.foreach { packageName =>
      packageToClasses.get(packageName).foreach { classNames =>
        classNames.foreach { className =>
          classToFile.get(className).foreach { case (file, _, _) =>
            dependencies += file
            log.debug(
              s"[FlattenCode] Wildcard dependency: $className from ${file.getName} (package $packageName)"
            )
          }
        }
      }
    }

    dependencies.toSet
  }

  /** Extrae package declaration: "package a.b.c" -> Some("a.b.c")
    */
  private def extractPackage(content: String): Option[String] = {
    val packagePattern = """(?m)^\s*package\s+([\w.]+)\s*$""".r
    val packageOpt = packagePattern.findFirstMatchIn(content).map(_.group(1))

    packageOpt.foreach { pkg =>
      log.debug(s"[FlattenCode] Found package: $pkg")
    }

    packageOpt
  }

  /** Extrae nombres de clases definidas: class, case class, object, trait
    * Método público para testing
    */
  def extractClasses(content: String): Set[String] = {
    val patterns = List(
      """(?m)^\s*case\s+class\s+(\w+)""".r,
      """(?m)^\s*class\s+(\w+)""".r,
      """(?m)^\s*object\s+(\w+)""".r,
      """(?m)^\s*trait\s+(\w+)""".r,
      """(?m)^\s*sealed\s+trait\s+(\w+)""".r
    )

    val classes = mutable.Set[String]()
    patterns.foreach { pattern =>
      pattern.findAllMatchIn(content).foreach { m =>
        classes += m.group(1)
      }
    }
    classes.toSet
  }

  /** Extrae referencias directas en código: new ClassName(),
    * ClassName.method(), ClassName(...), val x = ClassName extends/with
    * clauses, etc.
    */
  private def extractDirectReferences(content: String): Set[String] = {
    val references = mutable.Set[String]()

    // Eliminar comentarios y strings para evitar falsos positivos
    val cleanContent = removeCommentsAndStrings(content)

    val patterns = List(
      """new\s+(\w+)""".r, // new ClassName
      """(\w+)\s*\(""".r, // ClassName( - companion object apply
      """(\w+)\.(\w+)""".r, // ClassName.method or ClassName.CONSTANT
      """\s*=\s*(\w+)\s*(?:[;\n}]|$)""".r, // val x = ClassName
      """extends\s+(\w+)""".r, // extends MyTrait/MyClass
      """with\s+(\w+)""".r // with MyTrait
    )

    // new ClassName
    patterns(0).findAllMatchIn(cleanContent).foreach { m =>
      val className = m.group(1)
      if (className.head.isUpper) {
        references += className
        log.debug(s"[FlattenCode] Found 'new' reference: $className")
      }
    }

    // ClassName( - solo si empieza con mayúscula
    patterns(1).findAllMatchIn(cleanContent).foreach { m =>
      val className = m.group(1)
      if (className.head.isUpper && !isBuiltinType(className)) {
        references += className
        log.debug(s"[FlattenCode] Found apply reference: $className")
      }
    }

    // ClassName.method - solo la parte antes del punto
    patterns(2).findAllMatchIn(cleanContent).foreach { m =>
      val className = m.group(1)
      if (className.head.isUpper && !isBuiltinType(className)) {
        references += className
        log.debug(s"[FlattenCode] Found static reference: $className")
      }
    }

    // val x = ClassName
    patterns(3).findAllMatchIn(cleanContent).foreach { m =>
      val className = m.group(1)
      if (className.head.isUpper && !isBuiltinType(className)) {
        references += className
        log.debug(s"[FlattenCode] Found assignment reference: $className")
      }
    }

    // extends ClassName/TraitName
    patterns(4).findAllMatchIn(cleanContent).foreach { m =>
      val className = m.group(1)
      if (className.head.isUpper && !isBuiltinType(className)) {
        references += className
        log.debug(s"[FlattenCode] Found 'extends' reference: $className")
      }
    }

    // with TraitName
    patterns(5).findAllMatchIn(cleanContent).foreach { m =>
      val className = m.group(1)
      if (className.head.isUpper && !isBuiltinType(className)) {
        references += className
        log.debug(s"[FlattenCode] Found 'with' reference: $className")
      }
    }

    references.toSet
  }

  /** Elimina comentarios y strings literales para evitar falsos positivos
    */
  private def removeCommentsAndStrings(content: String): String = {
    val lines = content.split("\n")
    lines
      .map { line =>
        // Remover comentarios de línea
        val withoutComments = line.replaceAll("""//.*$""", "")
        // Remover strings (aproximado, no maneja escapes complejos)
        withoutComments.replaceAll(""""[^"]*"""", "\"\"")
      }
      .mkString("\n")
  }

  /** Verifica si es un tipo built-in que no deberíamos incluir
    */
  private def isBuiltinType(className: String): Boolean = {
    val builtins = Set(
      "List",
      "Array",
      "Set",
      "Map",
      "Option",
      "Some",
      "None",
      "Either",
      "Left",
      "Right",
      "Future",
      "Try",
      "Success",
      "Failure",
      "String",
      "Int",
      "Double",
      "Float",
      "Boolean",
      "Unit"
    )
    builtins.contains(className)
  }

  /** Extrae imports específicos: import a.b.ClassName -> Set("ClassName")
    */
  private def extractSpecificImports(content: String): Set[String] = {
    val pattern = """(?m)^\s*import\s+[\w.]+\.(\w+)\s*$""".r
    val classes = pattern.findAllMatchIn(content).map(_.group(1)).toSet

    if (classes.nonEmpty) {
      log.debug(
        s"[FlattenCode] Found specific imports: ${classes.mkString(", ")}"
      )
    }

    classes
  }

  /** Extrae imports múltiples: import a.b.{Class1, Class2} -> Set("Class1",
    * "Class2")
    */
  private def extractMultipleImports(content: String): Set[String] = {
    val pattern = """(?m)^\s*import\s+[\w.]+\.\{([^}]+)\}""".r
    val classes = mutable.Set[String]()

    pattern.findAllMatchIn(content).foreach { m =>
      val classNames = m.group(1).split(",").map(_.trim)
      classes ++= classNames
      log.debug(
        s"[FlattenCode] Found multiple imports: ${classNames.mkString(", ")}"
      )
    }

    classes.toSet
  }

  /** Extrae imports wildcards: import a.b._ -> Set("a.b")
    */
  private def extractWildcardImports(content: String): Set[String] = {
    val pattern = """(?m)^\s*import\s+([\w.]+)\._\s*$""".r
    val packages = pattern.findAllMatchIn(content).map(_.group(1)).toSet

    if (packages.nonEmpty) {
      log.debug(
        s"[FlattenCode] Found wildcard imports: ${packages.mkString(", ")}"
      )
    }

    packages
  }

  /** Remueve packages e imports locales, mantiene imports externos (scala.*,
    * java.*)
    */
  private def removePackagesAndImports(content: String): String = {
    val lines = content.split("\n")
    val filteredLines = lines.filterNot { line =>
      val trimmed = line.trim

      if (trimmed.startsWith("package ")) {
        true // Eliminar packages
      } else if (trimmed.startsWith("import ")) {
        // Mantener imports externos (scala.*, java.*, javax.*)
        val isExternal = trimmed.contains("scala.") || trimmed.contains(
          "java."
        ) || trimmed.contains("javax.")
        !isExternal // Eliminar si NO es externo
      } else {
        false
      }
    }

    filteredLines.mkString("\n")
  }
}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path}
import scala.io.Source
import sbt.internal.util.ConsoleLogger

class CodeFlattenerSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {
  
  private var tempDir: Path = _
  private var sourceDir: File = _
  private val testLogger = ConsoleLogger()
  
  override def beforeEach(): Unit = {
    tempDir = Files.createTempDirectory("flatten-test")
    sourceDir = new File(tempDir.toFile, "src")
    sourceDir.mkdirs()
  }
  
  override def afterEach(): Unit = {
    deleteRecursively(tempDir.toFile)
  }
  
  private def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) {
      file.listFiles().foreach(deleteRecursively)
    }
    file.delete()
  }
  
  private def createFile(path: String, content: String): File = {
    val file = new File(sourceDir, path)
    file.getParentFile.mkdirs()
    val writer = new PrintWriter(file)
    try {
      writer.write(content)
    } finally {
      writer.close()
    }
    file
  }
  
  "CodeFlattener" should "remove package declarations from single file" in {
    val mainContent = 
      """package com.example
        |
        |object Main {
        |  def main(args: Array[String]): Unit = {
        |    println("Hello")
        |  }
        |}""".stripMargin
    
    val mainFile = createFile("Main.scala", mainContent)
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    
    val result = flattener.flatten(mainFile)
    
    result should not include "package com.example"
    result should include("object Main")
    result should include("println(\"Hello\")")
  }
  
  it should "preserve external imports but remove local imports" in {
    val mainContent = 
      """package com.example
        |
        |import scala.collection.mutable.ListBuffer
        |import com.example.utils.Helper
        |
        |object Main {
        |  def main(args: Array[String]): Unit = {
        |    val buffer = ListBuffer[String]()
        |    Helper.doSomething()
        |  }
        |}""".stripMargin
    
    val helperContent = 
      """package com.example.utils
        |
        |object Helper {
        |  def doSomething(): Unit = println("Helper")
        |}""".stripMargin
    
    val mainFile = createFile("Main.scala", mainContent)
    createFile("utils/Helper.scala", helperContent)
    
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    val result = flattener.flatten(mainFile)
    
    // Should preserve external import
    result should include("import scala.collection.mutable.ListBuffer")
    // Should remove local import
    result should not include "import com.example.utils.Helper"
    // Should include both classes without packages
    result should include("object Main")
    result should include("object Helper")
  }
  
  it should "include dependencies recursively" in {
    val mainContent = 
      """package com.example
        |
        |import com.example.model.Person
        |
        |object Main {
        |  def main(args: Array[String]): Unit = {
        |    val person = Person("John", Address("Street"))
        |  }
        |}""".stripMargin
    
    val personContent = 
      """package com.example.model
        |
        |import com.example.model.Address
        |
        |case class Person(name: String, address: Address)""".stripMargin
    
    val addressContent = 
      """package com.example.model
        |
        |case class Address(street: String)""".stripMargin
    
    val mainFile = createFile("Main.scala", mainContent)
    createFile("model/Person.scala", personContent)
    createFile("model/Address.scala", addressContent)
    
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    val result = flattener.flatten(mainFile)
    
    // Should include all three definitions
    result should include("object Main")
    result should include("case class Person")
    result should include("case class Address")
    
    // Should not include any packages
    result should not include "package"
    
    // Should not include local imports
    result should not include "import com.example"
  }
  
  it should "handle multiple source directories" in {
    val mainContent = 
      """import utils.Helper
        |
        |object Main {
        |  Helper.help()
        |}""".stripMargin
    
    val helperContent = 
      """object Helper {
        |  def help(): Unit = println("Help")
        |}""".stripMargin
    
    val mainFile = createFile("Main.scala", mainContent)
    
    val utilsDir = new File(tempDir.toFile, "utils")
    utilsDir.mkdirs()
    val helperFile = new File(utilsDir, "Helper.scala")
    val writer = new PrintWriter(helperFile)
    try {
      writer.write(helperContent)
    } finally {
      writer.close()
    }
    
    val flattener = new CodeFlattener(Seq(sourceDir, utilsDir), testLogger)
    val result = flattener.flatten(mainFile)
    
    result should include("object Main")
    result should include("object Helper")
  }
  
  it should "not duplicate classes when included multiple times" in {
    val mainContent = 
      """import model.{Person, Address}
        |
        |object Main {
        |  val person = Person("John", Address("Street"))
        |}""".stripMargin
    
    val personContent = 
      """import model.Address
        |
        |case class Person(name: String, address: Address)""".stripMargin
    
    val addressContent = 
      """case class Address(street: String)""".stripMargin
    
    val mainFile = createFile("Main.scala", mainContent)
    createFile("model/Person.scala", personContent)
    createFile("model/Address.scala", addressContent)
    
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    val result = flattener.flatten(mainFile)
    
    // Count occurrences of Address class definition
    val addressCount = "case class Address".r.findAllIn(result).length
    addressCount shouldBe 1
  }
  
  it should "extract class names correctly from different definition types" in {
    val content = 
      """class RegularClass
        |case class CaseClass(value: Int)
        |object SingletonObject
        |trait SomeTrait
        |sealed trait SealedTrait""".stripMargin
    
    val file = createFile("Definitions.scala", content)
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    
    val result = flattener.extractClassDefinitions(content)
    
    result should contain("RegularClass")
    result should contain("CaseClass")
    result should contain("SingletonObject")
    result should contain("SomeTrait")
    result should contain("SealedTrait")
  }
  
  // NUEVAS PRUEBAS PARA WILDCARDS - Empezando con caso simple
  
  it should "handle simple wildcard import with one file" in {
    val mainContent = 
      """import test.geometry._
        |
        |object Main {
        |  val point = Point(1, 2)
        |}""".stripMargin
    
    val pointContent = 
      """package test.geometry
        |
        |case class Point(x: Int, y: Int)""".stripMargin
    
    val mainFile = createFile("Main.scala", mainContent)
    createFile("Point.scala", pointContent)
    
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    val result = flattener.flatten(mainFile)
    
    println("=== SIMPLE WILDCARD RESULT ===")
    println(result)
    println("===============================")
    
    result should include("case class Point")
    result should include("object Main")
    result should not include "package"
    result should not include "import test.geometry._"
  }
  
  it should "handle exact wildcard imports based on package declarations" in {
    val mainContent = 
      """import a.b.geometry._
        |
        |object Main {
        |  val point = Point(1, 2)
        |  val line = Line(point, Point(3, 4))
        |}""".stripMargin
    
    val pointContent = 
      """package a.b.geometry
        |
        |case class Point(x: Int, y: Int)""".stripMargin
    
    val lineContent = 
      """package a.b.geometry
        |
        |case class Line(start: Point, end: Point)""".stripMargin
    
    // Archivo que NO debe incluirse (diferente paquete)
    val algorithmContent = 
      """package a.b.algorithms
        |
        |object Sort {
        |  def quickSort[T](list: List[T]): List[T] = list
        |}""".stripMargin
    
    val mainFile = createFile("Main.scala", mainContent)
    createFile("parts/Point.scala", pointContent)        // Estructura libre de directorios
    createFile("geometry/Line.scala", lineContent)       // Estructura libre de directorios
    createFile("algo/Sort.scala", algorithmContent)      // No debe incluirse
    
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    val result = flattener.flatten(mainFile)
    
    // Debe incluir archivos del paquete a.b.geometry
    result should include("case class Point")
    result should include("case class Line")
    result should include("object Main")
    
    // NO debe incluir archivos de otros paquetes
    result should not include "object Sort"
    
    // No debe incluir packages ni imports locales
    result should not include "package"
    result should not include "import a.b.geometry._"
  }
  
  it should "handle parent wildcard imports including all sub-packages" in {
    val mainContent = 
      """import a.b._
        |
        |object GameEngine {
        |  val player = Player("Hero", Point(0, 0))
        |  val ai = MinimaxAI()
        |}""".stripMargin
    
    val playerContent = 
      """package a.b.model
        |
        |case class Player(name: String, position: Point)""".stripMargin
    
    val pointContent = 
      """package a.b.geometry
        |
        |case class Point(x: Int, y: Int)""".stripMargin
    
    val aiContent = 
      """package a.b.ai
        |
        |case class MinimaxAI()""".stripMargin
    
    // Archivo que NO debe incluirse (paquete diferente)
    val utilsContent = 
      """package utils.helpers
        |
        |object StringUtils {
        |  def reverse(s: String): String = s.reverse
        |}""".stripMargin
    
    val mainFile = createFile("Main.scala", mainContent)
    createFile("models/Player.scala", playerContent)     // a.b.model -> incluir
    createFile("geom/Point.scala", pointContent)         // a.b.geometry -> incluir
    createFile("ai/Minimax.scala", aiContent)            // a.b.ai -> incluir
    createFile("utils/StringUtils.scala", utilsContent)  // utils.helpers -> NO incluir
    
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    val result = flattener.flatten(mainFile)
    
    // Debe incluir todos los sub-paquetes de a.b
    result should include("case class Player")    // a.b.model
    result should include("case class Point")     // a.b.geometry
    result should include("case class MinimaxAI") // a.b.ai
    result should include("object GameEngine")
    
    // No debe incluir paquetes externos
    result should not include "object StringUtils"
    
    // No debe incluir declaraciones de paquete
    result should not include "package"
    result should not include "import a.b._"
  }
  
  it should "handle wildcards with files in arbitrary directory structure" in {
    val mainContent = 
      """import competitive.utils._
        |
        |object Solution {
        |  def solve(): Unit = {
        |    val helper = MathHelper
        |    val parser = InputParser()
        |  }
        |}""".stripMargin
    
    val mathContent = 
      """package competitive.utils
        |
        |object MathHelper {
        |  def gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        |}""".stripMargin
    
    val parserContent = 
      """package competitive.utils
        |
        |case class InputParser() {
        |  def readInt(): Int = scala.io.StdIn.readInt()
        |}""".stripMargin
    
    val mainFile = createFile("Solution.scala", mainContent)
    // Estructura de directorios completamente arbitraria
    createFile("random/deep/nested/MathHelper.scala", mathContent)
    createFile("somewhere/else/InputParser.scala", parserContent)
    
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    val result = flattener.flatten(mainFile)
    
    result should include("object Solution")
    result should include("object MathHelper")
    result should include("case class InputParser")
    result should not include "package competitive.utils"
    result should not include "import competitive.utils._"
  }
  
  it should "handle wildcard imports combined with specific imports" in {
    val mainContent = 
      """import a.b.geometry._
        |import a.b.algorithms.Sort
        |import scala.collection.mutable.ListBuffer
        |
        |object Mixed {
        |  val point = Point(1, 2)
        |  val sorted = Sort.quickSort(List(3, 1, 2))
        |  val buffer = ListBuffer[String]()
        |}""".stripMargin
    
    val pointContent = 
      """package a.b.geometry
        |
        |case class Point(x: Int, y: Int)""".stripMargin
    
    val circleContent = 
      """package a.b.geometry
        |
        |case class Circle(center: Point, radius: Double)""".stripMargin
    
    val sortContent = 
      """package a.b.algorithms
        |
        |object Sort {
        |  def quickSort[T](list: List[T]): List[T] = list.sorted
        |}""".stripMargin
    
    val mainFile = createFile("Mixed.scala", mainContent)
    createFile("Point.scala", pointContent)
    createFile("Circle.scala", circleContent)
    createFile("Sort.scala", sortContent)
    
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    val result = flattener.flatten(mainFile)
    
    // Wildcard debe incluir todo a.b.geometry
    result should include("case class Point")
    result should include("case class Circle")  // Incluido aunque no se use
    
    // Import específico debe incluir Sort
    result should include("object Sort")
    
    // Import externo debe preservarse
    result should include("import scala.collection.mutable.ListBuffer")
    
    // Imports locales deben eliminarse
    result should not include "import a.b.geometry._"
    result should not include "import a.b.algorithms.Sort"
  }
  
  it should "handle recursive wildcard dependencies" in {
    val mainContent = 
      """import game.engine._
        |
        |object Game {
        |  val engine = GameEngine()
        |}""".stripMargin
    
    val engineContent = 
      """package game.engine
        |
        |import game.model._
        |
        |case class GameEngine() {
        |  val player = Player("Hero")
        |}""".stripMargin
    
    val playerContent = 
      """package game.model
        |
        |case class Player(name: String)""".stripMargin
    
    val statsContent = 
      """package game.model
        |
        |case class Stats(level: Int)""".stripMargin
    
    val mainFile = createFile("Game.scala", mainContent)
    createFile("GameEngine.scala", engineContent)
    createFile("Player.scala", playerContent)
    createFile("Stats.scala", statsContent)
    
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    val result = flattener.flatten(mainFile)
    
    // Debe incluir engine por wildcard directo
    result should include("case class GameEngine")
    
    // Debe incluir todo game.model por wildcard en GameEngine
    result should include("case class Player")
    result should include("case class Stats")  // Incluido aunque no se use
    
    result should include("object Game")
    result should not include "package"
    result should not include "import game.engine._"
    result should not include "import game.model._"
  }
  
  "Integration test" should "flatten a complete competitive programming project with wildcards" in {
    val solutionContent = 
      """import competitive.geometry._
        |import scala.io.StdIn
        |
        |object Solution {
        |  def main(args: Array[String]): Unit = {
        |    val point = Point(1, 2)
        |    println(point.x)
        |  }
        |}""".stripMargin
    
    val pointContent = 
      """package competitive.geometry
        |
        |case class Point(x: Int, y: Int)""".stripMargin
    
    val solutionFile = createFile("Solution.scala", solutionContent)
    createFile("geometry/Point.scala", pointContent)
    
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    val result = flattener.flatten(solutionFile)
    
    // Debug: imprimir el resultado para diagnosticar
    println("=== INTEGRATION TEST RESULT ===")
    println(result)
    println("================================")
    
    // Verificar que incluye Point por wildcard
    result should include("case class Point")
    result should include("object Solution")
    
    // Verificar que preserva imports externos
    result should include("import scala.io.StdIn")
    
    // Verificar que elimina paquetes e imports locales
    result should not include "package"
    result should not include "import competitive.geometry._"
  }
}
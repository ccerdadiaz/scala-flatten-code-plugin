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
  
  "CodeFlattener" should "remove packages and preserve external imports" in {
    val playerContent = 
      """package contests.codingame.robowars
        |
        |import scala.collection.mutable.ListBuffer
        |
        |object Player extends App {
        |  val buffer = ListBuffer[String]()
        |  println("Hello CodeFlattener!")
        |}""".stripMargin
    
    val playerFile = createFile("Player.scala", playerContent)
    val flattener = new CodeFlattener(sourceDir, testLogger)
    
    val result = flattener.flatten(playerFile)
    
    // Should remove package
    result should not include "package contests.codingame.robowars"
    
    // Should preserve external import
    result should include("import scala.collection.mutable.ListBuffer")
    
    // Should include main content
    result should include("object Player extends App")
    result should include("println(\"Hello CodeFlattener!\")")
  }
  
  it should "include same-package classes referenced in code" in {
    val playerContent = 
      """package contests.codingame.robowars
        |
        |object Player extends App {
        |  val state = new GameState()
        |  val helper = HelperUtils
        |  println("Game starting")
        |}""".stripMargin
    
    val gameStateContent = 
      """package contests.codingame.robowars
        |
        |case class GameState(turn: Int = 1)""".stripMargin
    
    val helperContent = 
      """package contests.codingame.robowars
        |
        |object HelperUtils {
        |  def debug(msg: String): Unit = println(s"DEBUG: $msg")
        |}""".stripMargin
    
    // Tool in same package but NOT referenced -> should NOT be included
    val toolContent = 
      """package contests.codingame.robowars
        |
        |object FancyGenerator extends App {
        |  println("Generating fancy stuff...")
        |}""".stripMargin
    
    val playerFile = createFile("Player.scala", playerContent)
    createFile("GameState.scala", gameStateContent)
    createFile("HelperUtils.scala", helperContent)
    createFile("FancyGenerator.scala", toolContent)
    
    val flattener = new CodeFlattener(sourceDir, testLogger)
    val result = flattener.flatten(playerFile)
    
    // Should include referenced same-package classes
    result should include("case class GameState")
    result should include("object HelperUtils")
    result should include("object Player extends App")
    
    // Should NOT include unreferenced tool
    result should not include "object FancyGenerator"
    result should not include "Generating fancy stuff"
    
    // Should remove all packages
    result should not include "package contests.codingame.robowars"
  }
  
  it should "include classes from specific imports" in {
    val playerContent = 
      """package contests.codingame.robowars
        |
        |import contests.geometry.Point
        |import contests.ai.Strategy
        |
        |object Player extends App {
        |  val pos = Point(0, 0)
        |  val strategy = Strategy.aggressive()
        |}""".stripMargin
    
    val pointContent = 
      """package contests.geometry
        |
        |case class Point(x: Int, y: Int)""".stripMargin
    
    val strategyContent = 
      """package contests.ai
        |
        |object Strategy {
        |  def aggressive(): String = "ATTACK"
        |}""".stripMargin
    
    val playerFile = createFile("Player.scala", playerContent)
    createFile("Point.scala", pointContent)
    createFile("Strategy.scala", strategyContent)
    
    val flattener = new CodeFlattener(sourceDir, testLogger)
    val result = flattener.flatten(playerFile)
    
    result should include("object Player extends App")
    result should include("case class Point")
    result should include("object Strategy")
    
    // Should remove packages and local imports
    result should not include "package"
    result should not include "import contests.geometry.Point"
    result should not include "import contests.ai.Strategy"
  }
  
  it should "include classes from multiple imports" in {
    val playerContent = 
      """package contests.codingame.robowars
        |
        |import contests.geometry.{Point, Vector, Board}
        |
        |object Player extends App {
        |  val pos = Point(1, 1)
        |  println("Using geometry classes")
        |}""".stripMargin
    
    val pointContent = 
      """package contests.geometry
        |
        |case class Point(x: Int, y: Int)""".stripMargin
    
    val vectorContent = 
      """package contests.geometry
        |
        |case class Vector(dx: Int, dy: Int)""".stripMargin
    
    val boardContent = 
      """package contests.geometry
        |
        |case class Board(width: Int, height: Int)""".stripMargin
    
    val playerFile = createFile("Player.scala", playerContent)
    createFile("Point.scala", pointContent)
    createFile("Vector.scala", vectorContent)
    createFile("Board.scala", boardContent)
    
    val flattener = new CodeFlattener(sourceDir, testLogger)
    val result = flattener.flatten(playerFile)
    
    // Should include ALL classes from multiple import (even if not directly used)
    result should include("case class Point")
    result should include("case class Vector")
    result should include("case class Board")
    result should include("object Player extends App")
    
    // Should remove local imports and packages
    result should not include "import contests.geometry.{Point, Vector, Board}"
    result should not include "package"
  }
  
  it should "include all classes from wildcard imports" in {
    val playerContent = 
      """package contests.codingame.robowars
        |
        |import contests.ai._
        |
        |object Player extends App {
        |  val neural = NeuralNetwork()
        |  println("AI ready")
        |}""".stripMargin
    
    val neuralContent = 
      """package contests.ai
        |
        |case class NeuralNetwork() {
        |  def predict(): String = "MOVE_UP"
        |}""".stripMargin
    
    val dataContent = 
      """package contests.ai
        |
        |case class NeuralData(weights: List[Double])""".stripMargin
    
    val playerFile = createFile("Player.scala", playerContent)
    createFile("NeuralNetwork.scala", neuralContent)
    createFile("NeuralData.scala", dataContent)
    
    val flattener = new CodeFlattener(sourceDir, testLogger)
    val result = flattener.flatten(playerFile)
    
    // Should include ALL classes from wildcard package (even unused ones)
    result should include("case class NeuralNetwork")
    result should include("case class NeuralData") // Not directly used but in wildcard
    result should include("object Player extends App")
    
    // Should remove wildcard import and packages
    result should not include "import contests.ai._"
    result should not include "package"
  }
  
  it should "handle recursive dependencies" in {
    val playerContent = 
      """package contests.codingame.robowars
        |
        |import contests.geometry.Point
        |
        |object Player extends App {
        |  val pos = Point(1, 1)
        |}""".stripMargin
    
    val pointContent = 
      """package contests.geometry
        |
        |import contests.math.Calculator
        |
        |case class Point(x: Int, y: Int) {
        |  def distance(): Double = Calculator.sqrt(x * x + y * y)
        |}""".stripMargin
    
    val calculatorContent = 
      """package contests.math
        |
        |object Calculator {
        |  def sqrt(value: Double): Double = math.sqrt(value)
        |}""".stripMargin
    
    val playerFile = createFile("Player.scala", playerContent)
    createFile("Point.scala", pointContent)
    createFile("Calculator.scala", calculatorContent)
    
    val flattener = new CodeFlattener(sourceDir, testLogger)
    val result = flattener.flatten(playerFile)
    
    // Should include all dependencies recursively
    result should include("object Player extends App")
    result should include("case class Point")
    result should include("object Calculator") // Recursive dependency
    
    // Should remove all packages and local imports
    result should not include "package"
    result should not include "import contests.geometry.Point"
    result should not include "import contests.math.Calculator"
  }
  
  it should "detect various types of direct references in code" in {
    val playerContent = 
      """package contests.codingame.robowars
        |
        |object Player extends App {
        |  val state = new GameState()      // constructor
        |  val pos = Point(1, 2)           // companion apply
        |  Board.createEmpty()             // static method
        |  println(Constants.MAX_SIZE)     // static field
        |}""".stripMargin
    
    val gameStateContent = 
      """package contests.codingame.robowars
        |
        |case class GameState()""".stripMargin
    
    val pointContent = 
      """package contests.codingame.robowars
        |
        |case class Point(x: Int, y: Int)""".stripMargin
    
    val boardContent = 
      """package contests.codingame.robowars
        |
        |object Board {
        |  def createEmpty(): String = "empty"
        |}""".stripMargin
    
    val constantsContent = 
      """package contests.codingame.robowars
        |
        |object Constants {
        |  val MAX_SIZE = 100
        |}""".stripMargin
    
    val playerFile = createFile("Player.scala", playerContent)
    createFile("GameState.scala", gameStateContent)
    createFile("Point.scala", pointContent)
    createFile("Board.scala", boardContent)
    createFile("Constants.scala", constantsContent)
    
    val flattener = new CodeFlattener(sourceDir, testLogger)
    val result = flattener.flatten(playerFile)
    
    // Should detect and include all referenced classes
    result should include("object Player extends App")
    result should include("case class GameState") // from "new GameState()"
    result should include("case class Point")     // from "Point(1, 2)"
    result should include("object Board")         // from "Board.createEmpty()"
    result should include("object Constants")     // from "Constants.MAX_SIZE"
  }
  
  it should "not duplicate classes when included multiple times" in {
    val playerContent = 
      """package contests.codingame.robowars
        |
        |import contests.geometry.{Point, Board}
        |
        |object Player extends App {
        |  val pos = Point(1, 2)
        |}""".stripMargin
    
    val pointContent = 
      """package contests.geometry
        |
        |case class Point(x: Int, y: Int)""".stripMargin
    
    val boardContent = 
      """package contests.geometry
        |
        |import contests.geometry.Point
        |
        |case class Board(corners: List[Point])""".stripMargin
    
    val playerFile = createFile("Player.scala", playerContent)
    createFile("Point.scala", pointContent)
    createFile("Board.scala", boardContent)
    
    val flattener = new CodeFlattener(sourceDir, testLogger)
    val result = flattener.flatten(playerFile)
    
    // Count occurrences of Point class definition
    val pointCount = "case class Point".r.findAllIn(result).length
    pointCount shouldBe 1
    
    result should include("case class Board")
    result should include("object Player extends App")
  }
  
  // Test para el método público extractClasses (needed for testing)
  it should "extract class definitions correctly" in {
    val content = 
      """package test
        |
        |class RegularClass
        |case class CaseClass(value: Int)
        |object SingletonObject
        |trait SomeTrait
        |sealed trait SealedTrait""".stripMargin
    
    val flattener = new CodeFlattener(sourceDir, testLogger)
    val result = flattener.extractClasses(content)
    
    result should contain("RegularClass")
    result should contain("CaseClass")
    result should contain("SingletonObject")
    result should contain("SomeTrait")
    result should contain("SealedTrait")
  }
  
  // Integration test: proyecto completo competitive programming
  "Integration test" should "flatten a complete competitive programming project" in {
    val playerContent = 
      """package contests.codingame.robowars
        |
        |import contests.geometry._
        |import contests.codingame.robowars.solution._
        |import scala.io.StdIn
        |
        |object Player extends App {
        |  val board = GameBoard()
        |  val pos = Point(StdIn.readInt(), StdIn.readInt())
        |  val strategy = Strategy.defensive()
        |  println(pos.x)
        |}""".stripMargin
    
    // Clases de solución (paquete diferente, incluidas por wildcard)
    val gameBoardContent = 
      """package contests.codingame.robowars.solution
        |
        |case class GameBoard() {
        |  def isValid(): Boolean = true
        |}""".stripMargin
    
    val strategyContent = 
      """package contests.codingame.robowars.solution
        |
        |object Strategy {
        |  def defensive(): String = "DEFEND"
        |}""".stripMargin
    
    // Clase reutilizable (geometría)
    val pointContent = 
      """package contests.geometry
        |
        |case class Point(x: Int, y: Int) {
        |  def distance(other: Point): Double = {
        |    math.sqrt(math.pow(x - other.x, 2) + math.pow(y - other.y, 2))
        |  }
        |}""".stripMargin
    
    // Herramienta en mismo paquete que NO debe incluirse (no referenciada)
    val toolContent = 
      """package contests.codingame.robowars
        |
        |object TestDataGenerator extends App {
        |  println("Generating test data...")
        |}""".stripMargin
    
    val playerFile = createFile("Player.scala", playerContent)
    createFile("GameBoard.scala", gameBoardContent)
    createFile("Strategy.scala", strategyContent)
    createFile("Point.scala", pointContent)
    createFile("TestDataGenerator.scala", toolContent)
    
    val flattener = new CodeFlattener(sourceDir, testLogger)
    val result = flattener.flatten(playerFile)
    
    println("=== INTEGRATION TEST RESULT ===")
    println(result)
    println("================================")
    
    // Should include main class and all dependencies
    result should include("object Player extends App")
    result should include("case class GameBoard")    // wildcard: contests.codingame.robowars.solution._
    result should include("object Strategy")         // wildcard: contests.codingame.robowars.solution._
    result should include("case class Point")        // wildcard: contests.geometry._
    
    // Should preserve external imports
    result should include("import scala.io.StdIn")
    
    // Should NOT include unreferenced tool
    result should not include "object TestDataGenerator"
    result should not include "Generating test data"
    
    // Should remove all packages and local imports
    result should not include "package"
    result should not include "import contests.geometry._"
    result should not include "import contests.codingame.robowars.solution._"
  }
}
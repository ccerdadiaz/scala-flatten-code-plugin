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
    
    // Now we can call the method directly since it's public
    val result = flattener.extractClassDefinitions(content)
    
    result should contain("RegularClass")
    result should contain("CaseClass")
    result should contain("SingletonObject")
    result should contain("SomeTrait")
    result should contain("SealedTrait")
  }
  
  "Integration test" should "flatten a complete project structure" in {
    // Create a realistic project structure
    val gameContent = 
      """package game
        |
        |import game.model.{Player, Position}
        |import game.engine.GameEngine
        |
        |object Game {
        |  def main(args: Array[String]): Unit = {
        |    val player = Player("Hero", Position(0, 0))
        |    val engine = new GameEngine()
        |    engine.run(player)
        |  }
        |}""".stripMargin
    
    val playerContent = 
      """package game.model
        |
        |import game.model.Position
        |
        |case class Player(name: String, position: Position)""".stripMargin
    
    val positionContent = 
      """package game.model
        |
        |case class Position(x: Int, y: Int)""".stripMargin
    
    val engineContent = 
      """package game.engine
        |
        |import game.model.Player
        |
        |class GameEngine {
        |  def run(player: Player): Unit = {
        |    println(s"Running game with ${player.name}")
        |  }
        |}""".stripMargin
    
    val gameFile = createFile("Game.scala", gameContent)
    createFile("model/Player.scala", playerContent)
    createFile("model/Position.scala", positionContent)
    createFile("engine/GameEngine.scala", engineContent)
    
    val flattener = new CodeFlattener(Seq(sourceDir), testLogger)
    val result = flattener.flatten(gameFile)
    
    // Verify all components are included
    result should include("object Game")
    result should include("case class Player")
    result should include("case class Position")
    result should include("class GameEngine")
    
    // Verify no packages remain
    result should not include "package"
    
    // Verify local imports are removed
    result should not include "import game."
    
    // The flattened code should be compilable (basic syntax check)
    result should not include("import game.model.Player\n\nimport game.model.Player")
  }
}
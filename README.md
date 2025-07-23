# Scala FlattenCode Plugin

*[Español](#descripción) | [English](#description)*

---

## Descripción

**FlattenCode** es un plugin de SBT que aplana código Scala eliminando declaraciones de paquetes e incluyendo todas las dependencias locales en un único archivo. Perfecto para plataformas de programación competitiva como **CodinGame**, **HackerRank**, **LeetCode**, etc.

## Características

- **Elimina declaraciones de paquetes** automáticamente
- **Incluye dependencias locales** de forma recursiva
- **Preserva imports externos** (scala.*, java.*, etc.)
- **Modo supervisión** con `~flattenCode` para regeneración automática
- **Diseñado específicamente** para competitive programming

## Instalación

### Opción 1: Instalación Local
```bash
# Clona el repositorio
git clone https://github.com/ccerdadiaz/scala-flatten-code-plugin.git
cd flatten-code-plugin

# Publica localmente
sbt publishLocal
```

### Opción 2: Como Dependencia (cuando esté publicado)
```scala
// En project/plugins.sbt
addSbtPlugin("edu.krlos" % "flatten-code-plugin" % "1.0.0")
```

## Configuración

Añade esto a tu `build.sbt`:

```scala
// Configuración requerida
flattenInput := file("src/main/scala/Solution.scala"),    // Archivo principal
flattenOutput := file("target/solution.scala")           // Archivo de salida
```

## Uso

```bash
# Ejecución única
sbt flattenCode

# Modo supervisión (regenera automáticamente al cambiar archivos)
sbt ~flattenCode
```

## Workflow para Competitive Programming

1. **Inicia supervisión**: `sbt ~flattenCode`
2. **Desarrolla normalmente** en múltiples archivos `.scala`
3. **El archivo se regenera automáticamente** cuando guardas cambios
4. **Copia y pega** el contenido del archivo de salida a la plataforma

## Ejemplo de Estructura

**Antes (múltiples archivos):**
```
src/main/scala/
├── Solution.scala          // import Player, Position
├── model/
│   ├── Player.scala        // import Position
│   └── Position.scala
└── utils/
    └── Helper.scala
```

**Después (archivo único):**
```scala
// target/solution.scala
import scala.collection.mutable.ListBuffer  // Preservado

case class Position(x: Int, y: Int)         // Incluido
case class Player(name: String, pos: Position)  // Incluido
object Helper { /* ... */ }                 // Incluido

object Solution {                           // Sin packages
  def main(args: Array[String]): Unit = {
    // Tu código listo para copiar/pegar
  }
}
```

## Limitaciones Conocidas

- **No soporta** imports con wildcard (`import com.example._`)
- **Solo procesa** `src/main/scala` (no `src/test/scala`)
- **Requiere configuración manual** de `flattenInput` y `flattenOutput`

## Contribuir

Las contribuciones son bienvenidas. Por favor:

1. Haz fork del proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## Licencia

Este proyecto está bajo la **Creative Commons Attribution-NonCommercial-ShareAlike 4.0** - ver el archivo [LICENSE](LICENSE) para detalles.

**En resumen:**
- **Uso personal y educativo** libre
- **Modificación y distribución** permitidas  
- **Mejoras deben compartirse** bajo la misma licencia
- **Perfecto para estudiantes** y competitive programming

---

## Description

**FlattenCode** is an SBT plugin that flattens Scala code by removing package declarations and including all local dependencies in a single file. Perfect for competitive programming platforms like **CodinGame**, **HackerRank**, **LeetCode**, etc.

## Features

- **Removes package declarations** automatically
- **Includes local dependencies** recursively  
- **Preserves external imports** (scala.*, java.*, etc.)
- **Watch mode** with `~flattenCode` for automatic regeneration
- **Specifically designed** for competitive programming

## Installation

### Option 1: Local Installation
```bash
# Clone the repository
git clone https://github.com/ccerdadiaz/scala-flatten-code-plugin.git
cd flatten-code-plugin

# Publish locally
sbt publishLocal
```

### Option 2: As Dependency (when published)
```scala
// In project/plugins.sbt
addSbtPlugin("edu.krlos" % "flatten-code-plugin" % "1.0.0")
```

## Configuration

Add this to your `build.sbt`:

```scala
// Required settings
flattenInput := file("src/main/scala/Solution.scala"),    // Main file
flattenOutput := file("target/solution.scala")           // Output file
```

## Usage

```bash
# Single execution
sbt flattenCode

# Watch mode (automatically regenerates when files change)
sbt ~flattenCode
```

## Competitive Programming Workflow

1. **Start watching**: `sbt ~flattenCode`
2. **Develop normally** in multiple `.scala` files
3. **File regenerates automatically** when you save changes
4. **Copy and paste** the output file content to the platform

## Example Structure

**Before (multiple files):**
```
src/main/scala/
├── Solution.scala          // import Player, Position
├── model/
│   ├── Player.scala        // import Position
│   └── Position.scala
└── utils/
    └── Helper.scala
```

**After (single file):**
```scala
// target/solution.scala
import scala.collection.mutable.ListBuffer  // Preserved

case class Position(x: Int, y: Int)         // Included
case class Player(name: String, pos: Position)  // Included
object Helper { /* ... */ }                 // Included

object Solution {                           // No packages
  def main(args: Array[String]): Unit = {
    // Your code ready to copy/paste
  }
}
```

## Known Limitations

- **Doesn't support** wildcard imports (`import com.example._`)
- **Only processes** `src/main/scala` (not `src/test/scala`)
- **Requires manual configuration** of `flattenInput` and `flattenOutput`

## Contributing

Contributions are welcome. Please:

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Licence

This project is licensed under **Creative Commons Attribution-NonCommercial-ShareAlike 4.0** - see the [LICENSE](LICENSE) file for details.

**Summary:**
- **Personal and educational use** free
- **Modification and distribution** allowed
- **Improvements must be shared** under the same licence  
- **Perfect for students** and competitive programming
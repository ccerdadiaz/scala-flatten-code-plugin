# Scala FlattenCode Plugin

*[Español](#descripción) | [English](#description)*

---

## Descripción

**FlattenCode** es un plugin de SBT que aplana código Scala eliminando declaraciones de paquetes e incluyendo todas las dependencias locales en un único archivo. Perfecto para plataformas de programación competitiva como **CodinGame**, **HackerRank**, **LeetCode**, etc.

## Características

- **Elimina declaraciones de paquetes** automáticamente
- **Incluye dependencias locales** de forma recursiva
- **Preserva imports externos** (scala.*, java.*, etc.)
- **Soporte completo para imports wildcards** (`import a.b._`)
- **Estructura de directorios libre** - organiza tus archivos como quieras
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
3. **Copia piezas reutilizables** de otros proyectos a cualquier directorio
4. **El archivo se regenera automáticamente** cuando guardas cambios
5. **Copia y pega** el contenido del archivo de salida a la plataforma

## Soporte para Imports

El plugin soporta tres tipos de imports basándose en las **declaraciones de paquete** de los archivos:

### Tipos de Import Soportados

**Import directo:**
```scala
import competitive.geometry.Point
// Incluirá el archivo que contenga la clase, objeto o trait Point
```

**Import agrupado:**
```scala
import competitive.geometry.{Point, Line}
// Incluirá los archivos que contengan las clases, objetos o traits Point y Line
```

**Import wildcard:**
```scala
import competitive.geometry._
// Incluirá todos los archivos que declaren package competitive.geometry
```

**Import wildcard de nivel superior:**
```scala
import competitive._
// Incluirá todos los archivos con cualquier sub-paquete de competitive.*
// (competitive.geometry, competitive.algorithms, etc.)
```

### Ventajas:
- **Organización libre**: Los archivos pueden estar en cualquier directorio
- **Reutilización fácil**: Copia archivos de otros proyectos sin reorganizar

## Ejemplo de Estructura

**Antes (múltiples archivos):**
```
src/main/scala/
├── Solution.scala              // import competitive.geometry._
├── geom/Point.scala           // package competitive.geometry
├── geom/Line.scala            // package competitive.geometry  
├── algo/ConvexHull.scala      // package competitive.algorithms
└── utils/MathHelper.scala     // package competitive.utils
```

**Después (archivo único):**
```scala
// target/solution.scala
import scala.io.StdIn                    // Preservado (externo)

case class Point(x: Int, y: Int)         // Incluido por wildcard
case class Line(p1: Point, p2: Point)   // Incluido por wildcard
object ConvexHull { /* ... */ }          // Incluido por import específico

object Solution {                        // Sin packages
  def main(args: Array[String]): Unit = {
    // Tu código listo para copiar/pegar
  }
}
```

## Limitaciones Conocidas

- **Requiere configuración manual** de `flattenInput` y `flattenOutput`
- **Puede incluir clases no utilizadas** cuando se usan wildcards (comportamiento no óptimo pero consentido por simplicidad)

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

## Nota de Desarrollo

Este proyecto ha sido desarrollado con la asistencia de **Claude** (Anthropic) como asistente de codificación, facilitando la implementación de algoritmos y creación de pruebas unitarias.

---

## Description

**FlattenCode** is an SBT plugin that flattens Scala code by removing package declarations and including all local dependencies in a single file. Perfect for competitive programming platforms like **CodinGame**, **HackerRank**, **LeetCode**, etc.

## Features

- **Removes package declarations** automatically
- **Includes local dependencies** recursively  
- **Preserves external imports** (scala.*, java.*, etc.)
- **Full wildcard import support** (`import a.b._`)
- **Free directory structure** - organize your files however you want
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
3. **Copy reusable pieces** from other projects to any directory
4. **File regenerates automatically** when you save changes
5. **Copy and paste** the output file content to the platform

## Import Support

The plugin supports three types of imports based on **package declarations** in files:

### Supported Import Types

**Direct import:**
```scala
import competitive.geometry.Point
// Includes the file containing the Point class, object or trait
```

**Grouped import:**
```scala
import competitive.geometry.{Point, Line}
// Includes files containing the Point and Line classes, objects or traits
```

**Wildcard import:**
```scala
import competitive.geometry._
// Includes all files that declare package competitive.geometry
```

**Top-level wildcard import:**
```scala
import competitive._
// Includes all files with any competitive.* sub-package
// (competitive.geometry, competitive.algorithms, etc.)
```

### Advantages:
- **Free organization**: Files can be in any directory
- **Easy reuse**: Copy files from other projects without reorganizing

## Example Structure

**Before (multiple files):**
```
src/main/scala/
├── Solution.scala              // import competitive.geometry._
├── geom/Point.scala           // package competitive.geometry
├── geom/Line.scala            // package competitive.geometry  
├── algo/ConvexHull.scala      // package competitive.algorithms
└── utils/MathHelper.scala     // package competitive.utils
```

**After (single file):**
```scala
// target/solution.scala
import scala.io.StdIn                    // Preserved (external)

case class Point(x: Int, y: Int)         // Included by wildcard
case class Line(p1: Point, p2: Point)   // Included by wildcard
object ConvexHull { /* ... */ }          // Included by specific import

object Solution {                        // No packages
  def main(args: Array[String]): Unit = {
    // Your code ready to copy/paste
  }
}
```

## Known Limitations

- **Requires manual configuration** of `flattenInput` and `flattenOutput`
- **May include unused classes** when using wildcards (non-optimal but accepted for simplicity)

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

---

## Development Note

This project has been developed with the assistance of **Claude** (Anthropic) as a coding assistant, facilitating algorithm implementation and unit test creation.
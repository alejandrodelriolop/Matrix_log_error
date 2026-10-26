# Matrix — Fase 1: contrato de datos

Proyecto Maven para establecer las cuatro persistencias del contrato de datos:

- `datos/operaciones.txt`: histórico secuencial con eventos separados por `|` y fecha ISO 8601.
- `datos/nodos.dat`: un registro binario fijo de 28 bytes por nodo, consultado/modificado mediante `RandomAccessFile`.
- `datos/estado.xml`: estado actual y relaciones del mundo Matrix, procesado con DOM.
- `datos/reglas.json`: reglas y umbrales, leídos con Jackson.

## Requisitos

- JDK 21 o superior.
- Maven.
- IntelliJ IDEA (opcional).

## Ejecutar

Desde la carpeta raíz del proyecto:

```bash
mvn clean compile exec:java
```

La primera ejecución crea los ficheros que falten y muestra una comprobación de lectura de las cuatro persistencias. No sobrescribe el XML, JSON ni TXT que ya existan. El BIN se crea con cuatro registros si todavía no existe o está vacío.

## Notas

Esta fase solo implementa el contrato y las operaciones básicas de persistencia. Todavía no implementa el simulador de 30 segundos, los seis incidentes, Log4j, el analizador forense ni las salidas

# Matrix — Fase 2: simulador básico

Proyecto Maven para la práctica de persistencia de datos de Matrix.

## Qué incluye esta fase

- Conserva las cuatro persistencias de la fase 1.
- `SimuladorMain`: menú de inicio y selección entre modo normal y modo de pruebas.
- `SimuladorMatrix`: coordina una simulación de 30 segundos.
- Genera operaciones normales de conexión, acceso, inspección y comprobación de integridad.
- Añade cada operación a `datos/operaciones.txt`.
- Actualiza un registro fijo de `datos/nodos.dat` mediante `RandomAccessFile`.
- Actualiza el estado del nodo correspondiente en `datos/estado.xml`.
- Lee `datos/reglas.json` y comprueba que los datos iniciales son válidos.

**Importante:** el modo de pruebas está preparado como opción de ejecución, pero la selección e inyección real de los seis incidentes se implementará en la fase 3. Esta fase todavía no incorpora Log4j ni el analizador forense.

## Requisitos

- JDK 21 o superior.
- Maven.
- IntelliJ IDEA (opcional).

## Preparar los datos

Desde la raíz del proyecto, ejecutar una vez:

```bash
mvn clean compile exec:java
```

Este comando ejecuta `InicializarDatosMain`. Si ya existen datos, no los reinicia automáticamente.

## Ejecutar el simulador

Desde la raíz del proyecto:

```bash
mvn compile exec:java -Dexec.mainClass=es.matrix.datos.SimuladorMain
```

Elegir `1` para simulación normal o `2` para el modo de pruebas. La ejecución dura 30 segundos y añade operaciones al histórico existente. Para volver a probar desde un estado limpio, guarda una copia de `datos/` y ejecuta la inicialización con archivos de datos vacíos o restaurados.

## Archivos de datos

- `datos/operaciones.txt`: histórico secuencial, seis campos separados por `|`.
- `datos/nodos.dat`: cuatro registros binarios de 28 bytes, modificados mediante acceso directo.
- `datos/estado.xml`: estado actual y relaciones del mundo Matrix.
- `datos/reglas.json`: umbrales y reglas de configuración.

## Límites conocidos de la fase 2

- No implementa todavía los seis incidentes ni sus excepciones críticas.
- El modo de pruebas aún no permite seleccionar un incidente concreto.
- No incluye la configuración de Log4j ni el analizador forense.
- Las operaciones son deliberadamente básicas; los cambios de zona, permisos y consecuencias de incidentes se añadirán en las fases posteriores.
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
# Matrix — Fase 1: contrato de datos

Proyecto Maven para establecer las cuatro persistencias del contrato de datos:

- `datos/operaciones.txt`: histórico secuencial con eventos separados por `|` y fecha ISO 8601.
- `datos/nodos.dat`: un registro binario fijo de 28 bytes por nodo, consultado/modificado mediante `RandomAccessFile`.
- `datos/estado.xml`: estado actual y relaciones del mundo Matrix, procesado con DOM.
- `datos/reglas.json`: reglas y umbrales, leídos con Jackson.

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

## Incidentes de prueba
1. Pérdida de sincronización.
2. Comportamiento anómalo de un sujeto.
3. Acceso no autorizado.
4. Fallo de nodo.
5. Agente fuera de protocolo.
6. Alteración de zona.

Al inyectar un incidente, el simulador persiste evidencia en los archivos TXT, BIN y/o XML correspondientes y después lanza `EventoCriticoException`, que detiene la ejecución de forma controlada. No se escribe una etiqueta explícita con el número/tipo de incidente en los archivos: el analizador forense deberá deducirlo cruzando las evidencias.

## Importante
- Ejecuta las configuraciones con el directorio de trabajo en la raíz del proyecto (la carpeta que contiene `pom.xml` y `datos/`).
- Los datos se conservan entre ejecuciones. Si quieres repetir una prueba desde un estado inicial, haz copia de seguridad de `datos/` y vuelve a inicializar solo los archivos que corresponda.
- El modo normal incluye una probabilidad pequeña de incidente aleatorio.

## Pendiente para fases posteriores
Configuración de Log4j, analizador forense independiente, informes TXT/BIN/JSON, pruebas JUnit y documentación final.

## Fase 4: logging con Log4j 2

Esta fase añade Log4j 2 con configuración externa en `src/main/resources/log4j2.xml`.

- `logs/runtime/matrix-runtime.log`: registro técnico de ejecución. Al superar 5 MB rota a archivos `.gz`; se conservan hasta 10 archivos rotados por política de Log4j.
- `logs/evidencias/`: crea un archivo de evidencias por ejecución, con identificador basado en fecha y hora. Registra eventos persistidos e incidentes después de guardar sus evidencias en los archivos de datos.
- La evidencia de un incidente se guarda primero en TXT/XML/BIN según corresponda; después se registra el incidente y se lanza `EventoCriticoException`. El manejador de la excepción registra la parada controlada.

### Ejecutar

1. Abre la carpeta raíz del proyecto como proyecto Maven.
2. Comprueba que el directorio de trabajo sea la raíz del proyecto, donde están `pom.xml` y `datos/`.
3. Ejecuta `InicializarDatosMain` si necesitas inicializar los archivos.
4. Ejecuta `SimuladorMain`. Para reproducir un incidente, selecciona el modo 2.
5. Revisa `logs/evidencias/` y `logs/runtime/`.

No borres ni reinicialices los archivos de datos si necesitas conservar evidencias de una ejecución anterior. Los logs se excluyen de Git según `.gitignore`, así que conserva aparte los logs de las ejecuciones relevantes que exige la práctica.

## Fase 5 — Analizador forense independiente

Se añade `es.matrix.forense.AnalizadorForenseMain`, un segundo punto de entrada separado del simulador. Lee `datos/operaciones.txt`, `datos/nodos.dat`, `datos/estado.xml` y `datos/reglas.json` desde disco, sin reutilizar objetos en memoria del simulador.

### Ejecutar
1. Ejecuta el simulador y selecciona un incidente en modo de pruebas.
2. Cuando el simulador se detenga, ejecuta `AnalizadorForenseMain`.
3. Confirma con `S` para comenzar el análisis.
4. Consulta los tres resultados en `resultados/`:
    - `informe.txt`: conclusión, evidencias y nivel de certeza.
    - `anomalias.dat`: registros binarios relevantes para el hallazgo.
    - `entidades_afectadas.json`: subconjunto de entidades afectadas encontrado en el XML.

El analizador no usa los logs de Log4j como fuente de detección. Si no encuentra evidencias concluyentes, informa `INDETERMINADO` en vez de afirmar un incidente sin pruebas. Los archivos de resultados se sobrescriben en cada análisis.

# Matrix — Fase 6: pruebas y documentación

Esta fase añade una primera batería de pruebas automatizadas con JUnit 5 al proyecto de la Fase 5. No sustituye las pruebas manuales de los seis incidentes; permite comprobar las piezas principales de persistencia de forma aislada.

## Ejecutar las pruebas
1. Abre esta carpeta como proyecto Maven.
2. Espera a que Maven sincronice las dependencias.
3. Ejecuta `mvn test` desde el terminal de IntelliJ, o abre `src/test/java` y ejecuta las clases de prueba.

Las pruebas usan carpetas temporales de JUnit; no deberían modificar los archivos reales de `datos/`.

## Pruebas incluidas
- Conversión de eventos a línea TXT y recuperación del evento.
- Rechazo de líneas TXT inválidas y campos con separadores.
- Escritura, lectura y modificación de registros BIN mediante acceso directo.
- Rechazo de archivos BIN incompletos y de IDs inexistentes.
- Validación de reglas JSON válidas y fuera de rango.
- Actualización de estado XML y rechazo de estados inválidos.

## Ejecutar la aplicación
- Inicialización: `es.matrix.datos.InicializarDatosMain`
- Simulador: `es.matrix.datos.SimuladorMain`
- Analizador: `es.matrix.forense.AnalizadorForenseMain`

No ejecutes `InicializarDatosMain` entre la simulación y el análisis si necesitas conservar las evidencias generadas.

## Limitaciones conocidas
Esta batería verifica componentes de persistencia de forma aislada. Antes de entregar, ejecuta manualmente los seis incidentes desde el modo de pruebas y comprueba los tres artefactos de `resultados/`. También hay que revisar que el analizador distingue incidentes cuando se acumulan datos de varias ejecuciones. La compilación debe verificarse en el entorno local con JDK 21 y Maven.

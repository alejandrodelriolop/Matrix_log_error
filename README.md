# Matrix — simulador y analizador forense

Repositorio: <https://github.com/alejandrodelriolop/Matrix_log_error>

Práctica UD1 de Acceso a Datos (DAM): manejo de ficheros en Java. Dos programas independientes:

- **Simulador**: representa la operativa de una simulación *Matrix* (sujetos conectados, agentes, zonas y nodos),
  persiste lo que ocurre en TXT, BIN y XML y puede provocar uno de seis incidentes.
- 
- **Analizador forense**: lee **solo los ficheros** (no recibe el tipo de incidente ni usa los logs de Log4j),
  relaciona las tres fuentes y determina qué incidente es compatible con los datos.

## Requisitos

- JDK 21 o superior y Maven 3.9+.
- Dependencias (las descarga Maven): Jackson 2.22.4 (JSON), Log4j 2.26.1, JUnit 5.11.4 (pruebas).

## Estructura

```
src/main/java/es/matrix/
  simulador/      SimuladorMain, SimuladorMatrix, InicializarDatosMain
  forense/        AnalizadorForenseMain, AnalizadorForense
  persistencia/   TextEventRepository (TXT), BinaryNodeRepository (BIN), XmlWorldRepository (XML/DOM), JsonRulesRepository (JSON)
  incidentes/     IncidenteMatrix, EventoCriticoException
  modelo/         Evento, NodoRegistro, ResultadoForense
  configuracion/  RunLogging, DemoRotacionLogsMain
src/main/resources/log4j2.xml   configuración externa de Log4j 2
src/test/java/                  pruebas JUnit 5
datos/                          ficheros que genera el simulador (ejemplo incluido)
resultados/                     ficheros que genera el analizador (ejemplo incluido)
logs/runtime/                   log técnico con rotación (.log.gz)
logs/evidencias/                un log de evidencia por ejecución
docs/OBSERVABILIDAD.md          investigación del Anexo II
```

## Ficheros de datos y cómo se relacionan

| Fichero | Formato | Responsabilidad |
|---|---|---|
| `datos/operaciones.txt` | TXT | **Histórico** secuencial: `idEvento\|idEjecucion\|fechaHora\|actorId\|tipoOperacion\|objetivoId\|resultado` (ISO 8601). |
| `datos/nodos.dat` | BIN | **Mediciones actuales** de cada nodo: registros fijos de 28 bytes (`id`, `sincronización`, `carga`, `código de estado`, `nº operaciones`) con `RandomAccessFile`: se añade, se lee y se modifica por posición `(id-1)*28` con `seek()`. |
| `datos/estado.xml` | XML (DOM) | **Estructura y estado**: sujetos, agentes, zonas, nodos y permisos. |
| `datos/reglas.json` | JSON (Jackson) | **Umbrales**: sincronización mínima (80), carga máxima (90), integridad mínima (80). |

Claves de relación: `actorId`/`objetivoId` del TXT son IDs del XML (sujeto, agente, zona, nodo);
`idBinario` de cada `<nodo>` del XML es el ID del registro en el BIN; `<nodo zonaId>` une cada zona con su nodo;
`idEjecucion` (`RUN-…`) agrupa los eventos de una ejecución (los eventos antiguos de 6 campos se leen como `LEGACY`).

## Incidentes (6)

Ningún fichero contiene el nombre del incidente: cada uno deja una huella distinta y coherente en TXT, BIN y XML.

| # | Incidente | TXT | BIN (nodo afectado) | XML |
|---|---|---|---|---|
| 1 | Pérdida de sincronización | `COMPROBACION_SINCRONIZACION … ALERTA` sobre un nodo | sincronización < mínimo | nodo `DEGRADADO` |
| 2 | Comportamiento anómalo de un sujeto | ≥ 3 `CAMBIO_ZONA` seguidos del mismo sujeto | carga > máximo en el nodo de la zona final | sujeto `ANOMALO` en una zona sin permiso |
| 3 | Acceso no autorizado | `ACCESO` de un sujeto a una zona | carga > máximo en el nodo de esa zona | **no existe** `<permiso>` de ese sujeto para esa zona |
| 4 | Fallo de nodo | `OPERACION_NODO … FALLO` | código de estado 2 (sincronización 0, carga 100) | nodo `FALLO` |
| 5 | Agente fuera de protocolo | `ACCION_AGENTE` en una zona | carga > máximo en el nodo de esa zona | la zona difiere de su `zonaAsignada`; agente `FUERA_PROTOCOLO` |
| 6 | Alteración de zona | `COMPROBACION_INTEGRIDAD … ALERTA` sobre una zona | sincronización del nodo de la zona < mínimo | integridad de la zona < mínimo, estado `ALTERADA` |

### Cómo decide el analizador

1. Toma la **última ejecución** del histórico (`idEjecucion`), de modo que un incidente antiguo no se confunde con uno nuevo.
2. Busca el síntoma en el TXT y lo **contrasta** con el BIN (lectura directa por `seek`) y con el XML, usando los umbrales de `reglas.json`.
3. Acepta un incidente solo si **al menos dos fuentes** coinciden: certeza `ALTA` (TXT+BIN+XML) o `MEDIA` (dos fuentes).
   Si no hay evidencia suficiente devuelve `INDETERMINADO` y lista las anomalías residuales que encuentre.

## Ejecución

Desde la raíz del proyecto (el directorio de trabajo debe ser la carpeta con `pom.xml` y `datos/`).

```bash
mvn compile

# 1. Crear los ficheros de datos iniciales (no sobrescribe los existentes)
mvn -q exec:java -Dexec.mainClass=es.matrix.simulador.InicializarDatosMain

# 2a. Simulador — modo normal (30 s, con un 3 % de probabilidad de incidente aleatorio por ciclo)
mvn -q exec:java -Dexec.mainClass=es.matrix.simulador.SimuladorMain -Dexec.args="--modo=normal --duracion=30"

# 2b. Simulador — modo de pruebas: provoca un incidente concreto (1..6, ver tabla)
mvn -q exec:java -Dexec.mainClass=es.matrix.simulador.SimuladorMain -Dexec.args="--modo=pruebas --incidente=3 --duracion=10"

# 2c. O bien el menú interactivo (1 = inicializar, 2 = simulador)
mvn -q exec:java

# 3. Analizador forense (programa independiente; --auto evita la confirmación S/N)
mvn -q exec:java -Dexec.mainClass=es.matrix.forense.AnalizadorForenseMain -Dexec.args="--auto"

# 4. Demostración de rotación de logs
mvn -q exec:java -Dexec.mainClass=es.matrix.configuracion.DemoRotacionLogsMain
```

Salidas del analizador en `resultados/`:

- `informe.txt` — incidente, certeza, efectos detectados, conclusión y evidencias.
- `anomalias.dat` — BIN con **solo** los registros de nodo anómalos usados en el análisis (mismo formato de 28 bytes).
- `entidades_afectadas.json` — **solo** los elementos del XML que intervienen (zona, nodo, sujeto, permisos…).

No ejecutes la inicialización entre la simulación y el análisis si quieres conservar las evidencias.
Los datos se acumulan entre ejecuciones; para empezar de cero borra `datos/` y vuelve a inicializar.

## Pruebas automatizadas

```bash
mvn test
```

JUnit 5 con directorios temporales (`@TempDir`) y semillas fijas. Cubren: TXT, BIN con `RandomAccessFile`
(alta, lectura directa y modificación), XML, JSON, la excepción crítica de cada incidente (parametrizada),
la identificación de los seis incidentes por el analizador (parametrizada), el uso exclusivo de la última ejecución,
el caso `INDETERMINADO`, las salidas del analizador, el flujo completo
datos → incidente → persistencia → análisis → salidas y la rotación real de logs de Log4j.

## Logs (Log4j 2)

Configuración externa en `src/main/resources/log4j2.xml`. Cada línea lleva fecha y hora, nivel, logger y mensaje.

- **Evidencias por ejecución**: `logs/evidencias/matrix-<tipo>-AAAAMMDD-HHmmss-SSS.log` (simulador, inicialización y analizador).
- **Log técnico con rotación**: `logs/runtime/matrix-runtime.log`.
   - *¿Qué provoca el rollover?* Que el fichero activo alcance **2 KB** (`SizeBasedTriggeringPolicy`), pequeño a propósito para demostrarlo.
   - *¿Cómo se nombran los históricos?* `matrix-runtime-AAAA-MM-DD-N.log.gz` (fecha + índice).
   - *¿Cuántos se conservan?* **10** (`DefaultRolloverStrategy max="10"`): suficiente para tener historia reciente sin crecer sin límite.
   - *¿Para qué comprimir?* Los históricos ocupan mucho menos y se archivan o copian más rápido.
   - *¿Log activo vs rotado?* El activo es el que se sigue escribiendo; el rotado está cerrado, comprimido y ya no cambia.
- Los datos de la anomalía se persisten **antes** de lanzar `EventoCriticoException`; el `catch` solo registra con Log4j
  y finaliza de forma controlada.

## Decisiones de diseño

- Java I/O clásico (`RandomAccessFile`, `BufferedReader/Writer`) y NIO.2 (`Path`, `Files`) combinados.
- Rutas relativas y `Files.createDirectories` antes de usar cada carpeta; recursos con try-with-resources.
- DOM para el XML (estructura pequeña que se consulta y modifica en memoria) y Jackson para las reglas.
- Registros BIN de longitud fija para poder calcular la posición con `seek()` sin recorrer el fichero.

Ver [OBSERVABILIDAD.md](OBSERVABILIDAD.md).

# Uso de inteligencia artificial

## Herramientas utilizadas

### Herramienta 1
- Nombre: ChatGPT (OpenAI)
- Forma de uso: chat web. Consultas puntuales sobre requisitos, diseño, Java I/O, NIO.2, `RandomAccessFile`,
  excepciones, Log4j 2 y JUnit 5; revisión de fragmentos de código y localización de errores.

### Herramienta 2
- Nombre: Claude (Anthropic)
- Forma de uso: chat / agente de código. Revisión de la práctica contra el enunciado y reescritura de partes del proyecto
  (simulador, analizador forense, pruebas y documentación) a partir de esa revisión.

Las propuestas de la IA no se han dado por buenas sin más: se han revisado, adaptado al proyecto y comprobado
ejecutando el programa y las pruebas. La responsabilidad sobre el código entregado es del autor.

## Prompts principales

1. Estoy en segundo de DAM y necesito una guía para plantear el esquema e interconectar los servicios de una aplicación desde cero.
2. Ayúdame a diseñar el proyecto Matrix antes de empezar a crear las clases.
3. Define el contrato de datos de Matrix: formato temporal ISO 8601, XML como estado actual y BIN como registros de los nodos.
4. Explícame la responsabilidad de cada clase y cómo se relacionan los componentes.
5. Revisa este fragmento de código y ayúdame a localizar el error.
6. Explícame cómo debe funcionar el simulador y cómo debe leer sus datos el analizador forense.
7. Explícame cómo organizar los datos TXT, BIN, XML y JSON para que cada formato tenga una responsabilidad diferenciada.
8. Ayúdame a definir los campos de los registros de eventos y la identificación de cada ejecución.
9. Explícame cómo utilizar RandomAccessFile para consultar y modificar registros mediante acceso directo.
10. Ayúdame a revisar el tratamiento de excepciones y el registro técnico con Log4j 2.
11. Explícame cómo registrar una anomalía antes de lanzar o capturar una excepción.
12. Ayúdame a preparar pruebas automatizadas con JUnit 5.
13. Puedes revisar mi práctica y lo que se pide, y ver si me he dejado algo sin hacer?

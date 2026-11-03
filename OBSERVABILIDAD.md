# Del log local a la observabilidad (Anexo II)

Plataforma elegida: **Grafana Alloy + Loki + Grafana**.

Si Matrix se desplegara en producción, el recorrido sería:

```
Matrix (Log4j 2) → logs/runtime/matrix-runtime.log → rotación .gz → Alloy → Loki → Grafana (búsquedas, paneles, alertas)
```

- **¿Cómo llegan los logs de la aplicación a la plataforma?** 
- Log4j 2 escribe `matrix-runtime.log` (o JSON por stdout en un contenedor).
  Un agente instalado junto a la aplicación lee esos ficheros y los envía a Loki por HTTP.

- **¿Dónde se almacenan y cómo se consultan?** 
- En Loki, que indexa solo etiquetas (aplicación, entorno, nivel) y guarda el texto
  comprimido, lo que lo hace barato. Se consultan desde Grafana con LogQL, por ejemplo `{app="matrix"} |= "ERROR"`.

- **¿Qué componente actúa como collector o agente?** **Grafana Alloy**. 
- Promtail alcanzó fin de vida el 02/03/2026 y Grafana dirige las nuevas implementaciones hacia Alloy. 
  Alloy lee los ficheros (incluida la rotación), añade etiquetas y reenvía.

- **¿Permite crear dashboards y alertas?** 
  Sí. Grafana muestra paneles (errores por minuto, incidentes por tipo) y las reglas de
  alerta avisan, por ejemplo, cuando aparece `Evento crítico capturado` en el log.

- **¿Trabaja solo con logs o también con métricas y trazas?** 
- Loki es para logs, pero Grafana los combina con métricas (Prometheus/Mimir) y trazas (Tempo), 
- y Alloy puede recoger las tres señales (también como distribución de OpenTelemetry).

- **¿Qué problema resuelve que no resuelve abrir `aplicacion.log`?** 
  Centralizar los logs de muchas instancias o servidores, buscarlos y filtrarlos en segundos, 
  correlacionarlos con métricas y trazas, conservarlos aunque el servidor desaparezca,
  aplicar retención y recibir alertas sin que nadie tenga que abrir el fichero.

En Matrix un mensaje útil llevaría el identificador de ejecución (`idEjecucion=RUN-…`), que haría de *correlationId* para
reunir todos los logs de una misma simulación.

# Presentación de las exportaciones

Identidad de TaskTracker: azul atlántico `#0369a1`, tinta `#0c2e42` y hielo `#f0f9ff`. `ExcelReportStyle` comparte encabezados, filas alternadas, formato de impresión A4, fechas, numeración y colores semánticos en tareas, horas extras e incidentes. Se reutiliza Apache POI; no se agregan dependencias.

## Vista de tareas

`POST /api/tasks/export/view` recibe entre 1 y 10.000 filas `{ "id": 123, "level": 0 }`, con identificadores positivos y niveles de 0 a 100. Devuelve un `.xlsx` con el orden solicitado, sangría de jerarquía, filtros, encabezados inmovilizados, fechas reales y porcentajes numéricos. Obtiene los valores desde `TaskService`, con la misma sesión y protección CSRF de las otras API. No escribe datos.

El frontend debe integrarse junto a este backend. El antiguo endpoint de respaldo CSV y su importación conservan el contrato existente. El Excel de la vista se presenta como informe; no reemplaza ese respaldo.

## Horas extras e incidentes

Se conservan los nombres de hojas, el orden de columnas y los encabezados en la primera fila para permitir su reimportación. Horas extras incorpora fechas Excel y horas con dos decimales; mantiene el total al final y todos los registros que se descargaban anteriormente. Incidentes conserva su hoja de instrucciones y las validaciones de listas.

## Verificación

Java 21: `mvn -Dtest=TaskViewExcelApiTest,OvertimeExcelApiTest,IncidentExcelApiTest,HierarchyAndOvertimeApiTest test`.

Las 20 pruebas cubren exportación por selección y orden, jerarquía, tipos de datos, autenticación, CSRF y los contratos de importación existentes. Para guardar ejemplos con datos ficticios, añadir `-Dexport.samples=<directorio>` a la ejecución de `TaskViewExcelApiTest`.

El 2 de noviembre de 2026 se verificaron las descargas del frontend compilado en Chromium, escritorio de 1440 px y móvil de 390 px, temas claro y oscuro, y recuperación ante un fallo de exportación. Estas comprobaciones utilizaron respuestas simuladas; las pruebas Java utilizaron H2 aislada. Los tres tipos de PDF se renderizaron y revisaron, incluida la presencia de todos los registros y la numeración completa. Los Excel se abrieron y renderizaron con LibreOffice. No se modificaron datos ni configuración de producción.

La integración a `main` y el despliegue quedan sujetos a la autorización explícita definida en el `AGENTS.md` de la carpeta del proyecto en el servidor.

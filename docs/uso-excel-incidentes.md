# Cargar y descargar incidentes por Excel

## Uso

1. En Incidentes, selecciona un proyecto en el filtro Proyecto.
2. Descarga **Plantilla Excel** y completa una fila por defecto en la hoja **Incidentes**. También puedes usar el ejemplo con los seis defectos reportados por Susana Antilaf.
3. Pulsa **Subir Excel y reemplazar**. Si Ambiente o Reportante están vacíos en la planilla, completa sus valores por defecto en el formulario. Prioridad vacía se carga como Media.
4. Pulsa **Validar archivo y ver resumen**. Corrige cualquier error indicado por fila; hasta este punto no se modifica el sistema.
5. Revisa el proyecto, las filas válidas y el número de incidentes actuales. Confirma el reemplazo y pulsa **Reemplazar y cargar**.
6. **Descargar Excel del proyecto** exporta todos los incidentes del proyecto seleccionado, independientemente de los otros filtros de pantalla. El archivo tiene el mismo formato y se puede volver a subir.

La importación elimina los incidentes, comentarios, historial y adjuntos anteriores del proyecto seleccionado. Los demás proyectos se conservan. Todos los incidentes nuevos comienzan en Registrado; no se restauran identificadores ni aprobaciones antiguas. Las imágenes y PDF deben adjuntarse nuevamente después de la carga.

## Formato estándar

Hoja **Incidentes**, con encabezados en la primera fila:

| Columna | Uso |
| --- | --- |
| Proyecto | Opcional; si se completa debe coincidir con el proyecto seleccionado. |
| Título | Obligatorio, máximo 255 caracteres. |
| Descripción | Obligatoria. |
| Ambiente | DESA, CERT, UAT o PROD. Obligatorio en la fila o como valor por defecto al subir. |
| Prioridad | Baja, Media, Alta o Crítica. Vacío aplica Media. |
| Reportante | Nombre; obligatorio en la fila o como valor por defecto al subir. |
| Desarrollo (email) | Opcional, email de un miembro existente. |
| QA (email) | Opcional, email de un miembro existente. |
| Tarea ID | Opcional, identificador entero de una tarea del mismo proyecto. |
| Fecha objetivo | Opcional. |
| Pasos reproducción | Opcional. |
| Resultado esperado | Opcional. |
| Resultado obtenido | Opcional. |
| Estado reportado | Referencia del reporte de QA, sin cambiar el estado del sistema ni aprobar etapas. |
| Fecha entrega QA | Fecha informada por QA, sin certificar la corrección. |
| Observaciones | Notas del reporte. |
| Estado actual (referencia) | Estado al descargar; al volver a subir se conserva como observación, no como aprobación. |

Las fechas admiten celdas de fecha de Excel o texto dd/mm/aaaa, dd-mm-aaaa y aaaa-mm-dd. No se permiten fórmulas en las filas de datos. Máximo 2000 filas y 10 MB. Los encabezados admiten diferencias de mayúsculas y tildes. Un archivo vacío no elimina el listado.

## Ejemplo de Susana

Se prepararon seis filas de defectos, todas con reportante Susana Antilaf y estado reportado Pendiente validación QA. Las fechas de entrega se conservan: 29 y 30 de septiembre, y 2 de octubre de 2026.

El reporte no indica proyecto, prioridades ni emails de responsables: esas celdas se dejaron vacías. El usuario indicó UAT para las seis incidencias y el ejemplo ya incluye ese ambiente. Se debe seleccionar el proyecto al subir. Si no se completa prioridad, se aplica Media. Los resultados OK, en ejecución y pendientes de re-ejecución se conservaron como contexto en Instrucciones; no se convirtieron en defectos adicionales. No se asignó una fecha a “mañana”, porque el reporte no incluye la fecha desde la que se expresó.

## Implementación y verificación

- API nueva: GET `/api/incidents/excel/template`, GET `/api/incidents/excel/export`, POST `/api/incidents/excel/preview` y POST `/api/incidents/excel/import`.
- Todas las operaciones requieren `projectId`. Validación/importación reciben multipart `file`, `environment` y `reporter` opcionales. Importación también requiere `confirmed=true` y `fingerprint` del resumen validado.
- Se valida todo el archivo antes de borrar datos. El reemplazo se realiza en una sola transacción; ante cualquier fallo se recupera todo el contenido anterior.
- Si los incidentes cambian después de validar, el resumen queda desactualizado y debe validarse nuevamente. Los cambios del proyecto se coordinan con bloqueo transaccional.
- Nuevos datos persistidos por incidente: `reported_status`, `qa_delivery_date`, `import_notes`. La configuración actual de actualización de esquema los crea al arrancar el backend; ambientes con migraciones controladas deben crear estas columnas antes del despliegue.
- Se verificó la compatibilidad con la planilla entregada, la vuelta descarga/importación, las fechas, los responsables por email, los errores por fila, el aislamiento entre proyectos, la conservación ante errores y la recuperación ante fallos de almacenamiento.
- Pruebas sobre H2 aislado, sin modificar la base real. Debe completarse certificación en PostgreSQL del ambiente real antes del pase.

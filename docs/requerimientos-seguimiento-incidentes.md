# Seguimiento de incidentes — requerimientos funcionales

Estado: requerimientos definidos con el usuario e implementados localmente. Fecha: 13 de octubre de 2026.

## Base revisada

- Backend: taskTrackerBack, Java 21 y Spring Boot, persistencia JPA/PostgreSQL.
- Frontend: taskTrackerFront, Angular y Angular Material.
- Ambos repositorios en main, sincronizados con origin/main mediante pull, sin cambios locales al iniciar esta revisión.
- Tareas ofrece tabla, tablero, filtros, responsables, comentarios y exportación.
- Miembros del equipo dispone de un campo de rol; no se encontró autenticación ni control de permisos por usuario en el código revisado.

## Requerimientos confirmados

RF-01. Incorporar un apartado de seguimiento de incidentes con una experiencia similar a la pestaña de tareas.

RF-02. Contemplar los ambientes DESA, CERT, UAT y PROD. UAT corresponde a las pruebas del cliente.

RF-03. Toda corrección debe contar con certificación de QA en CERT antes de pasar a UAT.

RF-04. Definir primero los requerimientos funcionales mediante preguntas al usuario y luego implementar el módulo.

RF-05. Registrar errores detectados en cualquiera de los cuatro ambientes: DESA, CERT, UAT y PROD.

RF-06. Registrar aprobaciones seleccionando al responsable del equipo, sin agregar login por ahora. Esta selección registra una declaración y no verifica la identidad de quien opera el sistema.

RF-07. Exigir certificación QA en CERT y aprobación del cliente en UAT para todas las correcciones antes del paso a PROD y cierre.

RF-08. Si QA o cliente rechaza, devolver la corrección a desarrollo y exigir una nueva certificación QA antes de volver a UAT.

RF-09. Todo incidente debe pertenecer a un proyecto. La vinculación con una tarea existente es opcional.

RF-10. Exigir al registrar título, descripción, ambiente de detección, prioridad y reportante, además del proyecto. Permitir completar posteriormente pasos de reproducción, resultado esperado/obtenido, responsable de desarrollo y QA.

RF-11. Permitir adjuntar imágenes de evidencia tanto del incidente como de su corrección. Conservar la distinción entre ambas evidencias.

RF-12. Exigir una observación para aprobar en CERT y UAT. Los enlaces y archivos de evidencia son opcionales.

RF-13. Usar prioridades baja, media, alta y crítica, como en tareas. La fecha objetivo es opcional; no se requieren plazos automáticos ni severidad independiente.

RF-14. Llevar historial de estados y observaciones, sin número de intento ni versión de corrección. Al volver a desarrollo, conservar las aprobaciones previas como historial y exigir nuevas aprobaciones de QA y cliente para avanzar en el recorrido actual.

RF-15. Cerrar el incidente al registrar el despliegue en PROD, después de la certificación QA en CERT y aprobación del cliente en UAT. No se exige una validación adicional en PROD.

RF-16. La primera entrega debe incluir tabla y tablero, filtros, comentarios, historial e imágenes. Exportación e indicadores adicionales del dashboard no forman parte del alcance acordado para esta entrega.

RF-17. Registrar reportante y aprobador del cliente mediante nombre escrito. Seleccionar responsables de desarrollo y QA entre los miembros del equipo existente.

RF-18. Permitir reabrir incidentes cerrados, volviendo a desarrollo y exigiendo nuevamente el recorrido de aprobaciones. Exigir motivo y conservar el historial.

RF-19. Permitir cancelar incidentes por duplicado o porque no corresponden. Exigir motivo y conservar el historial.

RF-20. Admitir varios adjuntos PNG, JPG/JPEG, WebP y PDF, hasta 10 MB por archivo. Identificar cada archivo como evidencia del incidente o de la corrección.

RF-21. Exigir responsable de desarrollo al iniciar DESA o volver a desarrollo y responsable QA al enviar a CERT. Los rechazos requieren motivo. Registrar despliegue en PROD requiere nombre de responsable y observación.

## Estados y transiciones

| Estado actual | Acción | Resultado | Datos requeridos |
| --- | --- | --- | --- |
| Registrado | Iniciar desarrollo | En desarrollo · DESA | Responsable de desarrollo |
| En desarrollo · DESA | Enviar a CERT | En certificación · CERT | Responsable de desarrollo y QA |
| En certificación · CERT | Certificar QA | Certificado por QA | QA seleccionado y observación |
| Certificado por QA | Enviar a UAT | Validación del cliente · UAT | Certificación QA previa |
| Validación del cliente · UAT | Aprobar cliente | Aprobado · pendiente PROD | Nombre del cliente y observación |
| Aprobado · pendiente PROD | Registrar despliegue | Desplegado · cerrado | Nombre del responsable y observación |
| CERT o UAT | Rechazar | En desarrollo · DESA | Responsable y motivo |
| Cerrado | Reabrir | En desarrollo · DESA | Responsable y motivo |
| Cualquier estado activo | Cancelar | Cancelado | Responsable y motivo |

La certificación QA y la aprobación del cliente son obligatorias en cada recorrido. Un rechazo o reapertura vuelve a desarrollo; las aprobaciones antiguas quedan visibles, pero no habilitan saltar etapas. No existen números de intento ni versiones de corrección.

## Comportamiento de la interfaz

- Apartado Incidentes en la navegación principal, con tabla y tablero.
- Búsqueda por identificador, título, descripción o reportante; filtros por proyecto, ambiente de detección, estado, prioridad y responsable de desarrollo/QA.
- Ambiente de detección separado de la etapa del recorrido.
- Ficha con datos, acciones válidas, evidencias, comentarios e historial con fechas y responsables.
- Cambios de etapa mediante acciones en la ficha o movimiento de tarjetas en el tablero. Ambos usan el mismo formulario y las mismas validaciones del backend.
- Edición de datos y responsables en Registrado o Desarrollo. Al enviar a CERT, los datos de la corrección quedan protegidos; un rechazo devuelve a desarrollo y permite modificarlos para volver a certificar.
- Archivos de evidencia opcionales tanto al reportar como al documentar la corrección. Vista previa de imágenes y descarga de PDF.
- Aprobaciones requieren observación; enlaces http/https y adjuntos son opcionales.
- Registro de fechas de creación, actualización, cambios de etapa, comentarios, evidencias y cierre.
- Sin eliminación de incidentes: la cancelación conserva su trazabilidad. Los cancelados quedan en estado terminal.

## Criterios de aceptación

1. No se puede crear sin título, descripción, proyecto, ambiente, prioridad o reportante.
2. Una tarea vinculada debe pertenecer al proyecto seleccionado.
3. No se puede iniciar desarrollo sin responsable ni enviar a CERT sin QA.
4. No se puede pasar a UAT sin certificar QA; no se puede cerrar en PROD sin aprobación del cliente.
5. Rechazos, reaperturas y cancelaciones requieren motivo; aprobaciones y despliegue requieren observación y responsable.
6. Después de un rechazo o reapertura se debe repetir CERT y UAT, conservando el historial previo.
7. No se puede modificar una corrección en etapas de validación o ya aprobada mediante la edición de datos.
8. Se pueden adjuntar varios archivos admitidos, clasificados por incidente/corrección; el límite se valida en el servidor y la interfaz.
9. Un archivo ajeno al incidente no se puede descargar usando su identificador.
10. Tabla y tablero presentan los mismos incidentes y respetan las mismas restricciones de avance.

## Decisiones técnicas y límites de la primera entrega

- Persistencia de incidentes, eventos y adjuntos en tablas nuevas. Los archivos se guardan en la base de datos, junto con sus metadatos; deben incluirse en sus respaldos.
- Bloqueo transaccional al modificar un incidente para serializar cambios concurrentes.
- API en `/api/incidents`; ruta de interfaz `/incidents`.
- Sin login, según acuerdo. Los nombres seleccionados/escritos son registros declarados, sin verificación de identidad.
- Exportaciones e indicadores nuevos del dashboard fuera de esta primera entrega.
- Cancelación terminal; no se solicitó restaurar incidentes cancelados.
- Registro de despliegue manual: el módulo no ejecuta despliegues ni conecta ambientes de infraestructura.

## Verificación

- Ocho pruebas automatizadas del backend: seis de reglas/servicio y dos de API con persistencia H2 temporal en modo PostgreSQL.
- Cobertura del recorrido completo, rechazos, recertificación, reapertura, responsabilidades, motivos, adjuntos, enlaces y tareas de otro proyecto.
- Compilación de producción del frontend.
- Revisión visual de tabla, tablero y ficha; recorrido por interfaz DESA → CERT → UAT → cierre en PROD sobre backend aislado con datos ficticios.
- No se ha desplegado en DESA, CERT, UAT ni PROD reales. PostgreSQL del ambiente real no se modificó durante las pruebas.

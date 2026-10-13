# Implementación del seguimiento de incidentes

La especificación funcional está en [requerimientos-seguimiento-incidentes.md](requerimientos-seguimiento-incidentes.md).

## Componentes

- Backend: entidades Incident, IncidentEvent e IncidentAttachment; servicio IncidentService; reglas IncidentWorkflow; API IncidentController.
- Frontend: IncidentsComponent, modelo y servicio de incidentes; navegación y ruta `/incidents`.
- La implementación se distribuye entre los repositorios taskTrackerBack y taskTrackerFront. No se desplegaron servicios del sistema.

## Puesta en marcha

1. Compilar el backend con Java 21 mediante `mvn verify`.
2. Compilar el frontend mediante `npm run build`.
3. Desplegar ambos siguiendo el mecanismo habitual del proyecto. El frontend mantiene su configuración de API existente.
4. Con la configuración actual `spring.jpa.hibernate.ddl-auto=update`, el arranque del backend crea las tablas nuevas y sus claves foráneas. Si un ambiente usa migraciones controladas, preparar la migración equivalente antes de desplegar.
5. Asegurar que el límite de subida del proxy de ese ambiente permita solicitudes de hasta 11 MB; el módulo limita cada archivo a 10 MB.
6. Registrar un incidente de prueba en DESA y ejecutar el recorrido QA/CERT → cliente/UAT → registro de PROD y cierre antes de certificar la entrega del módulo.

## Persistencia

Tablas nuevas: `incidents`, `incident_events`, `incident_attachments`. Adjuntos en PostgreSQL como `bytea`. Las referencias a proyectos, tareas y miembros preservan integridad: no se deben eliminar registros referenciados sin resolver antes sus dependencias. La base y sus adjuntos deben estar incluidos en los respaldos.

## API

| Operación | Método y ruta |
| --- | --- |
| Listar / consultar | GET `/api/incidents` y `/api/incidents/{id}` |
| Registrar / editar | POST `/api/incidents` y PUT `/api/incidents/{id}` |
| Cambiar etapa | POST `/api/incidents/{id}/transitions` |
| Comentar | POST `/api/incidents/{id}/comments` |
| Adjuntar | POST `/api/incidents/{id}/attachments`, multipart `file` y `category` |
| Descargar evidencia | GET `/api/incidents/{id}/attachments/{attachmentId}` |

Categorías: `INCIDENT` y `CORRECTION`. Acciones: `START`, `SEND_CERT`, `APPROVE_QA`, `SEND_UAT`, `APPROVE_CLIENT`, `DEPLOY`, `REJECT`, `REOPEN`, `CANCEL`.

El backend valida las transiciones independientemente de la interfaz. No hay un campo de estado editable libremente. Las aprobaciones QA usan al responsable QA asignado y conservan su nombre en el evento; cliente, despliegue, reapertura y cancelación registran el nombre informado.

## Validación y límites

Las pruebas de API usan H2 temporal, no la base configurada del sistema. Se requiere certificar también contra PostgreSQL en DESA/CERT antes del pase real. Sin login, las aprobaciones no verifican identidad. La primera entrega carga el listado y su historial; paginación y almacenamiento externo de archivos quedan para un crecimiento posterior.

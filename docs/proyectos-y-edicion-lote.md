# Ficha de proyecto y edición en lote

La ficha reúne datos existentes sin modificar el esquema ni crear cuentas nuevas. Utiliza la identidad y sesión del portal Authelia con el propietario verificado `tyrak`.

## Ficha

`GET /api/projects/{id}/detail` devuelve el proyecto y sus tareas, miembros vinculados, incidentes y horas extras. Incluye las tareas de proyectos finalizados, cerrados o detenidos, que siguen consultables desde su ficha. El equipo reúne miembros explícitos y responsables de tareas e incidentes; no incluye personas ajenas al proyecto.

La interfaz `/projects/:id` ofrece resumen, tareas con jerarquía, incidentes, equipo y horas extras. Las fechas se derivan de las tareas ejecutables. Las métricas de la ficha cuentan las hojas para evitar duplicar grupos y subtareas. Los enlaces a las pantallas de gestión de incidentes y horas preservan el filtro del proyecto.

## Lotes

`PATCH /api/tasks/bulk` requiere sesión y CSRF. El cuerpo contiene:

- `taskIds`: selección única de 1 a 500 identificadores positivos.
- `status` y `priority`: opcionales; si se omiten se conservan.
- `updateAssignees` y `assigneeIds`: activan el reemplazo explícito de responsables; una lista vacía quita la asignación.
- `updateStartDate` con `startDate`, y `updateDueDate` con `dueDate`: activan cada cambio de fecha; `null` permite quitarla.

Se valida la selección completa antes de modificar entidades. Fechas incompatibles, tareas inexistentes, responsables inexistentes o grupos rechazan todo el lote. La transacción revierte cualquier fallo de persistencia. No se modifican descripciones, comentarios, jerarquías ni campos no activados.

Los grupos se calculan desde sus subtareas y no se pueden editar directamente en lote. La selección general toma tareas ejecutables visibles, hasta 500. Cambiar filtros, contraer grupos o cambiar de vista elimina de la selección las tareas que dejan de estar visibles. La vista previa enumera todas las tareas que se guardarán.

Completar una tarea fija su avance en 100 %. Cambiar a otro estado una tarea al 100 % reinicia el avance a 0 %; la interfaz lo explica antes de confirmar. Los cambios de estado sincronizan el estado del proyecto usando tareas hoja. Cambiar únicamente prioridad, responsables o fechas conserva el estado explícito del proyecto.

## Verificación

`mvn test` cubre la suite completa, incluida `ProjectDetailAndBulkApiTest`: aislamiento por proyecto, consulta de proyectos cerrados, actualizaciones parciales, rechazo de lotes inválidos, conservación de relaciones, limpieza explícita de campos, coherencia de avance y protección de sesión/CSRF.

El recorrido de navegador se verificó con el frontend compilado y una API Spring Boot real conectada a H2 desechable: guardar desde la ficha, guardar desde la tabla, selección Kanban, cancelar sin escribir, validación de fechas, consulta de las cinco pestañas, enlaces filtrados, estados vacíos y proyecto inexistente. Se verificaron escritorio de 1440 px, móvil de 390 px, tema oscuro y las descargas PDF/Excel reales. La validación de producción consulta datos existentes y rechaza una selección vacía; no modifica tareas reales.

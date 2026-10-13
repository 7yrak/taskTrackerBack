# Jerarquía y horas extras

En las tablas de Tareas e Incidentes se puede arrastrar cualquier fila sobre otra para convertirla en hija. El icono de puntos indica dónde iniciar el arrastre. La fila de destino se resalta.

Las zonas de proyectos encima de la tabla permiten dejar la fila como raíz y cambiarla de proyecto, incluso si el proyecto destino no tiene filas visibles. Tareas también permite dejarla sin proyecto. Al mover un padre se mueven todas sus descendientes. Para invertir padre/hija, primero deja la hija sin padre (botón de flecha hacia arriba o zona del proyecto) y luego arrastra el padre sobre ella. No se permiten ciclos.

Los estados de cada incidente y las aprobaciones QA/cliente se conservan al organizar la tabla. El cambio se registra en el historial. Los vínculos a tareas de otro proyecto se retiran con una observación. La importación Excel continúa reemplazando solo los incidentes del proyecto, desde cero y en Registrado; no reconstruye jerarquías.

La sección Horas extras permite registrar Fecha, Persona, Horas, Tema/Motivo y Cliente, vinculados a un Proyecto (decimales, mayores a cero y hasta 24 por registro). Sugiere personas del equipo y permite escribir nombres. Tiene edición en tabla y modal, eliminación confirmada, filtros por proyecto/persona/fechas/tema, total de horas y desglose por persona. La descarga CSV contiene la vista filtrada y abre en Excel.

Cliente se guarda por registro y puede indicar “Por confirmar”, independientemente del proyecto. Los registros existentes sin Cliente se muestran como “Por confirmar”. El CSV mantiene el orden Fecha, Persona, Horas, Tema/Motivo, Cliente y Proyecto.

# Acceso personal con el portal compartido

La instalación personal reconoce la identidad `sergio` de Authelia. El gateway sobrescribe `X-Shared-User` y `X-Shared-Key` después de comprobar la sesión central. La clave técnica exclusiva se entrega mediante `SHARED_PORTAL_KEY`, fuera de Git. No se copia la contraseña del usuario ni se crean filas de propietario nuevas.

El adaptador se vincula al propietario ya configurado del producto, conservando sus identificadores, permisos y datos. El usuario no necesita un segundo login desde la URL protegida del gateway. Cabeceras falsificadas, otra identidad o claves vacías/incorrectas se rechazan. Sin cabeceras, el flujo local previo se conserva para compatibilidad y recuperación; no es el acceso habitual del usuario.

El portal actúa como única barrera de entrada externa. No publicar directamente el backend ni distribuir la clave técnica. La identidad personal está limitada a `sergio`; una instalación multiusuario requiere asignaciones explícitas por usuario, no extender automáticamente el acceso del propietario.

Validación: `mvn -Dtest=SharedPortalFilterTest test` más las pruebas de seguridad existentes. Se verifica autorización real después del despliegue y navegación entre aplicaciones con la misma sesión de Authelia. El cambio no modifica esquemas ni contraseñas de bases de datos.

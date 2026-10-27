# taskTrackerBack

API de TaskTracker (Spring Boot 3.4 / Java 21 / PostgreSQL) para uso personal.

## Acceso

Un único propietario inicia sesión con usuario y contraseña (sesión por cookie `HttpOnly`, CSRF activo).
Tras 5 intentos fallidos seguidos el login responde `429` durante 5 minutos.

Variables de entorno obligatorias (el servicio **no arranca** sin ellas):

| Variable | Contenido |
| --- | --- |
| `TASKTRACKER_USER` | Nombre de usuario del propietario |
| `TASKTRACKER_PASSWORD_HASH` | Hash BCrypt de la contraseña (nunca la contraseña en claro) |

Generar el hash:

```bash
htpasswd -bnBC 12 "" 'tu-clave' | tr -d ':\n'
# o: python3 -c "import bcrypt,sys; print(bcrypt.hashpw(sys.argv[1].encode(), bcrypt.gensalt(12)).decode())" 'tu-clave'
```

Otras variables: `TASKTRACKER_DB_URL`, `TASKTRACKER_DB_USER`, `TASKTRACKER_DB_PASSWORD`,
`TASKTRACKER_DDL_AUTO` (por defecto `validate`; usa `update` para una base nueva) y
`TASKTRACKER_COOKIE_SECURE` (por defecto `true`; `false` solo para desarrollo en http).

Endpoints: `GET /auth/csrf`, `POST /auth/login` (form `username`/`password` + cabecera CSRF),
`GET /auth/session`, `POST /auth/logout`.

## Desarrollo

```bash
mvn -B -ntp test      # pruebas (H2 en memoria)
mvn spring-boot:run   # puerto 8081, solo 127.0.0.1
```

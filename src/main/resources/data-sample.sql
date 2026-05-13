-- Script de datos de ejemplo - ejecutar manualmente después de arrancar la app
-- (las tablas se crean automáticamente con ddl-auto=update)

-- Proyectos
INSERT INTO projects (name, description, color) VALUES
  ('Angular Frontend', 'Desarrollo del frontend con Angular y Material', '#e91e63'),
  ('Spring Boot API', 'Backend REST API con Spring Boot y JPA', '#3f51b5'),
  ('PostgreSQL DB', 'Diseño de base de datos y migraciones', '#009688')
ON CONFLICT DO NOTHING;

-- Miembros del equipo
INSERT INTO team_members (name, email, role) VALUES
  ('Ana García',    'ana@empresa.com',    'Frontend Developer'),
  ('Carlos López',  'carlos@empresa.com', 'Backend Developer'),
  ('María Torres',  'maria@empresa.com',  'Full Stack Developer'),
  ('Luis Pérez',    'luis@empresa.com',   'DBA / DevOps')
ON CONFLICT DO NOTHING;

-- ──────────────────────────────────────────────────────────────────────────────
-- Tareas raíz (sin parent_id) — nivel 0
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO tasks (title, description, status, priority, project_id, start_date, due_date, progress_actual)
VALUES
  -- Grupo 1: Frontend
  ('Diseño y componentes UI', 'Arquitectura visual del sistema Angular',
    'IN_PROGRESS', 'HIGH',
    (SELECT id FROM projects WHERE name='Angular Frontend'),
    CURRENT_DATE - INTERVAL '30 days', CURRENT_DATE + INTERVAL '15 days', 0),

  -- Grupo 2: API Backend
  ('API REST de la aplicación', 'Endpoints CRUD para todos los módulos',
    'IN_PROGRESS', 'CRITICAL',
    (SELECT id FROM projects WHERE name='Spring Boot API'),
    CURRENT_DATE - INTERVAL '40 days', CURRENT_DATE + INTERVAL '5 days', 0),

  -- Grupo 3: Base de datos
  ('Infraestructura de datos', 'Modelo de datos, migraciones y rendimiento',
    'IN_PROGRESS', 'HIGH',
    (SELECT id FROM projects WHERE name='PostgreSQL DB'),
    CURRENT_DATE - INTERVAL '45 days', CURRENT_DATE + INTERVAL '10 days', 0)
ON CONFLICT DO NOTHING;

-- ──────────────────────────────────────────────────────────────────────────────
-- Subtareas de "Diseño y componentes UI"
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO tasks (title, description, status, priority, project_id, start_date, due_date, progress_actual, parent_id)
SELECT
  t.title, t.description, t.status::task_status, t.priority::task_priority,
  (SELECT id FROM projects WHERE name='Angular Frontend'),
  t.start_date, t.due_date, t.progress_actual,
  (SELECT id FROM tasks WHERE title='Diseño y componentes UI')
FROM (VALUES
  ('Configurar Angular Material', 'Setup del tema base y paleta de colores',
    'DONE', 'HIGH',
    CURRENT_DATE - INTERVAL '28 days', CURRENT_DATE - INTERVAL '20 days', 100),
  ('Dashboard principal', 'Cards de estadísticas y gráficas de seguimiento',
    'IN_PROGRESS', 'HIGH',
    CURRENT_DATE - INTERVAL '19 days', CURRENT_DATE + INTERVAL '7 days', 60),
  ('Tabla de tareas con árbol', 'Vista jerárquica al estilo MS Project con expand/collapse',
    'IN_PROGRESS', 'HIGH',
    CURRENT_DATE - INTERVAL '10 days', CURRENT_DATE + INTERVAL '15 days', 30),
  ('Gestión de equipo', 'CRUD de miembros con asignación a proyectos',
    'TODO', 'MEDIUM',
    CURRENT_DATE + INTERVAL '5 days', CURRENT_DATE + INTERVAL '20 days', 0)
) AS t(title, description, status, priority, start_date, due_date, progress_actual)
ON CONFLICT DO NOTHING;

-- ──────────────────────────────────────────────────────────────────────────────
-- Subtareas de "API REST de la aplicación"
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO tasks (title, description, status, priority, project_id, start_date, due_date, progress_actual, parent_id)
SELECT
  t.title, t.description, t.status::task_status, t.priority::task_priority,
  (SELECT id FROM projects WHERE name='Spring Boot API'),
  t.start_date, t.due_date, t.progress_actual,
  (SELECT id FROM tasks WHERE title='API REST de la aplicación')
FROM (VALUES
  ('CRUD de proyectos y miembros', 'Endpoints para gestionar proyectos y equipo',
    'DONE', 'HIGH',
    CURRENT_DATE - INTERVAL '38 days', CURRENT_DATE - INTERVAL '25 days', 100),
  ('API de tareas con filtros', 'Endpoints CRUD con filtrado por estado, proyecto y asignado',
    'DONE', 'CRITICAL',
    CURRENT_DATE - INTERVAL '24 days', CURRENT_DATE - INTERVAL '10 days', 100),
  ('Importación y exportación Excel', 'Parser Apache POI para carga masiva de tareas',
    'IN_PROGRESS', 'MEDIUM',
    CURRENT_DATE - INTERVAL '9 days', CURRENT_DATE + INTERVAL '5 days', 80),
  ('Autenticación JWT', 'Login, refresh token y protección de endpoints',
    'TODO', 'HIGH',
    CURRENT_DATE + INTERVAL '6 days', CURRENT_DATE + INTERVAL '20 days', 0),
  ('Tests de integración', 'Cobertura end-to-end de todos los endpoints',
    'TODO', 'MEDIUM',
    CURRENT_DATE + INTERVAL '10 days', CURRENT_DATE + INTERVAL '30 days', 0)
) AS t(title, description, status, priority, start_date, due_date, progress_actual)
ON CONFLICT DO NOTHING;

-- ──────────────────────────────────────────────────────────────────────────────
-- Subtareas de "Infraestructura de datos"
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO tasks (title, description, status, priority, project_id, start_date, due_date, progress_actual, parent_id)
SELECT
  t.title, t.description, t.status::task_status, t.priority::task_priority,
  (SELECT id FROM projects WHERE name='PostgreSQL DB'),
  t.start_date, t.due_date, t.progress_actual,
  (SELECT id FROM tasks WHERE title='Infraestructura de datos')
FROM (VALUES
  ('Diseño del modelo de datos', 'ERD y normalización hasta 3FN',
    'DONE', 'CRITICAL',
    CURRENT_DATE - INTERVAL '43 days', CURRENT_DATE - INTERVAL '30 days', 100),
  ('Índices de rendimiento', 'Analizar queries lentas y agregar índices compuestos',
    'IN_REVIEW', 'MEDIUM',
    CURRENT_DATE - INTERVAL '12 days', CURRENT_DATE + INTERVAL '3 days', 90),
  ('Backup y recuperación', 'Script de backup automático y plan de DR',
    'TODO', 'HIGH',
    CURRENT_DATE + INTERVAL '4 days', CURRENT_DATE + INTERVAL '18 days', 0)
) AS t(title, description, status, priority, start_date, due_date, progress_actual)
ON CONFLICT DO NOTHING;

-- ──────────────────────────────────────────────────────────────────────────────
-- Sub-subtareas de "Dashboard principal" (nivel 2)
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO tasks (title, description, status, priority, project_id, start_date, due_date, progress_actual, parent_id)
SELECT
  t.title, t.description, t.status::task_status, t.priority::task_priority,
  (SELECT id FROM projects WHERE name='Angular Frontend'),
  t.start_date, t.due_date, t.progress_actual,
  (SELECT id FROM tasks WHERE title='Dashboard principal')
FROM (VALUES
  ('Cards de estadísticas', 'Total, en progreso, completadas, bloqueadas, vencidas',
    'DONE', 'HIGH',
    CURRENT_DATE - INTERVAL '18 days', CURRENT_DATE - INTERVAL '12 days', 100),
  ('Gráfica de avance por proyecto', 'Barra de progreso por proyecto con % completado',
    'IN_PROGRESS', 'MEDIUM',
    CURRENT_DATE - INTERVAL '11 days', CURRENT_DATE + INTERVAL '3 days', 70),
  ('Vista árbol MS Project', 'Tabla jerárquica con barras de avance real vs esperado',
    'IN_PROGRESS', 'HIGH',
    CURRENT_DATE - INTERVAL '5 days', CURRENT_DATE + INTERVAL '7 days', 40)
) AS t(title, description, status, priority, start_date, due_date, progress_actual)
ON CONFLICT DO NOTHING;

-- ──────────────────────────────────────────────────────────────────────────────
-- Asignaciones de miembros
-- ──────────────────────────────────────────────────────────────────────────────

-- Subtareas Frontend → Ana (principalmente)
INSERT INTO task_assignees (task_id, member_id)
SELECT t.id, m.id FROM tasks t, team_members m
WHERE t.title IN ('Configurar Angular Material','Dashboard principal','Gestión de equipo')
  AND m.email = 'ana@empresa.com'
ON CONFLICT DO NOTHING;

INSERT INTO task_assignees (task_id, member_id)
SELECT t.id, m.id FROM tasks t, team_members m
WHERE t.title IN ('Tabla de tareas con árbol','Cards de estadísticas','Gráfica de avance por proyecto','Vista árbol MS Project')
  AND m.email IN ('ana@empresa.com','maria@empresa.com')
ON CONFLICT DO NOTHING;

-- Subtareas API → Carlos y María
INSERT INTO task_assignees (task_id, member_id)
SELECT t.id, m.id FROM tasks t, team_members m
WHERE t.title IN ('CRUD de proyectos y miembros','API de tareas con filtros','Autenticación JWT')
  AND m.email = 'carlos@empresa.com'
ON CONFLICT DO NOTHING;

INSERT INTO task_assignees (task_id, member_id)
SELECT t.id, m.id FROM tasks t, team_members m
WHERE t.title IN ('Importación y exportación Excel','Tests de integración')
  AND m.email IN ('carlos@empresa.com','maria@empresa.com')
ON CONFLICT DO NOTHING;

-- Subtareas DB → Luis
INSERT INTO task_assignees (task_id, member_id)
SELECT t.id, m.id FROM tasks t, team_members m
WHERE t.title IN ('Diseño del modelo de datos','Índices de rendimiento','Backup y recuperación')
  AND m.email = 'luis@empresa.com'
ON CONFLICT DO NOTHING;

-- ──────────────────────────────────────────────────────────────────────────────
-- Comentarios de ejemplo
-- ──────────────────────────────────────────────────────────────────────────────
INSERT INTO task_comments (author, text, date, task_id)
SELECT 'Ana García', 'Revisando los componentes de Material, todo en orden.', CURRENT_TIMESTAMP - INTERVAL '2 days', id 
FROM tasks WHERE title = 'Configurar Angular Material'
ON CONFLICT DO NOTHING;

INSERT INTO task_comments (author, text, date, task_id)
SELECT 'Carlos López', 'Necesito que me pasen los tokens para el JWT.', CURRENT_TIMESTAMP - INTERVAL '1 day', id 
FROM tasks WHERE title = 'Autenticación JWT'
ON CONFLICT DO NOTHING;

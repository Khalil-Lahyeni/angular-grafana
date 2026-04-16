-- Creation idempotente des bases
SELECT 'CREATE DATABASE keycloak_db'
WHERE NOT EXISTS (
	SELECT FROM pg_database WHERE datname = 'keycloak_db'
)
\gexec

SELECT 'CREATE DATABASE railway_db'
WHERE NOT EXISTS (
	SELECT FROM pg_database WHERE datname = 'railway_db'
)
\gexec

-- Le script est execute avec l'utilisateur de bootstrap Postgres
GRANT ALL PRIVILEGES ON DATABASE keycloak_db TO CURRENT_USER;
GRANT ALL PRIVILEGES ON DATABASE railway_db TO CURRENT_USER;

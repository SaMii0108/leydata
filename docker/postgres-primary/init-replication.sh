#!/bin/bash
# Corre una sola vez cuando el primary se inicializa por primera vez.
# Crea el usuario de replicaciÃ³n y habilita streaming replication.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
  CREATE USER replicator REPLICATION LOGIN PASSWORD 'replicator_pass';
SQL

# Permitir conexiones de replicaciÃ³n desde cualquier host de la red Docker
echo "host replication replicator all md5" >> "$PGDATA/pg_hba.conf"

# Configurar WAL para streaming replication
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
  ALTER SYSTEM SET wal_level = replica;
  ALTER SYSTEM SET max_wal_senders = 3;
  ALTER SYSTEM SET wal_keep_size = '64MB';
  ALTER SYSTEM SET hot_standby = on;
SQL

echo "==> Replication configured. Reload required (happens automatically on next start)."

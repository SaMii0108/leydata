#!/bin/bash
# Entrypoint del contenedor replica.
# Si PGDATA estÃ¡ vacÃ­o, hace pg_basebackup desde el primary antes de arrancar.
set -e

PGDATA="${PGDATA:-/var/lib/postgresql/data}"

if [ -z "$(ls -A "$PGDATA" 2>/dev/null)" ]; then
  echo "==> PGDATA vacÃ­o â€” iniciando pg_basebackup desde el primary (db:5432)..."

  until PGPASSWORD=replicator_pass pg_isready -h db -U replicator -q; do
    echo "    Esperando que el primary estÃ© listo..."
    sleep 2
  done

  PGPASSWORD=replicator_pass pg_basebackup \
    -h db \
    -U replicator \
    -D "$PGDATA" \
    -P \
    -Xs \
    -R

  # -R crea standby.signal y escribe primary_conninfo en postgresql.auto.conf
  echo "==> pg_basebackup completado. Arrancando en modo standby."
else
  echo "==> PGDATA ya existe â€” arrancando replica en modo standby."
fi

exec docker-entrypoint.sh "$@"

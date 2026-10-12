#!/bin/sh
set -eu
cd "$(dirname "$0")"

DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
NAME="$DIR/taskboard-$(date +%Y%m%d-%H%M%S)"

mkdir -p "$DIR"
docker compose exec -T db pg_dump -U taskboard -d taskboard --format=custom > "$NAME.dump.tmp"
mv "$NAME.dump.tmp" "$NAME.dump"
docker compose exec -T app tar -czf - -C /data files > "$NAME-files.tar.gz.tmp"
mv "$NAME-files.tar.gz.tmp" "$NAME-files.tar.gz"
find "$DIR" -name 'taskboard-*' -mtime +"$KEEP_DAYS" -delete

echo "$(date '+%Y-%m-%d %H:%M:%S') backup ok: $NAME.dump ($(du -h "$NAME.dump" | cut -f1)), files ($(du -h "$NAME-files.tar.gz" | cut -f1))"

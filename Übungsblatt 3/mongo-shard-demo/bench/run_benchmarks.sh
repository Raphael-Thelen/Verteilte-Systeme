#!/usr/bin/env bash
set -euo pipefail

# Ins Bench-Verzeichnis wechseln (sicherstellen, dass relative Pfade stimmen)
cd "$(dirname "$0")"

LOG_DIR="logs"
mkdir -p "$LOG_DIR"

CLUSTER_SIZES=(1 2 3)
MODES=(pael pcec)

# Load-Test-Parameter
CONNS=100
DUR=30
PIPE=10

for shards in "${CLUSTER_SIZES[@]}"; do
  echo "=== Starte Cluster mit $shards Shard(s) ==="
  ../start_cluster.sh "$shards"

  for mode in "${MODES[@]}"; do
    echo "--- Testmodus: $mode ---"

    # Mapping Konsistenz-Parameter
    if [ "$mode" = "pael" ]; then
      WRITE_CONCERN_W=1
      WRITE_CONCERN_J=false
      READ_CONCERN_LEVEL=local
      READ_PREFERENCE=primaryPreferred
    else
      WRITE_CONCERN_W=majority
      WRITE_CONCERN_J=true
      READ_CONCERN_LEVEL=majority
      READ_PREFERENCE=primary
    fi

    # .env für die App erzeugen
    cat > ../app/.env <<EOF
MONGO_URI=mongodb://localhost:27017/?readPreference=${READ_PREFERENCE}
PORT=3000
WRITE_CONCERN_W=${WRITE_CONCERN_W}
WRITE_CONCERN_J=${WRITE_CONCERN_J}
READ_CONCERN_LEVEL=${READ_CONCERN_LEVEL}
READ_PREFERENCE=${READ_PREFERENCE}
EOF

    # App neustarten
    pkill -f "node index.js" || true
    (cd ../app && node index.js &)
    sleep 5  # kurz warten, bis die App bereit ist

    # 1) Reiner Schreibtest
    echo "  * Schreibtest..."
    autocannon -c "$CONNS" -d "$DUR" -p "$PIPE" \
      -H 'Content-Type: application/json' \
      -m POST \
      -b '{"counterId":"pageViews","delta":1}' \
      -j "http://localhost:3000/increment" \
      > "$LOG_DIR/${shards}shard_${mode}_write.json"

    # 2) Reiner Lesetest
    echo "  * Lesetest..."
    autocannon -c $((CONNS/2)) -d "$DUR" -p "$PIPE" \
      -j "http://localhost:3000/value?counterId=pageViews" \
      > "$LOG_DIR/${shards}shard_${mode}_read.json"

    # 3) Gemischter 80/20-Mix
    echo "  * Mixed-Test..."
    cat > bench_${mode}.json <<EOF
[
  {
    "method": "POST",
    "path": "/increment",
    "body": "{\\"counterId\\":\\"pageViews\\",\\"delta\\":1}",
    "headers": { "Content-Type": "application/json" },
    "weight": 8
  },
  {
    "method": "GET",
    "path": "/value?counterId=pageViews",
    "weight": 2
  }
]
EOF
    autocannon -c "$CONNS" -d "$DUR" -p "$PIPE" \
      -j -g bench_${mode}.json \
      http://localhost:3000 \
      > "$LOG_DIR/${shards}shard_${mode}_mixed.json"

    # App beenden
    pkill -f "node index.js" || true
  done
done

echo "✅ Alle Benchmarks fertig. Logs findest Du in $LOG_DIR/"
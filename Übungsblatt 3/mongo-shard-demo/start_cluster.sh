#!/usr/bin/env bash
set -euo pipefail

# 1) Skript-Directory ermitteln und dorthin wechseln (Projekt-Root)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 2) Parameter prüfen
if [[ $# -ne 1 || ! $1 =~ ^[1-3]$ ]]; then
  echo "Usage: $0 <#shards>   (#shards = 1 | 2 | 3)"
  exit 1
fi
NUM_SHARDS=$1

# 3) Cleanup
echo "⏹️  Cleanup…"
docker-compose down --volumes

# 4) Container starten
echo "⚙️  Starte Config-Server & Shard1…"
docker-compose up -d configsvr1 configsvr2 configsvr3 shard1

if (( NUM_SHARDS >= 2 )); then
  echo "⚙️  Starte Shard2…"
  docker-compose up -d shard2
fi

if (( NUM_SHARDS >= 3 )); then
  echo "⚙️  Starte Shard3…"
  docker-compose up -d shard3
fi

echo "⚙️  Starte mongos…"
docker-compose up -d mongos

echo "✅ Cluster mit $NUM_SHARDS Shard(s) gestartet."
echo "🔧 Initialisiere ReplicaSets & Sharding…"

# 5) init.sh im gleichen Verzeichnis aufrufen
bash "$SCRIPT_DIR/init.sh" "$NUM_SHARDS"

echo "🎉 Fertig. Verbinde Deine App via mongodb://localhost:27017"
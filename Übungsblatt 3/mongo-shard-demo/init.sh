#!/usr/bin/env bash
set -euo pipefail

# Skript-Directory ermitteln und dorthin wechseln (Projekt-Root)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [[ $# -ne 1 || ! $1 =~ ^[1-3]$ ]]; then
  echo "Usage: $0 <#shards>   (#shards = 1 | 2 | 3)"
  exit 1
fi
NUM_SHARDS=$1

# Helper: Warten bis mongod/mongos im Container lauscht
wait_for() {
  local svc=$1 port=$2
  echo -n "  → Warten auf $svc (Port $port)…"
  until docker-compose exec -T "$svc" \
      mongosh --quiet --port "$port" \
             --eval 'db.adminCommand({ping:1})' \
    >/dev/null 2>&1; do
    printf "."
    sleep 1
  done
  echo " ready!"
}

echo "1) Config-Server-ReplSet initialisieren"
wait_for configsvr1 27019
docker-compose exec configsvr1 \
  mongosh --port 27019 --eval '
    rs.initiate({
      _id: "cfgrs",
      configsvr: true,
      members:[
        {_id:0,host:"configsvr1:27019"},
        {_id:1,host:"configsvr2:27019"},
        {_id:2,host:"configsvr3:27019"}
      ]
    });
  '
echo "   Config-Server replSet OK"

echo "2) Shard1-ReplSet initialisieren"
wait_for shard1 27016
docker-compose exec shard1 \
  mongosh --port 27016 --eval '
    rs.initiate({
      _id: "shard1",
      members:[{_id:0,host:"shard1:27016"}]
    });
  '
echo "   Shard1 replSet OK"

if (( NUM_SHARDS >= 2 )); then
  echo "3) Shard2-ReplSet initialisieren"
  wait_for shard2 27015
  docker-compose exec shard2 \
    mongosh --port 27015 --eval '
      rs.initiate({
        _id: "shard2",
        members:[{_id:0,host:"shard2:27015"}]
      });
    '
  echo "   Shard2 replSet OK"
fi

if (( NUM_SHARDS >= 3 )); then
  echo "4) Shard3-ReplSet initialisieren"
  wait_for shard3 27014
  docker-compose exec shard3 \
    mongosh --port 27014 --eval '
      rs.initiate({
        _id: "shard3",
        members:[{_id:0,host:"shard3:27014"}]
      });
    '
  echo "   Shard3 replSet OK"
fi

echo "5) Warten auf mongos (27017)"
wait_for mongos 27017

echo "6) Shards hinzufügen & Sharding aktivieren"
docker-compose exec mongos \
  mongosh --eval 'sh.addShard("shard1/shard1:27016");'
if (( NUM_SHARDS >= 2 )); then
  docker-compose exec mongos \
    mongosh --eval 'sh.addShard("shard2/shard2:27015");'
fi
if (( NUM_SHARDS >= 3 )); then
  docker-compose exec mongos \
    mongosh --eval 'sh.addShard("shard3/shard3:27014");'
fi

docker-compose exec mongos \
  mongosh --eval '
    sh.enableSharding("testdb");
    sh.shardCollection("testdb.counters",{_id:"hashed"});
    print("=== Cluster ready ===");
  '
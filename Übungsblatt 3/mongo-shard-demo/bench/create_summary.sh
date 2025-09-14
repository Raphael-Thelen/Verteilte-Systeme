cd logs
echo "shards,mode,test,req_s,p50_ms,p90_ms,p99_ms,errors" \
  > ../summary.csv

for f in *.json; do
  # Dateinamen einmal zerlegen: z.B. 2shard_pael_write.json
  base=$(basename "$f" .json)
  shards=${base%%shard*}
  rest=${base#*shard_}        # pael_write
  mode=${rest%%_*}            # pael
  test=${rest#*_}             # write

  # Kennzahlen aus dem JSON ziehen
  req=$(jq .requests.average   "$f")
  p50=$(jq .latency.p50        "$f")
  p90=$(jq .latency.p90        "$f")
  p99=$(jq .latency.p99        "$f")
  err=$(jq .non2xx             "$f")

  echo "$shards,$mode,$test,$req,$p50,$p90,$p99,$err" \
    >> ../summary.csv
done
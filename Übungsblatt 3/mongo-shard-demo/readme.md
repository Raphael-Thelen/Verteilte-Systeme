mongodb und mongosh installieren
autocannon -g installieren

in mongo-shard-demo:

```zsh
docker-compose up -d
./start_cluster.sh {1-3}
```

in mongo-shard-demo/app:

```zsh
npm install
node index.js
```

Test Post:

```zsh
curl -X POST 'http://localhost:3000/increment' \
  -H 'Content-Type: application/json' \
  -d '{"counterId":"pageViews","delta":5}'
```

Test Get:

```zsh
curl 'http://localhost:3000/value?counterId=pageViews'
```

Autocannon Test

```zsh
autocannon -c 100 -d 30 -p 10 -m POST \
  -H 'Content-Type: application/json' \
  -b '{"counterId":"pageViews","delta":1}' \
  'http://localhost:3000/increment'
```

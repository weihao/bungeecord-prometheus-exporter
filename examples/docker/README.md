# Local stack

A proxy with the exporter, two backend servers, Prometheus and Grafana.

```sh
# from the repository root
mvn package
cp exporter-dist/target/bungeecord-prometheus-exporter-*.jar examples/docker/plugins/
cd examples/docker
docker compose up
```

| Service | URL |
| --- | --- |
| Metrics | <http://localhost:9985/metrics> |
| Prometheus | <http://localhost:9090> |
| Grafana | <http://localhost:3000> (admin / admin) |
| Minecraft | `localhost:25565` |

The dashboard is provisioned automatically under the **Minecraft** folder.

Try a different proxy:

```sh
PROXY_TYPE=BUNGEECORD docker compose up
PROXY_TYPE=WATERFALL  docker compose up
```

The same jar serves all three; nothing else changes.

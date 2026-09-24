# Migrating from 3.x to 4.0

4.0 is a rewrite. Every metric name changed, and the config file changed format. Nothing
carries over automatically, and nothing needs to be uninstalled first — drop in the new jar
and follow this page.

## Why everything was renamed

The 3.x names did not follow the Prometheus conventions, and the cost of that showed up in
queries:

- **Counters had no `_total` suffix.** `bungeecord_player_connects` reads like a gauge, and
  tooling that special-cases counters (recording-rule generators, some Grafana panels) got
  it wrong.
- **No base units.** `bungeecord_online_player_latency` was in milliseconds; Prometheus
  expects seconds. Mixing a millisecond metric into a dashboard alongside anything else
  meant hand-converting in every query.
- **Standard metrics were namespaced.** `bungeecord_jvm_memory` meant no off-the-shelf JVM
  dashboard worked, because they all look for `jvm_memory_used_bytes`.
- **Latency was a Summary labelled by player name.** Summary quantiles cannot be averaged
  or aggregated, so a p95 across two proxies was not computable at all — and one quantile
  stream per player name meant unbounded cardinality.

`MetricNamingConventionTest` now enforces all of these rules at build time, and the
end-to-end suite runs `promtool check metrics` over real output.

## Metric name changes

| 3.x | 4.0 | Notes |
| --- | --- | --- |
| `bungeecord_player_connects` | `minecraft_proxy_logins_total{result="allowed"}` | now split by outcome; `result="denied"` is new |
| `bungeecord_player_disconnects` | `minecraft_proxy_disconnects_total` | |
| `bungeecord_player_kicks` | `minecraft_proxy_kicks_total{server}` | now labelled by origin server |
| `bungeecord_player_chats` | `minecraft_proxy_chat_messages_total` | |
| `bungeecord_player_commands` | `minecraft_proxy_commands_total` | optional `command` label, off by default |
| `bungeecord_server_list_pings` | `minecraft_proxy_list_pings_total` | |
| `bungeecord_managed_servers` | `minecraft_proxy_servers_registered` | |
| `bungeecord_installed_network_plugins` | `minecraft_proxy_plugins_installed` | plus `minecraft_proxy_plugin_info{plugin,version}` |
| `bungeecord_online_player{server,player,online_mode}` | `minecraft_proxy_server_players_online{server}` | aggregate, always on |
| | `minecraft_proxy_player_online{player,uuid,server}` | per player, **off by default** |
| `bungeecord_online_player_latency` (Summary, ms) | `minecraft_proxy_player_ping_seconds` (Histogram, s) | aggregate, always on |
| | `minecraft_proxy_player_latency_seconds{player,uuid,server}` | per player, **off by default** |
| `bungeecord_jvm_memory{type}` | `jvm_memory_used_bytes`, `jvm_memory_committed_bytes`, … | standard names, not namespaced |
| `bungeecord_jvm_gc_*` | `jvm_gc_*` | standard names |
| `bungeecord_jvm_threads_*` | `jvm_threads_*` | standard names |
| `bungeecord_redis_online_player{server,player}` | `minecraft_proxy_redis_players_online` | now a count, not one series per player |
| `bungeecord_redis_bungee_online_proxies` | `minecraft_proxy_redis_proxies_online` | |
| `bungeecord_redis_player_connects` | removed | see below |
| `bungeecord_redis_player_disconnects` | removed | see below |

### New in 4.0

| Metric | Why it matters |
| --- | --- |
| `minecraft_proxy_server_up{server}` | backend reachability, measured by actually pinging it. This is the one to alert on. |
| `minecraft_proxy_server_ping_seconds{server}` | backend latency as seen from the proxy |
| `minecraft_proxy_server_reported_players{server}` | player count the backend itself reports |
| `minecraft_proxy_server_switches_total{from,to}` | movement between backends |
| `minecraft_proxy_connections_total` | raw connections, before authentication |
| `minecraft_proxy_info{type,name,version,online_mode}` | which proxy software and version |
| `minecraft_proxy_exporter_build_info` | which exporter build is running |
| `minecraft_proxy_exporter_scrape_errors_total{collector}` | a collector failed and metrics are missing |
| `minecraft_proxy_exporter_series_dropped_total{metric}` | the cardinality budget is clipping series |
| `process_*` | standard process metrics (CPU, file descriptors, resident memory) |

### Removed

`bungeecord_redis_player_connects` and `bungeecord_redis_player_disconnects` counted
RedisBungee's network join and leave events. They are gone because RedisBungee is now bound
reflectively rather than at compile time, and subscribing to its events requires the
compile dependency that made the 3.x build depend on JitPack being up. The per-proxy
`minecraft_proxy_logins_total` and `minecraft_proxy_disconnects_total`, summed across
proxies, carry the same information.

## Config file

`config.json` is replaced by `config.yml`. Delete the old file; a commented default is
written on first start.

On BungeeCord the plugin's data folder also moves, from
`plugins/BungeeCordPrometheusExporter/` to `plugins/proxy-prometheus-exporter/`. The name
now matches the Velocity plugin id, so `minecraft_proxy_plugin_info{plugin="..."}` reads
the same on both platforms. The old folder can be deleted.

<table>
<tr><th>3.x <code>config.json</code></th><th>4.0 <code>config.yml</code></th></tr>
<tr><td>

```json
{
  "host": "0.0.0.0",
  "port": "9985",
  "prefix": "bungeecord_",
  "jvm_gc": "true",
  "jvm_memory": "true",
  "jvm_threads": "true",
  "player_connects": "true",
  "player_kicks": "true",
  "online_player": "true",
  "online_player_latency": "true",
  "managed_servers": "true",
  "installed_network_plugins": "true",
  "redis_online_player": "false"
}
```

</td><td>

```yaml
host: 0.0.0.0
port: 9985
namespace: minecraft_proxy

metrics:
  jvm: true            # replaces jvm_gc, jvm_memory, jvm_threads
  events: true         # replaces player_connects, _kicks, _chats, _commands
  players-online: true
  players-per-server: true
  player-ping-histogram: true
  servers: true        # replaces managed_servers
  plugins: true        # replaces installed_network_plugins
  backend-health: true # new
  redisbungee: false
```

</td></tr>
</table>

Notable differences:

- `prefix: "bungeecord_"` became `namespace: minecraft_proxy` — no trailing underscore, and
  it no longer applies to `jvm_*` or `process_*`.
- The three JVM toggles collapsed into one `metrics.jvm`.
- Every event counter is controlled by one `metrics.events`.
- Values are real YAML booleans, not the strings `"true"` and `"false"`.
- Everything can be overridden by environment variable, e.g.
  `MC_PROXY_EXPORTER_PORT=9985`. See the [README](../README.md#configuration).

### Keeping the old metric names

If rewriting dashboards is not an option right now, setting `namespace: bungeecord` gets
the prefix back — but the metric names themselves still differ (`_total` suffixes, base
units, the reshaped player metrics), so queries still need updating. Rewriting them is the
shorter path.

## Query rewrites

```promql
# 3.x: logins per second
rate(bungeecord_player_connects[5m])
# 4.0
rate(minecraft_proxy_logins_total{result="allowed"}[5m])

# 3.x: players online (summing a per-player gauge)
sum(bungeecord_online_player)
# 4.0
minecraft_proxy_players_online

# 3.x: players per server
sum by (server) (bungeecord_online_player)
# 4.0
minecraft_proxy_server_players_online

# 3.x: p95 latency — not actually aggregatable across proxies
bungeecord_online_player_latency{quantile="0.95"}
# 4.0, in seconds, correct across any number of proxies
histogram_quantile(0.95, sum by (le) (rate(minecraft_proxy_player_ping_seconds_bucket[5m])))

# 3.x: heap used
bungeecord_jvm_memory{type="used"}
# 4.0
jvm_memory_used_bytes{area="heap"}
```

## Dashboard

The bundled Grafana dashboard was rebuilt for the new names. Import
[`dashboards/proxy-overview.json`](../dashboards/proxy-overview.json); the 3.x dashboard
will not work against 4.0 metrics.

## Checklist

1. Stop the proxy.
2. Remove the old jar and the old `config.json`.
3. Drop in `bungeecord-prometheus-exporter-4.0.0.jar`.
4. Start the proxy; confirm `Prometheus metrics available at ...` in the log.
5. Check `curl localhost:9985/metrics` and confirm `minecraft_proxy_players_online` is there.
6. Update Prometheus alerts and recording rules using the table above.
7. Import the new dashboard.

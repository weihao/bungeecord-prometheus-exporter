[![en](https://img.shields.io/badge/lang-English-blue.svg)](https://github.com/weihao/bungeecord-prometheus-exporter/blob/main/README.md)
[![zh-Hans](https://img.shields.io/badge/lang-中文-red.svg)](https://github.com/weihao/bungeecord-prometheus-exporter/blob/main/README.zh-Hans.md)
[![jp](https://img.shields.io/badge/lang-日本語-yellow.svg)](https://github.com/weihao/bungeecord-prometheus-exporter/blob/main/README.jp.md)
[![Build](https://github.com/weihao/bungeecord-prometheus-exporter/actions/workflows/build.yml/badge.svg)](https://github.com/weihao/bungeecord-prometheus-exporter/actions/workflows/build.yml)
![GitHub all releases](https://img.shields.io/github/downloads/weihao/bungeecord-prometheus-exporter/total)
<a href="https://www.buymeacoffee.com/nokb"><img src="https://img.buymeacoffee.com/button-api/?text=Buy Me a Coffee&emoji=☕&slug=nokb&button_colour=f07167&font_colour=000000&font_family=Arial&outline_colour=000000&coffee_colour=FFDD00" height="20px" /></a>

# Proxy Prometheus Exporter

Prometheus metrics for **Velocity**, **BungeeCord** and **Waterfall** — one jar, both
platforms, no configuration required to get started.

> Running a single server rather than a proxy? Use
> [minecraft-prometheus-exporter](https://github.com/sladkoff/minecraft-prometheus-exporter)
> instead. The two are complementary: this one watches the proxy, that one watches the
> backends.

## What you get

- **Player counts** — network total and per backend server
- **Backend health** — each backend is server-list-pinged on an interval, so
  `minecraft_proxy_server_up` tells you a server is unreachable *before* players do
- **Latency distribution** — a real histogram, so `histogram_quantile()` works and
  aggregates correctly across several proxies
- **Event counters** — logins by outcome, disconnects, kicks, chat, commands, server
  switches, MOTD pings
- **Standard JVM and process metrics** — plain `jvm_*` and `process_*`, so any off-the-shelf
  JVM dashboard works unmodified
- **Exporter self-metrics** — you can tell a broken exporter from a quiet one

Full list: [docs/METRICS.md](docs/METRICS.md).

## Requirements

- Java 17 or newer
- Velocity 3.4+ (including 4.x), BungeeCord (including 26.1), or Waterfall

One jar covers all of them; see [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) for why and
how that is verified.

## Quick start

Drop `bungeecord-prometheus-exporter-<version>.jar` into your proxy's `plugins/` directory
and start it. Metrics are then at:

```
http://<proxy-host>:9985/metrics
```

A commented `config.yml` is written to the plugin's data directory on first start. The
defaults are safe for production — the metrics that scale with your player base are off
until you turn them on.

### Prometheus

```yaml
scrape_configs:
  - job_name: 'minecraft-proxy'
    scrape_interval: 15s
    static_configs:
      - targets: ['proxy-1:9985']
        labels:
          proxy_name: 'eu-west'
      - targets: ['proxy-2:9985']
        labels:
          proxy_name: 'us-east'
```

Ready-made alerting rules: [examples/prometheus/alerts.yml](examples/prometheus/alerts.yml).

### Grafana

Import [`dashboards/proxy-overview.json`](dashboards/proxy-overview.json) — 26 panels
across overview, players, backend servers, activity, proxy runtime and exporter health.

Or bring up the whole stack locally, dashboard included:

```sh
mvn package
cp exporter-dist/target/bungeecord-prometheus-exporter-*.jar examples/docker/plugins/
cd examples/docker && docker compose up
```

See [examples/docker/README.md](examples/docker/README.md).

## Configuration

Every setting can come from the config file, a system property, or an environment
variable. Precedence, highest first:

1. environment variable — `MC_PROXY_EXPORTER_PORT=9985`
2. system property — `-Dmc.proxy.exporter.port=9985`
3. `config.yml`
4. the built-in default

The environment variable name is the setting's path, upper-cased, with dots and dashes
replaced by underscores:

| Setting | Environment variable |
| --- | --- |
| `port` | `MC_PROXY_EXPORTER_PORT` |
| `namespace` | `MC_PROXY_EXPORTER_NAMESPACE` |
| `auth.enabled` | `MC_PROXY_EXPORTER_AUTH_ENABLED` |
| `metrics.players-online` | `MC_PROXY_EXPORTER_METRICS_PLAYERS_ONLINE` |
| `collection.backend-ping-interval-seconds` | `MC_PROXY_EXPORTER_COLLECTION_BACKEND_PING_INTERVAL_SECONDS` |

That makes the plugin configurable in Docker without baking a config file into the image:

```yaml
services:
  proxy:
    image: itzg/mc-proxy
    environment:
      TYPE: VELOCITY
      MC_PROXY_EXPORTER_HOST: "0.0.0.0"
      MC_PROXY_EXPORTER_METRICS_BACKEND_HEALTH: "true"
    ports:
      - "25565:25565"
      - "9985:9985"
```

### config.yml

```yaml
host: 0.0.0.0          # 127.0.0.1 to keep the endpoint off the network
port: 9985
path: /metrics
namespace: minecraft_proxy

auth:
  enabled: false       # Prometheus supports this via basic_auth in scrape_configs
  username: ""
  password: ""

collection:
  backend-ping-interval-seconds: 30        # 0 disables backend health checks
  backend-ping-timeout-seconds: 5
  player-ping-sample-interval-seconds: 15  # 0 disables the latency histogram
  max-series-per-metric: 2000              # cardinality safety net

metrics:
  jvm: true
  proxy-info: true
  players-online: true
  players-per-server: true
  player-ping-histogram: true
  servers: true
  backend-health: true
  plugins: true
  events: true

  # high cardinality — one series per player. Leave off unless you need them.
  player-list: false
  player-ping-per-player: false
  command-label: false

  redisbungee: false

bstats: true
```

### Securing the endpoint

The metrics endpoint exposes player names only if you enable the high-cardinality metrics,
but it always exposes your topology. Two options:

```yaml
host: 127.0.0.1        # simplest: only reachable from the machine itself
```

```yaml
auth:                  # or require credentials
  enabled: true
  username: prometheus
  password: "${MC_PROXY_EXPORTER_AUTH_PASSWORD}"
```

with the matching Prometheus side:

```yaml
    basic_auth:
      username: prometheus
      password_file: /etc/prometheus/proxy-password
```

Credentials are compared in constant time, so the endpoint does not leak the secret to
anyone who can measure response times. The password is used exactly as written, so
leading and trailing spaces are significant.

Enabling auth also protects `/-/healthy`. If a Kubernetes liveness probe or a Docker
`HEALTHCHECK` watches that path, give it the same credentials:

```yaml
    livenessProbe:
      httpGet:
        path: /-/healthy
        port: 9985
        httpHeaders:
          - name: Authorization
            value: "Basic cHJvbWV0aGV1czpzM2NyZXQ="
```

## A note on cardinality

Prometheus stores one time series per unique combination of labels. A Minecraft proxy is
an excellent source of unbounded labels — player names, command strings, server pairs — and
an exporter that hands Prometheus an unbounded number of series takes the monitoring system
down with it.

So:

- the metrics that scale with your player base are **off by default**;
- when you do turn them on, `collection.max-series-per-metric` caps them, and
  `minecraft_proxy_exporter_series_dropped_total` tells you when the cap is biting;
- the command label is off by default because command text is player input.

For latency, prefer the histogram over the per-player gauges:

```promql
histogram_quantile(0.95, sum by (le) (rate(minecraft_proxy_player_ping_seconds_bucket[5m])))
```

That is one series per bucket regardless of whether you have ten players or ten thousand.

## RedisBungee

Set `metrics.redisbungee: true` to export network-wide counts from RedisBungee or
ValioBungee. It is bound reflectively, so no particular fork or version is required, and it
works on **both** BungeeCord and Velocity.

Off by default: with several proxies scraped individually, these values are identical on
every one of them, so summing them across instances multiplies the real number.

## Upgrading from 3.x

4.0 is a rewrite and every metric name changed. See
[docs/MIGRATION.md](docs/MIGRATION.md) for the full mapping and rewritten queries.

## Building

```sh
mvn package                 # unit tests + the shaded jar
mvn verify                  # adds end-to-end tests in Docker
mvn verify -Pcoverage       # with a JaCoCo report
```

The jar lands at `exporter-dist/target/bungeecord-prometheus-exporter-<version>.jar`.

The end-to-end suite boots real Velocity, BungeeCord and Waterfall containers against the
built jar, scrapes them over HTTP, and validates the output with the real
`promtool check metrics`. It skips itself when Docker is unavailable.

## Architecture

```
exporter-core/          platform-agnostic: config, metrics, HTTP endpoint, samplers
                        deliberately has no BungeeCord or Velocity dependency
exporter-bungeecord/    BungeeCord and Waterfall adapter
exporter-velocity/      Velocity adapter
exporter-dist/          shades all three into one jar with both plugin descriptors
```

The core talks to a small `ProxyPlatform` interface rather than to either proxy, which is
what lets the whole metric pipeline be tested in a plain JVM — including states that are
hard to produce on a real proxy, such as a backend that hangs forever or ten thousand
distinct player names.

## Links

- [Metric reference](docs/METRICS.md)
- [Compatibility and version policy](docs/COMPATIBILITY.md)
- [Migration from 3.x](docs/MIGRATION.md)
- Indexed in the Prometheus [exporter list](https://github.com/prometheus/docs/blob/main/content/docs/instrumenting/exporters.md#miscellaneous)
  and [default port allocations](https://github.com/prometheus/prometheus/wiki/Default-port-allocations#exporters-starting-at-9100) (9985)

## License

MIT — see [LICENSE](LICENSE).

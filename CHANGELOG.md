# Changelog

## 4.0.0

A complete rewrite. Every metric name changed and the config file changed format — see
[docs/MIGRATION.md](docs/MIGRATION.md) for the full mapping.

### Breaking

- **All metrics renamed.** Counters now carry `_total`, durations are in seconds, and
  standard JVM metrics are no longer namespaced. The default prefix is `minecraft_proxy`
  rather than `bungeecord_`.
- **`config.json` replaced by `config.yml`.** Delete the old file; a commented default is
  written on first start.
- **Per-player metrics are off by default.** `player-list` and `player-ping-per-player`
  emit one series per player, which is the fastest way to overwhelm a Prometheus server.
- **Player latency is a histogram, not a Summary.** Summary quantiles cannot be aggregated
  across proxies; `histogram_quantile()` can.
- **`bungeecord_redis_player_connects` / `_disconnects` removed.** See the migration guide.

### Added

- **Velocity is now a first-class platform**, not an afterthought. One jar loads on
  Velocity 3.4 through 4.x, BungeeCord including 26.1, and Waterfall.
- **Backend health checks.** Each backend is server-list-pinged on an interval;
  `minecraft_proxy_server_up` reports reachability before players notice, along with ping
  latency and the backend's own player counts.
- **Environment and system-property overrides** for every setting, so the plugin is
  configurable in Docker and Kubernetes without a config file.
- **HTTP basic auth** on the metrics endpoint, with constant-time credential comparison.
- **A cardinality budget.** `collection.max-series-per-metric` caps label combinations per
  metric and counts what it drops, so a runaway label set degrades one metric instead of
  taking Prometheus down.
- **Exporter self-metrics**: scrape errors by collector, dropped series by metric, sampler
  runs and durations, and build info.
- **Server switch, connection and login-outcome counters.**
- **Standard `process_*` metrics** alongside `jvm_*`.
- **A `/-/healthy` endpoint** that answers without running the collectors.
- **RedisBungee works on Velocity too** — it is bound reflectively, so no compile
  dependency on JitPack and no particular fork required.
- **Generated documentation.** `docs/METRICS.md` is produced from a live scrape and
  verified in CI, so it cannot drift from the code.
- **An end-to-end test suite** that boots real Velocity, BungeeCord and Waterfall
  containers against the built jar and validates the output with the real
  `promtool check metrics`.

### Changed

- **Migrated from the deprecated `simpleclient` 0.16.0 to `prometheus-metrics` 1.9.0.**
- **Dropped Jetty.** The endpoint now runs on the JDK's own HTTP server, which removes
  Jetty, `jakarta.servlet`, json-path and gson from the jar and with them a common source
  of classloader conflicts between plugins.
- **Text-format exposition only.** Excluding the protobuf exposition module saves about
  2 MB of jar.
- **OpenMetrics content negotiation** is supported.
- **A failing collector no longer fails the whole scrape.** Previously one throwing metric
  turned `/metrics` into an HTTP 500, which made Prometheus record the entire proxy as
  down and lose every other metric with it.
- **The exporter now shuts down cleanly.** 3.x never stopped its HTTP server, so a reload
  left the port bound and the exporter dead until the proxy restarted.
- **Each instance gets its own metric registry** rather than the JVM-wide default, so
  repeated enable/disable cycles cannot collide.
- **Backend pings and latency sampling run on a background thread**, never in the scrape
  path, so an unreachable backend cannot time out a scrape.
- **The jar is compiled to Java 17**, the highest target every supported proxy can load.
- **The bundled Grafana dashboard was rebuilt** and is generated from a script, so a metric
  rename is visible in review instead of buried in exported JSON.

### Fixed

- Counters no longer lack `_total`, latency is no longer reported in milliseconds under a
  unit-less name, and JVM metrics are no longer namespaced — all three are now enforced by
  `MetricNamingConventionTest` and by `promtool` in the end-to-end suite.
- The Velocity plugin no longer reports a hard-coded version of `1.0.0`; the descriptor is
  filtered from the POM.
- The configured metrics path is now actually honoured. The endpoint previously answered
  with a full scrape on *every* URL, so any stray request triggered a collection pass.
- A build bug where the shaded jar could silently ship stale classes on an incremental
  build has been fixed.

## 3.x and earlier

See the [release history](https://github.com/weihao/bungeecord-prometheus-exporter/releases).

#!/usr/bin/env python3
"""Generates dashboards/proxy-overview.json.

The dashboard is built from code rather than exported from Grafana so that the PromQL
lives somewhere readable and reviewable. A Grafana JSON export is 1000 lines of
machine-written noise in which a metric rename is invisible in review; this is a few
hundred lines where every query is on one line.

Regenerate after changing a metric name:

    python dashboards/build_dashboard.py
"""

import json
import pathlib

DS = {"type": "prometheus", "uid": "${datasource}"}
SEL = '{job=~"$job", instance=~"$instance"}'


def target(expr, legend, ref="A", instant=False):
    return {
        "datasource": DS,
        "editorMode": "code",
        "expr": expr,
        "legendFormat": legend,
        "range": not instant,
        "instant": instant,
        "refId": ref,
    }


def grid(x, y, w, h):
    return {"h": h, "w": w, "x": x, "y": y}


def defaults(unit=None, extra=None, thresholds=None):
    d = {
        "color": {"mode": "palette-classic"},
        "mappings": [],
        "thresholds": thresholds
        or {"mode": "absolute", "steps": [{"color": "green", "value": None}]},
    }
    if unit:
        d["unit"] = unit
    if extra:
        d.update(extra)
    return d


def stat(title, expr, legend, gp, unit=None, thresholds=None,
         text_mode="auto", color_mode="value", desc=None):
    panel = {
        "type": "stat",
        "title": title,
        "datasource": DS,
        "gridPos": gp,
        "fieldConfig": {"defaults": defaults(unit, thresholds=thresholds), "overrides": []},
        "options": {
            "colorMode": color_mode,
            "graphMode": "area",
            "justifyMode": "auto",
            "orientation": "auto",
            "textMode": text_mode,
            "reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
        },
        "targets": [target(expr, legend, instant=True)],
    }
    if desc:
        panel["description"] = desc
    return panel


def timeseries(title, targets, gp, unit=None, fill=10, stack=False, desc=None, minv=0):
    custom = {
        "axisBorderShow": False,
        "axisCenteredZero": False,
        "axisLabel": "",
        "axisPlacement": "auto",
        "barAlignment": 0,
        "drawStyle": "line",
        "fillOpacity": fill,
        "gradientMode": "opacity",
        "hideFrom": {"legend": False, "tooltip": False, "viz": False},
        "insertNulls": False,
        "lineInterpolation": "smooth",
        "lineWidth": 2,
        "pointSize": 5,
        "scaleDistribution": {"type": "linear"},
        "showPoints": "never",
        "spanNulls": False,
        "stacking": {"group": "A", "mode": "normal" if stack else "none"},
        "thresholdsStyle": {"mode": "off"},
    }
    d = defaults(unit, extra={"custom": custom})
    if minv is not None:
        d["min"] = minv
    panel = {
        "type": "timeseries",
        "title": title,
        "datasource": DS,
        "gridPos": gp,
        "fieldConfig": {"defaults": d, "overrides": []},
        "options": {
            "legend": {
                "calcs": ["mean", "max", "lastNotNull"],
                "displayMode": "table",
                "placement": "right",
                "showLegend": True,
            },
            "tooltip": {"mode": "multi", "sort": "desc"},
        },
        "targets": targets,
    }
    if desc:
        panel["description"] = desc
    return panel


def row(title, y):
    return {
        "type": "row",
        "title": title,
        "gridPos": grid(0, y, 24, 1),
        "collapsed": False,
        "panels": [],
    }


def build():
    panels = []
    y = 0

    # ---------------------------------------------------------------- overview
    panels.append(row("Overview", y))
    y += 1
    panels.append(stat("Players online",
                       "sum(minecraft_proxy_players_online" + SEL + ")",
                       "players", grid(0, y, 3, 4)))
    panels.append(stat("Proxies up",
                       "count(up" + SEL + " == 1)",
                       "proxies", grid(3, y, 3, 4)))
    panels.append(stat("Backends up",
                       "sum(minecraft_proxy_server_up" + SEL + ")",
                       "up", grid(6, y, 3, 4),
                       thresholds={"mode": "absolute",
                                   "steps": [{"color": "red", "value": None},
                                             {"color": "green", "value": 1}]}))
    panels.append(stat("Backends down",
                       "sum(minecraft_proxy_servers_registered" + SEL + ")"
                       " - sum(minecraft_proxy_server_up" + SEL + ")",
                       "down", grid(9, y, 3, 4),
                       thresholds={"mode": "absolute",
                                   "steps": [{"color": "green", "value": None},
                                             {"color": "red", "value": 1}]}))
    panels.append(stat("p95 latency",
                       "histogram_quantile(0.95, sum by (le) "
                       "(rate(minecraft_proxy_player_ping_seconds_bucket"
                       + SEL + "[$__rate_interval])))",
                       "p95", grid(12, y, 3, 4), unit="s",
                       thresholds={"mode": "absolute",
                                   "steps": [{"color": "green", "value": None},
                                             {"color": "yellow", "value": 0.2},
                                             {"color": "red", "value": 0.4}]}))
    panels.append(stat("Logins / min",
                       "sum(rate(minecraft_proxy_logins_total"
                       '{job=~"$job", instance=~"$instance", result="allowed"}'
                       "[$__rate_interval])) * 60",
                       "logins", grid(15, y, 3, 4)))
    panels.append(stat("Proxy version",
                       "minecraft_proxy_info" + SEL,
                       "{{name}} {{version}}", grid(18, y, 3, 4),
                       text_mode="name", color_mode="none"))
    panels.append(stat("Exporter version",
                       "minecraft_proxy_exporter_build_info" + SEL,
                       "{{version}}", grid(21, y, 3, 4),
                       text_mode="name", color_mode="none"))
    y += 4

    # ----------------------------------------------------------------- players
    panels.append(row("Players", y))
    y += 1
    panels.append(timeseries(
        "Players online, by backend server",
        [target("sum by (server) (minecraft_proxy_server_players_online" + SEL + ")",
                "{{server}}")],
        grid(0, y, 12, 8), stack=True,
        desc="Derived from the proxy's player list, so the stack always sums to the "
             "network total."))
    panels.append(timeseries(
        "Players online, by proxy",
        [target("sum by (instance) (minecraft_proxy_players_online" + SEL + ")",
                "{{instance}}")],
        grid(12, y, 12, 8)))
    y += 8

    panels.append(timeseries(
        "Player latency percentiles",
        [target("histogram_quantile(0.5, sum by (le) "
                "(rate(minecraft_proxy_player_ping_seconds_bucket" + SEL
                + "[$__rate_interval])))", "p50", "A"),
         target("histogram_quantile(0.95, sum by (le) "
                "(rate(minecraft_proxy_player_ping_seconds_bucket" + SEL
                + "[$__rate_interval])))", "p95", "B"),
         target("histogram_quantile(0.99, sum by (le) "
                "(rate(minecraft_proxy_player_ping_seconds_bucket" + SEL
                + "[$__rate_interval])))", "p99", "C")],
        grid(0, y, 12, 8), unit="s",
        desc="A histogram, so these percentiles aggregate correctly across every proxy "
             "in the selection. Summary quantiles could not be combined this way."))
    panels.append(timeseries(
        "Connection lifecycle",
        [target("sum(rate(minecraft_proxy_connections_total" + SEL
                + "[$__rate_interval])) * 60", "connections", "A"),
         target("sum(rate(minecraft_proxy_logins_total"
                '{job=~"$job", instance=~"$instance", result="allowed"}'
                "[$__rate_interval])) * 60", "logins allowed", "B"),
         target("sum(rate(minecraft_proxy_logins_total"
                '{job=~"$job", instance=~"$instance", result="denied"}'
                "[$__rate_interval])) * 60", "logins denied", "C"),
         target("sum(rate(minecraft_proxy_disconnects_total" + SEL
                + "[$__rate_interval])) * 60", "disconnects", "D")],
        grid(12, y, 12, 8), unit="cpm"))
    y += 8

    # ---------------------------------------------------------------- backends
    panels.append(row("Backend servers", y))
    y += 1
    panels.append({
        "type": "state-timeline",
        "title": "Backend reachability",
        "datasource": DS,
        "gridPos": grid(0, y, 12, 8),
        "description": "Sampled by actually server-list-pinging each backend, so a red "
                       "band appears before players start failing to connect.",
        "fieldConfig": {
            "defaults": {
                "color": {"mode": "thresholds"},
                "custom": {"fillOpacity": 85, "insertNulls": False, "lineWidth": 0,
                           "spanNulls": False},
                "mappings": [{"type": "value",
                              "options": {"0": {"text": "down", "color": "red", "index": 0},
                                          "1": {"text": "up", "color": "green", "index": 1}}}],
                "thresholds": {"mode": "absolute",
                               "steps": [{"color": "red", "value": None},
                                         {"color": "green", "value": 1}]},
            },
            "overrides": [],
        },
        "options": {"alignValue": "left",
                    "legend": {"displayMode": "list", "placement": "bottom",
                               "showLegend": False},
                    "mergeValues": True, "rowHeight": 0.9, "showValue": "never",
                    "tooltip": {"mode": "single", "sort": "none"}},
        "targets": [target("minecraft_proxy_server_up" + SEL, "{{server}}")],
    })
    panels.append(timeseries(
        "Backend ping latency",
        [target("minecraft_proxy_server_ping_seconds" + SEL, "{{server}}")],
        grid(12, y, 12, 8), unit="s"))
    y += 8

    panels.append(timeseries(
        "Server switches per minute",
        [target("sum by (to) (rate(minecraft_proxy_server_switches_total" + SEL
                + "[$__rate_interval])) * 60", "to {{to}}")],
        grid(0, y, 8, 7), unit="cpm"))
    panels.append(timeseries(
        "Kicks per minute, by origin server",
        [target("sum by (server) (rate(minecraft_proxy_kicks_total" + SEL
                + "[$__rate_interval])) * 60", "{{server}}")],
        grid(8, y, 8, 7), unit="cpm"))
    panels.append(timeseries(
        "Backend capacity",
        [target("minecraft_proxy_server_reported_players" + SEL
                + " / minecraft_proxy_server_reported_max_players" + SEL, "{{server}}")],
        grid(16, y, 8, 7), unit="percentunit"))
    y += 7

    # ---------------------------------------------------------------- activity
    panels.append(row("Activity", y))
    y += 1
    panels.append(timeseries(
        "Chat and commands per minute",
        [target("sum(rate(minecraft_proxy_chat_messages_total" + SEL
                + "[$__rate_interval])) * 60", "chat", "A"),
         target("sum(rate(minecraft_proxy_commands_total" + SEL
                + "[$__rate_interval])) * 60", "commands", "B")],
        grid(0, y, 8, 7), unit="cpm"))
    panels.append(timeseries(
        "Server-list pings per minute",
        [target("sum(rate(minecraft_proxy_list_pings_total" + SEL
                + "[$__rate_interval])) * 60", "MOTD pings")],
        grid(8, y, 8, 7), unit="cpm",
        desc="Clients viewing the server in their multiplayer list. A useful proxy for "
             "interest that never converts into a join."))
    panels.append({
        "type": "table",
        "title": "Plugins",
        "datasource": DS,
        "gridPos": grid(16, y, 8, 7),
        "description": "Useful for spotting a proxy left behind by a partial rollout.",
        "fieldConfig": {"defaults": defaults(), "overrides": []},
        "options": {"showHeader": True, "cellHeight": "sm",
                    "footer": {"show": False, "reducer": ["sum"], "countRows": False,
                               "fields": ""}},
        "transformations": [
            {"id": "labelsToFields", "options": {"mode": "columns"}},
            {"id": "organize",
             "options": {"excludeByName": {"Time": True, "Value": True,
                                           "__name__": True, "job": True},
                         "renameByName": {"plugin": "Plugin", "version": "Version",
                                          "instance": "Proxy"}}},
        ],
        "targets": [target("minecraft_proxy_plugin_info" + SEL, "", instant=True)],
    })
    y += 7

    # ----------------------------------------------------------------- runtime
    panels.append(row("Proxy runtime", y))
    y += 1
    panels.append(timeseries(
        "JVM heap",
        [target("sum by (instance) (jvm_memory_used_bytes"
                '{job=~"$job", instance=~"$instance", area="heap"})', "{{instance}} used", "A"),
         target("sum by (instance) (jvm_memory_max_bytes"
                '{job=~"$job", instance=~"$instance", area="heap"})', "{{instance}} max", "B")],
        grid(0, y, 8, 7), unit="bytes",
        desc="Standard jvm_* metrics, not namespaced, so any JVM dashboard works against "
             "this target unmodified."))
    panels.append(timeseries(
        "CPU",
        [target("rate(process_cpu_seconds_total" + SEL + "[$__rate_interval])",
                "{{instance}}")],
        grid(8, y, 8, 7), unit="percentunit"))
    panels.append(timeseries(
        "Threads",
        [target("jvm_threads_current" + SEL, "{{instance}}")],
        grid(16, y, 8, 7)))
    y += 7

    # --------------------------------------------------------- exporter health
    panels.append(row("Exporter health", y))
    y += 1
    panels.append(timeseries(
        "Collector failures",
        [target("sum by (collector) (rate(minecraft_proxy_exporter_scrape_errors_total"
                + SEL + "[$__rate_interval]))", "{{collector}}")],
        grid(0, y, 8, 6),
        desc="Non-zero means that collector threw and its metrics are missing from every "
             "scrape. The stack trace is in the proxy log, logged once per collector."))
    panels.append(timeseries(
        "Series dropped by the cardinality budget",
        [target("sum by (metric) (rate(minecraft_proxy_exporter_series_dropped_total"
                + SEL + "[$__rate_interval]))", "{{metric}}")],
        grid(8, y, 8, 6),
        desc="Raise collection.max-series-per-metric, or turn off the high-cardinality "
             "metric groups."))
    panels.append(timeseries(
        "Sampler duration",
        [target("histogram_quantile(0.95, sum by (le, sampler) "
                "(rate(minecraft_proxy_exporter_sampler_duration_seconds_bucket" + SEL
                + "[$__rate_interval])))", "{{sampler}} p95")],
        grid(16, y, 8, 6), unit="s"))

    return panels


def main():
    panels = build()
    restart_expr = ("changes(minecraft_proxy_start_time_seconds"
                    '{job=~"$job"}[$__interval]) > 0')
    dashboard = {
        "annotations": {"list": [
            {"builtIn": 1,
             "datasource": {"type": "grafana", "uid": "-- Grafana --"},
             "enable": True, "hide": True, "iconColor": "rgba(0, 211, 255, 1)",
             "name": "Annotations & Alerts", "type": "dashboard"},
            {"datasource": DS, "enable": True, "hide": False, "iconColor": "purple",
             "name": "Proxy restarts", "expr": restart_expr,
             "titleFormat": "Proxy restarted", "textFormat": "{{instance}}",
             "target": {"expr": restart_expr, "refId": "Anno"}},
        ]},
        "description": "Velocity / BungeeCord / Waterfall proxy metrics from "
                       "bungeecord-prometheus-exporter 4.x.",
        "editable": True,
        "graphTooltip": 1,
        "links": [{"asDropdown": False, "icon": "external link", "tags": [],
                   "targetBlank": True, "title": "Metric reference", "tooltip": "",
                   "type": "link",
                   "url": "https://github.com/weihao/bungeecord-prometheus-exporter"
                          "/blob/main/docs/METRICS.md"}],
        "panels": panels,
        "refresh": "30s",
        "schemaVersion": 39,
        "tags": ["minecraft", "proxy", "velocity", "bungeecord", "prometheus"],
        "templating": {"list": [
            {"current": {}, "hide": 0, "includeAll": False, "label": "Data source",
             "multi": False, "name": "datasource", "options": [], "query": "prometheus",
             "refresh": 1, "regex": "", "skipUrlSync": False, "type": "datasource"},
            {"current": {}, "datasource": DS,
             "definition": "label_values(minecraft_proxy_info, job)",
             "hide": 0, "includeAll": True, "label": "Job", "multi": True, "name": "job",
             "options": [],
             "query": {"query": "label_values(minecraft_proxy_info, job)", "refId": "job"},
             "refresh": 1, "regex": "", "skipUrlSync": False, "sort": 1, "type": "query"},
            {"current": {}, "datasource": DS,
             "definition": 'label_values(minecraft_proxy_info{job=~"$job"}, instance)',
             "hide": 0, "includeAll": True, "label": "Proxy", "multi": True,
             "name": "instance", "options": [],
             "query": {"query": 'label_values(minecraft_proxy_info{job=~"$job"}, instance)',
                       "refId": "instance"},
             "refresh": 1, "regex": "", "skipUrlSync": False, "sort": 1, "type": "query"},
        ]},
        "time": {"from": "now-6h", "to": "now"},
        "timepicker": {},
        "timezone": "browser",
        "title": "Minecraft Proxy Overview",
        "uid": "minecraft-proxy-overview",
        "version": 1,
        "weekStart": "",
    }

    out = pathlib.Path(__file__).parent / "proxy-overview.json"
    out.write_text(json.dumps(dashboard, indent=2) + "\n", encoding="utf-8")
    rows = sum(1 for p in panels if p["type"] == "row")
    print("wrote", out, "-", len(panels) - rows, "panels in", rows, "rows")


if __name__ == "__main__":
    main()

# Bungeecord Prometheus Exporter

A **Bungee plugin** which exports network stats for Prometheus.

## Why Bungeecord Prometheus Exporter?
- monitor your server infrastructure
- track your players, events, and servers
- automates the collection, management and viewing of your data
- get alerts for service outages 

## Quick Start

Drop the bungeecord-prometheus-exporter.jar into your Bungeecord plugins directory and start your Bungeecord proxy server.

After startup, the Prometheus metrics endpoint should be available at ``localhost:9225/metrics`` (assuming localhost is the server hostname).

If running inside the docker, change the host to `0.0.0.0` to allow Prometheus and other services to reach the endpoint.

The metrics port can be customized in the plugin's config.yml (a default config will be created after the first use).

## Import Grafana Dashboard

1. Navigate to Grafana -> Dashboards -> Import
1. Paste in or upload [default dashboard](https://raw.githubusercontent.com/weihao/bungeecord-prometheus-exporter/main/dashboards/default.json)
1. ![default dashboard](https://raw.githubusercontent.com/weihao/bungeecord-prometheus-exporter/main/images/dashboard.PNG)

## Credit
Thanks to @sladkoff
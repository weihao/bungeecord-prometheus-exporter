[![Release](https://github.com/weihao/bungeecord-prometheus-exporter/actions/workflows/release.yml/badge.svg)](https://github.com/weihao/bungeecord-prometheus-exporter/actions/workflows/release.yml)
![GitHub all releases](https://img.shields.io/github/downloads/weihao/bungeecord-prometheus-exporter/total)

# Bungeecord Prometheus Exporter

A **Bungee plugin** which exports network stats for Prometheus.

> If you don't run a network proxy, you might also be interested in [Prometheus Exporter](https://github.com/sladkoff/minecraft-prometheus-exporter) for a `single server` metrics!

## Why Bungeecord Prometheus Exporter?

- monitor your server infrastructure
- track your players, events, and servers
- automates the collection, management and viewing of your data
- get alerts for service outages

## Quick Start

Drop the bungeecord-prometheus-exporter.jar into your Bungeecord plugins directory and start your Bungeecord proxy
server.

After startup, the Prometheus metrics endpoint should be available at ``localhost:9225/metrics`` (assuming localhost is
the server hostname).

If running inside the docker, change the host to `0.0.0.0` to allow Prometheus and other services to reach the endpoint.

The metrics port can be customized in the plugin's config.yml (a default config will be created after the first use).

## Prometheus config

Add the following job to the ``scrape_configs`` section of your Prometheus configuration `prometheus.yml`:

### Single Proxy

```yml
  - job_name: 'bungeecord'
    scrape_interval: 5s

    static_configs:
       - targets: ['localhost:9225']
         labels:
            server_name: 'proxy1'
```

### Multiple proxies

You can use labels in your Prometheus scrape configuration to distinguish between multiple proxies:
```yml
  - job_name: 'bungeecord'
    scrape_interval: 5s

    static_configs:
       - targets: ['localhost:9225']
         labels:
            server_name: 'proxy1'
       - targets: ['localhost:9226']
         labels:
            server_name: 'proxy2'
```

## Import Grafana Dashboard

1. Navigate to Grafana -> Dashboards -> Import
1. Paste in or
   upload [default dashboard](https://github.com/weihao/bungeecord-prometheus-exporter/tree/main/dashboards)
1. ![default dashboard](https://raw.githubusercontent.com/weihao/bungeecord-prometheus-exporter/main/images/dashboard.jpg)

## Notes


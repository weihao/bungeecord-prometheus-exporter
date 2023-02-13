package org.akadia.prometheus;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Enumeration;

public class MetricsServer {

    private final String host;
    private final int port;
    private final PrometheusExporter prometheusExporter;

    private Server server;

    public MetricsServer(String host, int port, PrometheusExporter prometheusExporter) {
        this.host = host;
        this.port = port;
        this.prometheusExporter = prometheusExporter;
    }

    public void start() throws Exception {
        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request request, HttpServletRequest httpServletRequest, HttpServletResponse response) throws IOException {
                if (!target.equals("/metrics")) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                try {
                    MetricRegistry.getInstance().collectMetrics();
                    response.setStatus(HttpStatus.OK_200);
                    response.setContentType(TextFormat.CONTENT_TYPE_004);
                    Enumeration<Collector.MetricFamilySamples> metricFamilySamplesEnumeration = CollectorRegistry.defaultRegistry.metricFamilySamples();

                    TextFormat.write004(response.getWriter(), CollectorRegistry.defaultRegistry.metricFamilySamples());

                    request.setHandled(true);
                } catch (IOException e) {
                    prometheusExporter.warn("Failed to read server statistic: " + e.getMessage());
                    response.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500);
                }

            }
        });

        InetSocketAddress address = new InetSocketAddress(host, port);
        server = new Server(address);
        server.setHandler(gzipHandler);

        server.start();
    }

    public void stop() throws Exception {
        if (server == null) {
            return;
        }

        server.stop();
    }
}
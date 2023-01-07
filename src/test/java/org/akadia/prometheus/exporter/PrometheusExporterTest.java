package org.akadia.prometheus.exporter;


import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.exporter.common.TextFormat;
import io.restassured.RestAssured;
import org.akadia.prometheus.MetricsServer;
import org.akadia.prometheus.PrometheusExporter;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.URIUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class PrometheusExporterTest {

    @Mock
    private PrometheusExporter exporterMock;

    private int metricsServerPort;
    private MetricsServer metricsServer;

    @BeforeEach
    void setup() throws Exception {
        CollectorRegistry.defaultRegistry.clear();
        metricsServerPort = getRandomFreePort();
        metricsServer = new MetricsServer("localhost", metricsServerPort, exporterMock);
        metricsServer.start();
    }

    private int getRandomFreePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        metricsServer.stop();
    }

    @Test
    void metrics_server_should_return_valid_prometheus_response() {
        mockPrometheusCounter("bungeecord_online_players", "This is a mock metric of online bungeecord players", 233);

        String requestPath = URIUtil.newURI("http", "localhost", metricsServerPort, "/metrics", null);
        String responseText = RestAssured.when()
                .get(requestPath)
                .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(TextFormat.CONTENT_TYPE_004)
                .extract()
                .asString();

        String[] lines = responseText.split("\n");
        assertEquals(lines[0], "# HELP bungeecord_online_players_total This is a mock metric of online bungeecord players");
        assertEquals(lines[1], "# TYPE bungeecord_online_players_total counter");
        assertEquals(lines[2], "bungeecord_online_players_total 233.0");
    }

    private void mockPrometheusCounter(String name, String help, int value) {
        Counter mockPrometheusCounter = Counter.build().name(name).help(help).register();
        mockPrometheusCounter.inc(value);
    }

    @Test
    void metrics_server_should_return_404_on_unknown_paths() {
        String requestPath = URIUtil.newURI("http", "localhost", metricsServerPort, "/unknown-path", null);

        RestAssured.when()
                .get(requestPath)
                .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
    }

}
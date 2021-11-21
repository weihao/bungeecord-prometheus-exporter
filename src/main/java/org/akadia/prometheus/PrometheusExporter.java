package org.akadia.prometheus;

public interface PrometheusExporter {
    void info(String info);

    void warn(String warning);

    String getPrefix();
}

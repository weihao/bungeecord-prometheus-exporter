package org.akadia.prometheus.core.config;

/** Raised when configuration is present but unusable. Always carries an actionable message. */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

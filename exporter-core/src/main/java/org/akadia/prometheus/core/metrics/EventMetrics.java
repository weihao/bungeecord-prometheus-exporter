package org.akadia.prometheus.core.metrics;

import io.prometheus.metrics.core.metrics.Counter;
import org.akadia.prometheus.core.config.ExporterConfig;
import org.akadia.prometheus.core.config.MetricId;

import java.util.Locale;

/**
 * Counters driven by proxy events. This is the API the BungeeCord and Velocity adapters
 * call from their listeners.
 *
 * <p>All counters carry the {@code _total} suffix and only ever increase, so they are
 * meaningful under {@code rate()} and survive proxy restarts as a counter reset rather
 * than a bogus spike.
 *
 * <p>Every method is safe to call from any thread and is a no-op when
 * {@code metrics.events} is off, so adapters never need a null check.
 */
public final class EventMetrics {

    /** Longest command token kept as a label value. */
    private static final int MAX_COMMAND_LENGTH = 32;

    private static final String COMMAND_OTHER = "other";
    private static final String UNKNOWN = "unknown";

    private final boolean enabled;
    private final boolean commandLabelEnabled;
    private final CardinalityGuard guard;

    private final Counter logins;
    private final Counter connections;
    private final Counter disconnects;
    private final Counter kicks;
    private final Counter chatMessages;
    private final Counter commands;
    private final Counter serverSwitches;
    private final Counter listPings;

    private final String kicksMetric;
    private final String commandsMetric;
    private final String switchesMetric;

    public EventMetrics(ExporterConfig config, CardinalityGuard guard, MetricRegistrar registrar) {
        this.enabled = config.isEnabled(MetricId.EVENTS);
        this.commandLabelEnabled = config.isEnabled(MetricId.COMMAND_LABEL);
        this.guard = guard;
        this.kicksMetric = config.metricName(MetricNames.KICKS_TOTAL);
        this.commandsMetric = config.metricName(MetricNames.COMMANDS_TOTAL);
        this.switchesMetric = config.metricName(MetricNames.SERVER_SWITCHES_TOTAL);

        if (!enabled) {
            this.logins = null;
            this.connections = null;
            this.disconnects = null;
            this.kicks = null;
            this.chatMessages = null;
            this.commands = null;
            this.serverSwitches = null;
            this.listPings = null;
            return;
        }

        this.connections = registrar.register(MetricNames.CONNECTIONS_TOTAL, Counter.builder()
                .name(config.metricName(MetricNames.CONNECTIONS_TOTAL))
                .help("Connections accepted by the proxy, before authentication")
                .build());

        this.logins = registrar.register(MetricNames.LOGINS_TOTAL, Counter.builder()
                .name(config.metricName(MetricNames.LOGINS_TOTAL))
                .help("Login attempts by outcome: allowed or denied")
                .labelNames(MetricNames.LABEL_RESULT)
                .build());
        // Pre-create both outcomes so rate() has a baseline before the first denial.
        this.logins.initLabelValues("allowed");
        this.logins.initLabelValues("denied");

        this.disconnects = registrar.register(MetricNames.DISCONNECTS_TOTAL, Counter.builder()
                .name(config.metricName(MetricNames.DISCONNECTS_TOTAL))
                .help("Players that disconnected from the proxy")
                .build());

        this.kicks = registrar.register(MetricNames.KICKS_TOTAL, Counter.builder()
                .name(kicksMetric)
                .help("Players kicked from a backend server")
                .labelNames(MetricNames.LABEL_SERVER)
                .build());

        this.chatMessages = registrar.register(MetricNames.CHAT_MESSAGES_TOTAL, Counter.builder()
                .name(config.metricName(MetricNames.CHAT_MESSAGES_TOTAL))
                .help("Chat messages sent through the proxy, excluding commands")
                .build());

        Counter.Builder commandsBuilder = Counter.builder()
                .name(commandsMetric)
                .help("Commands executed through the proxy"
                        + (commandLabelEnabled ? ", labelled by command root" : ""));
        if (commandLabelEnabled) {
            commandsBuilder.labelNames(MetricNames.LABEL_COMMAND);
        }
        this.commands = registrar.register(MetricNames.COMMANDS_TOTAL, commandsBuilder.build());

        this.serverSwitches = registrar.register(MetricNames.SERVER_SWITCHES_TOTAL, Counter.builder()
                .name(switchesMetric)
                .help("Players moving between backend servers")
                .labelNames(MetricNames.LABEL_FROM, MetricNames.LABEL_TO)
                .build());

        this.listPings = registrar.register(MetricNames.LIST_PINGS_TOTAL, Counter.builder()
                .name(config.metricName(MetricNames.LIST_PINGS_TOTAL))
                .help("Server-list pings answered by the proxy, i.e. clients viewing the MOTD")
                .build());
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** A raw connection reached the proxy. */
    public void recordConnection() {
        if (enabled) {
            connections.inc();
        }
    }

    /** A login completed. {@code allowed} is false when a plugin or the proxy denied it. */
    public void recordLogin(boolean allowed) {
        if (enabled) {
            logins.labelValues(allowed ? "allowed" : "denied").inc();
        }
    }

    public void recordDisconnect() {
        if (enabled) {
            disconnects.inc();
        }
    }

    /** A player was kicked from {@code server}, which may be null if the proxy did not say. */
    public void recordKick(String server) {
        if (!enabled) {
            return;
        }
        String value = labelOrUnknown(server);
        if (guard.admit(kicksMetric, value)) {
            kicks.labelValues(value).inc();
        }
    }

    public void recordChat() {
        if (enabled) {
            chatMessages.inc();
        }
    }

    /**
     * A command was executed.
     *
     * @param rawCommand the full command line; only its first token is ever used as a label,
     *                   and only when {@code metrics.command-label} is on
     */
    public void recordCommand(String rawCommand) {
        if (!enabled) {
            return;
        }
        if (!commandLabelEnabled) {
            commands.inc();
            return;
        }
        String root = commandRoot(rawCommand);
        // Commands are attacker-controlled input, so an unbounded label here is a denial of
        // service against Prometheus. Anything past the budget collapses into "other".
        String value = guard.admit(commandsMetric, root) ? root : COMMAND_OTHER;
        commands.labelValues(value).inc();
    }

    /** A player moved between backend servers. {@code from} is null on the initial connect. */
    public void recordServerSwitch(String from, String to) {
        if (!enabled) {
            return;
        }
        String fromValue = labelOrUnknown(from);
        String toValue = labelOrUnknown(to);
        if (guard.admit(switchesMetric, fromValue, toValue)) {
            serverSwitches.labelValues(fromValue, toValue).inc();
        }
    }

    public void recordListPing() {
        if (enabled) {
            listPings.inc();
        }
    }

    /**
     * Reduces a command line to a stable label value.
     *
     * <p>{@code "/Tp  Notch 1 2 3"} becomes {@code "tp"}: leading slash removed, first token
     * only, lower-cased, non-identifier characters stripped, length capped.
     */
    static String commandRoot(String rawCommand) {
        if (rawCommand == null) {
            return UNKNOWN;
        }
        String trimmed = rawCommand.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int space = trimmed.indexOf(' ');
        if (space >= 0) {
            trimmed = trimmed.substring(0, space);
        }
        // Namespaced commands such as "minecraft:tp" keep only the command part.
        int colon = trimmed.lastIndexOf(':');
        if (colon >= 0 && colon < trimmed.length() - 1) {
            trimmed = trimmed.substring(colon + 1);
        }
        StringBuilder sanitised = new StringBuilder(Math.min(trimmed.length(), MAX_COMMAND_LENGTH));
        for (int i = 0; i < trimmed.length() && sanitised.length() < MAX_COMMAND_LENGTH; i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sanitised.append(Character.toLowerCase(c));
            }
        }
        return sanitised.isEmpty() ? UNKNOWN : sanitised.toString().toLowerCase(Locale.ROOT);
    }

    private static String labelOrUnknown(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}

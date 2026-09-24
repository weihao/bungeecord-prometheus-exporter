package org.akadia.prometheus.core.metrics;

/**
 * Every metric name this exporter emits, without the configurable namespace prefix.
 *
 * <p>Centralised so that the collectors, the documentation generator and
 * {@code MetricNamingConventionTest} all reason about the same list. Names follow the
 * Prometheus conventions: base units in the suffix, {@code _total} on counters,
 * {@code _info} on metadata metrics, no unit prefixes anywhere else.
 */
public final class MetricNames {

    private MetricNames() {
    }

    // -- proxy identity ---------------------------------------------------------------
    public static final String INFO = "info";
    public static final String START_TIME_SECONDS = "start_time_seconds";
    public static final String UPTIME_SECONDS = "uptime_seconds";

    // -- players ----------------------------------------------------------------------
    public static final String PLAYERS_ONLINE = "players_online";
    public static final String SERVER_PLAYERS_ONLINE = "server_players_online";
    public static final String PLAYER_ONLINE = "player_online";
    public static final String PLAYER_PING_SECONDS = "player_ping_seconds";
    public static final String PLAYER_LATENCY_SECONDS = "player_latency_seconds";

    // -- backend servers --------------------------------------------------------------
    public static final String SERVERS_REGISTERED = "servers_registered";
    public static final String SERVER_INFO = "server_info";
    public static final String SERVER_UP = "server_up";
    public static final String SERVER_PING_SECONDS = "server_ping_seconds";
    public static final String SERVER_REPORTED_PLAYERS = "server_reported_players";
    public static final String SERVER_REPORTED_MAX_PLAYERS = "server_reported_max_players";

    // -- plugins ----------------------------------------------------------------------
    public static final String PLUGINS_INSTALLED = "plugins_installed";
    public static final String PLUGIN_INFO = "plugin_info";

    // -- events -----------------------------------------------------------------------
    public static final String LOGINS_TOTAL = "logins_total";
    public static final String CONNECTIONS_TOTAL = "connections_total";
    public static final String DISCONNECTS_TOTAL = "disconnects_total";
    public static final String KICKS_TOTAL = "kicks_total";
    public static final String CHAT_MESSAGES_TOTAL = "chat_messages_total";
    public static final String COMMANDS_TOTAL = "commands_total";
    public static final String SERVER_SWITCHES_TOTAL = "server_switches_total";
    public static final String LIST_PINGS_TOTAL = "list_pings_total";

    // -- redisbungee ------------------------------------------------------------------
    public static final String REDIS_PLAYERS_ONLINE = "redis_players_online";
    public static final String REDIS_PROXIES_ONLINE = "redis_proxies_online";

    // -- exporter self-observability --------------------------------------------------
    public static final String EXPORTER_BUILD_INFO = "exporter_build_info";
    public static final String EXPORTER_SCRAPE_ERRORS_TOTAL = "exporter_scrape_errors_total";
    public static final String EXPORTER_SERIES_DROPPED_TOTAL = "exporter_series_dropped_total";
    public static final String EXPORTER_SAMPLER_RUNS_TOTAL = "exporter_sampler_runs_total";
    public static final String EXPORTER_SAMPLER_DURATION_SECONDS = "exporter_sampler_duration_seconds";

    // -- label names ------------------------------------------------------------------
    public static final String LABEL_SERVER = "server";
    public static final String LABEL_PLAYER = "player";
    public static final String LABEL_UUID = "uuid";
    public static final String LABEL_PLUGIN = "plugin";
    public static final String LABEL_VERSION = "version";
    public static final String LABEL_ADDRESS = "address";
    public static final String LABEL_RESULT = "result";
    public static final String LABEL_COMMAND = "command";
    public static final String LABEL_FROM = "from";
    public static final String LABEL_TO = "to";
    public static final String LABEL_METRIC = "metric";
    public static final String LABEL_COLLECTOR = "collector";
    public static final String LABEL_SAMPLER = "sampler";
    public static final String LABEL_TYPE = "type";
    public static final String LABEL_NAME = "name";
    public static final String LABEL_ONLINE_MODE = "online_mode";
}

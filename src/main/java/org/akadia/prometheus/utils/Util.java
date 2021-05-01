package org.akadia.prometheus.utils;

public class Util {
    private final static String COMMON_PREFIX = "bungee_";

    public static String prefix(String name) {
        return COMMON_PREFIX + name;
    }
}

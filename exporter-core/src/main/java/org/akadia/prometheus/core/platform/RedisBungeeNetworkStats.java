package org.akadia.prometheus.core.platform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * RedisBungee / ValioBungee integration, bound reflectively.
 *
 * <p>Reflection rather than a compile dependency, for three reasons:
 *
 * <ul>
 *   <li>RedisBungee is only published on JitPack, so compiling against it makes every
 *       build of this plugin depend on a third-party build service being up;</li>
 *   <li>the API has moved between forks and coordinates over the years, and reflection
 *       binds to whichever one the operator actually installed;</li>
 *   <li>{@code RedisBungeeAPI.getRedisBungeeApi()} has the same signature on the
 *       BungeeCord and Velocity builds, so one implementation covers both platforms -
 *       the previous exporter could only do this on BungeeCord.</li>
 * </ul>
 *
 * <p>The binding is resolved lazily and retried until it succeeds. RedisBungee's API
 * returns null until its own plugin has finished enabling, and plugin enable order is not
 * guaranteed on BungeeCord, so resolving once at startup and giving up would silently
 * leave the network metrics absent for the whole run. The plugin descriptors also declare
 * a soft dependency to get the ordering right; this is the belt to that pair of braces.
 */
public final class RedisBungeeNetworkStats implements NetworkStatsProvider {

    private static final String API_CLASS = "com.imaginarycode.minecraft.redisbungee.RedisBungeeAPI";
    private static final String ACCESSOR = "getRedisBungeeApi";

    private final ClassLoader classLoader;

    /** Resolved once the RedisBungee classes are visible; still null until then. */
    private volatile Handles handles;

    private RedisBungeeNetworkStats(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Creates a binding that resolves on first use and on every use until it succeeds.
     *
     * @param classLoader loader that can see the RedisBungee plugin's classes
     */
    public static RedisBungeeNetworkStats lazyBinding(ClassLoader classLoader) {
        return new RedisBungeeNetworkStats(classLoader);
    }

    @Override
    public boolean isAvailable() {
        return api() != null;
    }

    @Override
    public int networkPlayerCount() {
        Handles resolved = handles;
        Object api = api();
        if (api == null || resolved == null) {
            return 0;
        }
        Object value = invoke(resolved.playerCount(), api);
        return value instanceof Number number ? number.intValue() : 0;
    }

    @Override
    public int proxyCount() {
        Handles resolved = handles;
        Object api = api();
        if (api == null || resolved == null) {
            return 0;
        }
        Object value = invoke(resolved.allProxies(), api);
        return value instanceof Collection<?> collection ? collection.size() : 0;
    }

    /**
     * The live API instance, or null while RedisBungee is absent or still starting.
     *
     * <p>The reflective lookups are cached after the first success; before that the cost
     * is one failed {@code Class.forName} per scrape, which at one scrape every fifteen
     * seconds is not worth optimising away.
     */
    private Object api() {
        try {
            Handles resolved = handles;
            if (resolved == null) {
                Class<?> apiClass = Class.forName(API_CLASS, false, classLoader);
                resolved = new Handles(
                        apiClass.getMethod(ACCESSOR),
                        apiClass.getMethod("getPlayerCount"),
                        apiClass.getMethod("getAllProxies"));
                handles = resolved;
            }
            // Returns null until RedisBungee's own plugin has finished enabling.
            return resolved.accessor().invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static Object invoke(Method method, Object api) {
        try {
            return method.invoke(api);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            // Redis can be unreachable mid-scrape. Throwing rather than reporting 0 is
            // deliberate: a silent zero looks like an empty network, whereas the collector
            // wrapper turns this into a counted scrape error and omits the series, so the
            // gap is visible instead of misleading.
            throw new IllegalStateException("RedisBungee call failed: " + method.getName(), e);
        }
    }

    /** Reflective handles, resolved together so they are cached or absent as a unit. */
    private record Handles(Method accessor, Method playerCount, Method allProxies) {
    }
}

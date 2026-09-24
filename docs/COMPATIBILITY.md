# Compatibility

One jar runs on every currently supported proxy. This is the reasoning behind that claim,
and how to re-check it when a new proxy version ships.

## Runtime support

| Proxy | Version | Minimum JVM | Supported |
| --- | --- | --- | --- |
| Velocity | 3.4.x | 17 | yes |
| Velocity | 3.5.x | 21 | yes |
| Velocity | 4.x | 25 | yes |
| BungeeCord | 1.21-R0.x | 8 | yes |
| BungeeCord | 26.1 | 17 | yes |
| Waterfall | 1.21 and earlier | 8 | yes (archived upstream) |

The plugin itself requires **Java 17 or newer**.

## Why Java 17 and not something newer

The jar is compiled with `--release 17`. That is the highest target that every supported
proxy can load:

```
BungeeCord 26.1  class file 61  -> Java 17
Velocity   3.4.0 class file 61  -> Java 17
Velocity   3.5.1 class file 65  -> Java 21
Velocity   4.2.0 class file 69  -> Java 25
```

Java 17 bytecode runs unchanged on a Java 21 or Java 25 JVM, so targeting 17 covers the
whole table. Targeting anything higher would produce `UnsupportedClassVersionError` on
BungeeCord, which is still on 17 as of 26.1.

`ProxyExporterIT.jarIsPortable` walks every class in the shipped jar and fails the build if
any of them is newer than class file 61, so this cannot regress silently.

## Why the APIs are pinned to older releases

| Dependency | Compiled against | Newest available |
| --- | --- | --- |
| `com.velocitypowered:velocity-api` | 3.4.0 | 4.2.0 |
| `net.md-5:bungeecord-api` | 1.21-R0.4 | 26.1-R0.1-SNAPSHOT |

Compiling against the *oldest* API that contains every symbol used produces a jar that
loads on old and new proxies alike. Compiling against the newest would bind to methods that
do not exist on a 3.4 proxy and fail with `NoSuchMethodError` at runtime.

This is only safe if the newer APIs are additive over the pinned ones for everything the
plugin touches. That was verified by diffing method signatures class by class:

```sh
javap -cp velocity-api-3.4.0 com.velocitypowered.api.proxy.ProxyServer > old.txt
javap -cp velocity-api-4.2.0 com.velocitypowered.api.proxy.ProxyServer > new.txt
diff old.txt new.txt
```

Across `ProxyServer`, `Player`, `RegisteredServer`, `ServerInfo`, `ServerPing`,
`PluginManager`, `PluginContainer`, `PluginDescription`, `Scheduler` and every event class
used, Velocity 4.2.0 differs from 3.4.0 only by **additions**:

- `Player` gained `applySkinToPlayerHeadContents` and a new super-interface
- `LoginEvent` gained a constructor overload and `getServerIdHash()`
- `PluginDescription` gained a default `getProvidedIds()`
- `PlayerClientLoadedWorldEvent` is new

None of those are used here. The BungeeCord surface used is **byte-identical** between
1.21-R0.4 and 26.1-R0.1.

## Re-checking after a proxy release

Run the same diff for the classes the exporter uses:

```sh
# Velocity
curl -sLO https://repo.papermc.io/repository/maven-public/com/velocitypowered/velocity-api/<new>/velocity-api-<new>.jar
unzip -q velocity-api-<new>.jar -d new && unzip -q velocity-api-3.4.0.jar -d old
for c in ProxyServer Player; do
  diff <(javap -cp old com.velocitypowered.api.proxy.$c) \
       <(javap -cp new com.velocitypowered.api.proxy.$c)
done
```

If the diff is additions only, nothing needs to change. If something used here was
*removed* or *changed*, the pinned version has to move forward and the minimum supported
proxy version rises with it.

The end-to-end suite also boots real Velocity, BungeeCord and Waterfall containers against
the built jar, so a genuine incompatibility fails CI rather than a user's proxy.

## RedisBungee

RedisBungee (and the ValioBungee fork) is bound **reflectively**, not at compile time, for
three reasons:

- it is only published on JitPack, so compiling against it would make every build of this
  plugin depend on a third-party build service;
- the API has moved between forks and coordinates over the years, and reflection binds to
  whichever one is actually installed;
- `RedisBungeeAPI.getRedisBungeeApi()` has the same signature on the BungeeCord *and*
  Velocity builds, so one implementation covers both — the 3.x exporter could only do this
  on BungeeCord.

If RedisBungee is absent or fails to initialise, the network metrics are simply not
registered and everything else continues to work.

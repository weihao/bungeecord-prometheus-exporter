package org.akadia.prometheus.core.support;

import org.akadia.prometheus.core.platform.PlatformLogger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Captures log output so tests can assert on operator-facing messages. */
public final class RecordingLogger implements PlatformLogger {

    private final List<String> info = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();

    @Override
    public void info(String message) {
        info.add(message);
    }

    @Override
    public void warn(String message) {
        warnings.add(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        errors.add(message + (throwable == null ? "" : " | " + throwable));
    }

    public List<String> infos() {
        return List.copyOf(info);
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public boolean warnedAbout(String fragment) {
        return warnings.stream().anyMatch(message -> message.contains(fragment));
    }

    public boolean erroredAbout(String fragment) {
        return errors.stream().anyMatch(message -> message.contains(fragment));
    }

    public void clear() {
        info.clear();
        warnings.clear();
        errors.clear();
    }
}

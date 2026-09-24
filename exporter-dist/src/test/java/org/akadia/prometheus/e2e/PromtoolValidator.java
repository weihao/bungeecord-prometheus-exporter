package org.akadia.prometheus.e2e;

import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates an exposition body with the real {@code promtool} from the Prometheus image.
 *
 * <p>The in-process naming test encodes the conventions as this project understands them.
 * This runs the actual tool the Prometheus maintainers ship, so the output is checked
 * against the upstream parser rather than against our reading of the spec - including the
 * lint rules for counter suffixes, unit suffixes and metric-name casing.
 */
final class PromtoolValidator implements AutoCloseable {

    private final GenericContainer<?> prometheus;

    PromtoolValidator() {
        prometheus = new GenericContainer<>(DockerImageName.parse("prom/prometheus:v3.7.3"))
                // The image's entrypoint starts a Prometheus server; this only needs the
                // promtool binary, so keep the container alive doing nothing instead.
                .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
                .withCommand("-c", "sleep 600");
        prometheus.start();
    }

    /** Result of a promtool invocation. */
    record Result(int exitCode, String output) {
        boolean ok() {
            return exitCode == 0;
        }
    }

    /**
     * Runs {@code promtool check metrics} over {@code exposition}.
     *
     * <p>Parse errors are failures; lint findings are reported on stdout and also make the
     * tool exit non-zero.
     */
    Result checkMetrics(String exposition) throws IOException, InterruptedException {
        Path local = Files.createTempFile("metrics", ".prom");
        Files.writeString(local, exposition, StandardCharsets.UTF_8);
        prometheus.copyFileToContainer(MountableFile.forHostPath(local), "/tmp/metrics.prom");

        Container.ExecResult result = prometheus.execInContainer(
                "/bin/sh", "-c", "promtool check metrics < /tmp/metrics.prom");
        Files.deleteIfExists(local);
        return new Result(result.getExitCode(),
                (result.getStdout() + result.getStderr()).trim());
    }

    @Override
    public void close() {
        prometheus.stop();
    }
}

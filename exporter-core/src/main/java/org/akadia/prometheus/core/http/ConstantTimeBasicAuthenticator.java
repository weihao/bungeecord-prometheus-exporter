package org.akadia.prometheus.core.http;

import com.sun.net.httpserver.BasicAuthenticator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * HTTP basic auth for the metrics endpoint.
 *
 * <p>Credentials are compared with {@link MessageDigest#isEqual}, which does not return
 * early on the first differing byte. A naive {@code String.equals} leaks the shared secret
 * one character at a time to anyone who can measure response times, and a metrics endpoint
 * is exactly the kind of thing left reachable from a wider network than intended.
 *
 * <p>Both credentials are compared unconditionally so that a wrong username and a wrong
 * password take the same time.
 */
final class ConstantTimeBasicAuthenticator extends BasicAuthenticator {

    private final byte[] expectedUsername;
    private final byte[] expectedPassword;

    ConstantTimeBasicAuthenticator(String realm, String username, String password) {
        super(realm);
        this.expectedUsername = username.getBytes(StandardCharsets.UTF_8);
        this.expectedPassword = password.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        boolean userMatches = MessageDigest.isEqual(
                expectedUsername, username.getBytes(StandardCharsets.UTF_8));
        boolean passwordMatches = MessageDigest.isEqual(
                expectedPassword, password.getBytes(StandardCharsets.UTF_8));
        return userMatches & passwordMatches;
    }
}

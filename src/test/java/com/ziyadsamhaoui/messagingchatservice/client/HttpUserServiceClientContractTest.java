package com.ziyadsamhaoui.messagingchatservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ziyadsamhaoui.messagingchatservice.client.dto.UserProfileResponse;
import com.ziyadsamhaoui.messagingchatservice.config.UserServiceProperties;
import com.ziyadsamhaoui.messagingchatservice.exception.DependencyUnavailableException;
import com.ziyadsamhaoui.messagingchatservice.exception.UserNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/**
 * Contract test for the Chat → User internal API (INC-01).
 *
 * <p>Chat's suites mock {@link UserServiceClient}, so a path or header drift stayed
 * invisible until a live direct-room creation failed. This test boots a real HTTP
 * endpoint and exercises the real {@link HttpUserServiceClient}, pinning the exact
 * request (path + {@code X-Internal-Token}) and the {@code 404}/{@code 5xx} mapping.
 */
class HttpUserServiceClientContractTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final String PROFILE_ID = "033fce2c-1517-4704-aa91-203a90b509c2";

    private HttpServer server;
    private String baseUrl;
    private final List<String> requestedPaths = new ArrayList<>();
    private final AtomicReference<String> internalTokenHeader = new AtomicReference<>();
    private final AtomicReference<Integer> forcedStatus = new AtomicReference<>();
    private final AtomicReference<String> profileBody = new AtomicReference<>(
            "{\"id\":\"" + PROFILE_ID + "\",\"username\":\"omar\"}");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/users", exchange -> {
            record(exchange);
            Integer status = forcedStatus.get();
            if (status != null) {
                respond(exchange, status, "{\"message\":\"forced\"}");
                return;
            }
            respond(exchange, HttpStatus.OK.value(), profileBody.get());
        });
        server.createContext("/internal/blocks/check", exchange -> {
            record(exchange);
            respond(exchange, HttpStatus.OK.value(), "{\"blocked\":true}");
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void getProfileCallsTheDocumentedInternalPathWithTheInternalToken() {
        UserProfileResponse profile = client().getProfile(PROFILE_ID);

        assertThat(profile.id()).isEqualTo(PROFILE_ID);
        assertThat(profile.username()).isEqualTo("omar");
        // The exact path Chat is contracted to call (docs/API_ENDPOINTS.md, Chat → User).
        assertThat(requestedPaths).containsExactly("/internal/users/" + PROFILE_ID);
        assertThat(internalTokenHeader.get()).isEqualTo(INTERNAL_TOKEN);
    }

    @Test
    void getProfileMapsAMissingProfileToUserNotFound() {
        forcedStatus.set(HttpStatus.NOT_FOUND.value());

        assertThatThrownBy(() -> client().getProfile(PROFILE_ID))
                .isInstanceOf(UserNotFoundException.class)
                .extracting(exception -> ((UserNotFoundException) exception).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getProfileIsUnavailableWhenTheInternalRouteIsNotServed() {
        // The historical failure: the User service had no GET /internal/users/{id}, so the
        // request fell through to the static-resource handler and answered 500. Chat must
        // report a dependency failure — never a missing profile and never a success.
        forcedStatus.set(HttpStatus.INTERNAL_SERVER_ERROR.value());

        assertThatThrownBy(() -> client().getProfile(PROFILE_ID))
                .isInstanceOf(DependencyUnavailableException.class)
                .extracting(exception -> ((DependencyUnavailableException) exception).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void getProfileIsUnavailableWhenTheUserServiceIsUnreachable() {
        UserServiceProperties offline = new UserServiceProperties("http://127.0.0.1:1", "/internal/users/{userId}",
                "/internal/blocks/check", "X-Internal-Token", INTERNAL_TOKEN, Duration.ofMillis(200),
                Duration.ofMillis(200));

        assertThatThrownBy(() -> configuredClient(offline).getProfile(PROFILE_ID))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void getProfileRejectsAnIncompleteProfile() {
        profileBody.set("{\"id\":\"" + PROFILE_ID + "\"}");

        assertThatThrownBy(() -> client().getProfile(PROFILE_ID))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void isBlockedBetweenCallsTheDocumentedBlockCheckPath() {
        assertThat(client().isBlockedBetween("user-a", "user-b")).isTrue();
        assertThat(requestedPaths).containsExactly("/internal/blocks/check?a=user-a&b=user-b");
    }

    private HttpUserServiceClient client() {
        return configuredClient(new UserServiceProperties(baseUrl, "/internal/users/{userId}",
                "/internal/blocks/check", "X-Internal-Token", INTERNAL_TOKEN, Duration.ofSeconds(2),
                Duration.ofSeconds(3)));
    }

    private HttpUserServiceClient configuredClient(UserServiceProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl())
                .defaultHeader(properties.internalTokenHeader(), properties.internalToken());
        return new HttpUserServiceClient(builder.build(), properties);
    }

    private void record(HttpExchange exchange) {
        requestedPaths.add(exchange.getRequestURI().toString());
        internalTokenHeader.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}

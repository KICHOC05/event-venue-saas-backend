package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "app.cors.allowed-origins=http://localhost:5173,https://staff.spacekidshgo.site")
@ActiveProfiles("test")
class WebSocketOriginIntegrationTest {

    private static final String STAFF_ORIGIN = "https://staff.spacekidshgo.site";

    @LocalServerPort
    private int port;

    @Autowired
    private CorsProperties corsProperties;

    @Test
    void acceptsNativeWebSocketHandshakeFromConfiguredStaffOrigin() throws Exception {
        assertThat(corsProperties.allowedOrigins()).contains(STAFF_ORIGIN);

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(STAFF_ORIGIN);

        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(
                        new TextWebSocketHandler(), headers, wsUri())
                .get(10, TimeUnit.SECONDS);

        try {
            assertThat(session.isOpen()).isTrue();
        } finally {
            session.close();
        }
    }

    @Test
    void rejectsNativeWebSocketHandshakeFromUnauthorizedOrigin() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("https://attacker.example");

        StandardWebSocketClient client = new StandardWebSocketClient();

        assertThatThrownBy(() -> client.execute(
                        new TextWebSocketHandler(), headers, wsUri())
                .get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    private URI wsUri() {
        return URI.create("ws://localhost:" + port + "/ws");
    }
}

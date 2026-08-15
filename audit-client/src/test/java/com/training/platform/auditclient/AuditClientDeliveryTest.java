package com.training.platform.auditclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditClientDeliveryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void retriesServerFailureWithTheSameAuditId() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        List<String> paths = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<JsonNode> bodies = new ArrayList<>();
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            keys.add(exchange.getRequestHeaders().getFirst(AuditClient.INTERNAL_KEY_HEADER));
            bodies.add(objectMapper.readTree(exchange.getRequestBody()));
            int status = requests.incrementAndGet() == 1 ? 500 : 201;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();

        try {
            AuditClient client = new AuditClient(
                    "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + server.getAddress().getPort(),
                    "test-audit-key");

            client.success("transactions", "TRANSACTION_COMPLETED", "Transaction completed",
                    Map.of("transactionId", "txn-1", "transactionRef", "ref-1"));

            assertThat(requests.get()).isEqualTo(2);
            assertThat(paths).containsOnly("/api/audit/transactions");
            assertThat(keys).containsOnly("test-audit-key");
            assertThat(bodies.get(0).get("auditId").asText()).isNotBlank();
            assertThat(bodies.get(1).get("auditId").asText())
                    .isEqualTo(bodies.get(0).get("auditId").asText());
            assertThat(bodies.get(1).get("transactionId").asText()).isEqualTo("txn-1");
        } finally {
            server.stop(0);
        }
    }
}

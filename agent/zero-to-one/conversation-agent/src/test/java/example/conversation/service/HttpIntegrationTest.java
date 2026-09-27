package example.conversation.service;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 HTTP 入口 + 真实 OpenAI 协议适配器 + 本机模拟服务，绝不连接 DeepSeek。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.openai.api-key=offline-test-key")
class HttpIntegrationTest {
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final HttpServer MOCK = mockServer();
    @LocalServerPort int port;

    static HttpServer mockServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/completions", exchange -> {
                REQUESTS.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                int round = CALLS.incrementAndGet();
                String message = round % 2 == 1
                        ? "\"content\":\"\",\"tool_calls\":[{\"id\":\"wire-call\",\"type\":\"function\",\"function\":{\"name\":\"readDoc\",\"arguments\":\"{\\\"docId\\\":\\\"pagination-v2\\\"}\"}}]"
                        : "\"content\":\"pageSize 最大 100；默认值未规定。来源 pagination-v2。\"";
                String json = "{\"id\":\"offline\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"deepseek-flash\","
                        + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\"," + message
                        + "},\"finish_reason\":\"" + (round % 2 == 1 ? "tool_calls" : "stop")
                        + "\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var stream = exchange.getResponseBody()) { stream.write(body); }
            });
            server.start();
            return server;
        } catch (IOException ex) { throw new ExceptionInInitializerError(ex); }
    }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + MOCK.getAddress().getPort());
    }
    @AfterAll static void stop() { MOCK.stop(0); }

    HttpResponse<String> post(String path, String question) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"" + question + "\"}")).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test void endpointMemoryValidationAndWireFormat() throws Exception {
        var first = post("/api/chat/sessions/wire-a", "WIRE_MARKER_A 的分页规则");
        assertEquals(200, first.statusCode(), first.body());
        assertTrue(first.body().contains("COMPLETED"), first.body());
        assertTrue(first.body().contains("readDoc 返回"), first.body());
        assertEquals(2, REQUESTS.size());
        assertTrue(REQUESTS.get(0).contains("\"model\":\"deepseek-flash\""));
        assertTrue(REQUESTS.get(0).contains("\"thinking\":{\"type\":\"disabled\"}"));
        assertTrue(REQUESTS.get(1).contains("\"role\":\"tool\""));
        assertTrue(REQUESTS.get(1).contains("pagination-v2"));
        assertTrue(post("/api/chat/sessions/wire-a", "那默认值呢？").body().contains("COMPLETED"));
        assertTrue(REQUESTS.get(2).contains("WIRE_MARKER_A"));
        assertFalse(REQUESTS.get(2).contains("\"role\":\"tool\""));
        assertTrue(post("/api/chat/sessions/wire-b", "新的独立问题").body().contains("COMPLETED"));
        assertFalse(REQUESTS.get(4).contains("WIRE_MARKER_A"));
        assertTrue(post("/api/chat/stateless", "没有历史的问题").body().contains("COMPLETED"));
        assertFalse(REQUESTS.get(6).contains("WIRE_MARKER_A"));
        int count = REQUESTS.size();
        assertEquals(400, post("/api/chat/sessions/wire-a", "").statusCode());
        assertEquals(400, post("/api/chat/sessions/invalid.id", "问题").statusCode());
        assertEquals(count, REQUESTS.size());
        var clear = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/chat/sessions/wire-a"))
                .DELETE().build();
        assertTrue(HttpClient.newHttpClient().send(clear, HttpResponse.BodyHandlers.ofString()).body().contains("CLEARED"));
        assertTrue(post("/api/chat/sessions/wire-a", "清空后的新问题").body().contains("COMPLETED"));
        assertFalse(REQUESTS.get(count).contains("WIRE_MARKER_A"));
    }
}

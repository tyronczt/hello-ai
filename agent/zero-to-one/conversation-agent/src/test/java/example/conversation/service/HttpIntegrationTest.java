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
    /** 本机模拟模型收到的请求 JSON，按顺序核对工具回填与会话标记。 */
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    /** 模拟模型的请求次数；奇数次请求工具，偶数次返回固定候选答案。 */
    private static final AtomicInteger CALLS = new AtomicInteger();
    /** 绑定本机随机端口的模拟模型 HTTP 服务，测试结束后显式停止。 */
    private static final HttpServer MOCK = mockServer();
    /** Spring Boot 为被测教学服务分配的本机随机 HTTP 端口。 */
    @LocalServerPort int port;

    /**
     * 启动本机模拟模型端点，记录请求并交替返回工具请求与最终回复。
     * 返回数据只是预设协议响应，不验证真实模型回答质量。
     *
     * @return 已启动且监听 127.0.0.1 随机端口的模拟 HTTP 服务
     * @throws ExceptionInInitializerError 本机服务创建失败时包装 IOException 抛出
     */
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

    /**
     * 将被测应用的模型 base-url 指向本机模拟服务，避免访问真实模型地址。
     *
     * @param registry Spring 测试上下文的动态配置注册器
     */
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + MOCK.getAddress().getPort());
    }
    /** 在全部检查结束后立即停止模拟模型服务，不保留本机监听端口。 */
    @AfterAll static void stop() { MOCK.stop(0); }

    /**
     * 向被测本机教学服务发送一个同步 JSON 问题，请求等待上限为 15 秒。
     * 问题直接拼入 JSON，仅适用于本测试中不含引号或转义字符的固定输入。
     *
     * @param path 被测服务的绝对路径，如 /api/chat/sessions/wire-a
     * @param question 本测试的固定问题文本，允许空字符串以验证校验失败
     * @return 真实 HTTP 响应，正文保持字符串供断言检查
     * @throws Exception HTTP 构建或发送失败，或等待响应被中断时传播
     */
    HttpResponse<String> post(String path, String question) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"" + question + "\"}")).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 经过真实 HTTP 入口与模型协议适配器，检查工具回填、会话隔离、清空及参数 400。
     * 模型服务为本机脚本响应，此检查不覆盖真实模型、登录鉴权或持久化恢复。
     *
     * @throws Exception HTTP 请求失败或等待响应被中断时传播
     */
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

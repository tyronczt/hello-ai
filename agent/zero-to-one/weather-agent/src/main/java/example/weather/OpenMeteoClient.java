package example.weather;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Open-Meteo 公开只读客户端；固定域名、有限超时，不自动重试或跟随重定向。 */
final class OpenMeteoClient {
    /** 允许访问的服务域名，模型不能提供 URL。 */
    private static final Set<String> HOSTS = Set.of("geocoding-api.open-meteo.com", "api.open-meteo.com");
    /** 仅在本次工具实例内复用连接。 */
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    /** 沿用 Spring AI 工程已有的 Jackson 版本。 */
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 获取一个公开 JSON 响应，不把服务端错误正文写入返回或日志。
     * @param uri 由应用构造的 HTTPS 地址
     * @return 实际服务响应对象
     */
    JsonNode get(URI uri) {
        if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1) {
            throw new Failure("INVALID_ENDPOINT");
        }
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429) throw new Failure("RATE_LIMITED");
            if (response.statusCode() != 200) throw new Failure("PROVIDER_HTTP_ERROR");
            // 响应接收完成后校验大小；这不是下载过程中的流式大小限制。
            if (response.body().length() > 1_000_000) throw new Failure("INVALID_RESPONSE");
            JsonNode body = json.readTree(response.body());
            if (body == null || !body.isObject() || body.path("error").asBoolean(false)) {
                throw new Failure("INVALID_RESPONSE");
            }
            return body;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Failure("INTERRUPTED");
        } catch (IOException ex) {
            throw new Failure("PROVIDER_UNAVAILABLE");
        } catch (Failure ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new Failure("INVALID_RESPONSE");
        }
    }

    /** 只携带应用定义的错误码，不包含 URL、凭据或远端错误正文。 */
    static final class Failure extends RuntimeException {
        /** Java 异常序列化版本。 */
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        /** @param code 应用预定义的错误码 */
        Failure(String code) {
            super(code);
        }
    }
}

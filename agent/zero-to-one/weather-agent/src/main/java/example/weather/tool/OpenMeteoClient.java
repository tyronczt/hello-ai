package example.weather.tool;

import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Open-Meteo 公开只读客户端；固定域名、有限超时，不自动重试或跟随重定向。 */
final class OpenMeteoClient {
    /** 记录固定服务域名、HTTP 状态、错误码及耗时，不记录 URL 查询参数、远端正文或凭据。 */
    private static final Logger LOG = LoggerFactory.getLogger(OpenMeteoClient.class);

    /** 允许访问的服务域名，模型不能提供 URL。 */
    private static final Set<String> HOSTS = Set.of("geocoding-api.open-meteo.com", "api.open-meteo.com");

    /** 在本客户端内复用连接；连接超时为 5 秒，禁止自动跟随重定向。 */
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** 使用工程已有的 Jackson 解析公开服务返回的 JSON。 */
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * 获取一个公开 JSON 响应，不把服务端错误正文写入返回或日志。
     * 单次请求超时为 15 秒，不自动重试；发生线程中断时恢复中断标记后报告失败。
     *
     * @param uri 由应用构造的非 null HTTPS 地址，使用允许的域名且不带用户信息或显式端口
     * @return 实际服务返回的 JSON 对象，具体天气字段由工具层继续校验
     * @throws Failure 地址不符合限制，HTTP 或网络请求失败，线程被中断，或响应无效
     */
    JsonNode get(URI uri) {
        if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1) {
            LOG.warn("天气接口地址被拒绝：stage=provider_rejected, code=INVALID_ENDPOINT");
            throw new Failure("INVALID_ENDPOINT");
        }
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").GET().build();
        long started = System.nanoTime();
        int httpStatus = 0;
        LOG.info("天气接口请求开始：stage=provider_start, host={}, requestTimeoutMs={}",
                uri.getHost(), request.timeout().orElseThrow().toMillis());
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            httpStatus = response.statusCode();
            if (response.statusCode() == 429) {
                throw new Failure("RATE_LIMITED");
            }
            if (response.statusCode() != 200) {
                throw new Failure("PROVIDER_HTTP_ERROR");
            }
            // 响应接收完成后校验大小；这不是下载过程中的流式大小限制。
            if (response.body().length() > 1_000_000) {
                throw new Failure("INVALID_RESPONSE");
            }
            JsonNode body = json.readTree(response.body());
            if (body == null || !body.isObject() || body.path("error").asBoolean(false)) {
                throw new Failure("INVALID_RESPONSE");
            }
            LOG.info("天气接口请求完成：stage=provider_end, host={}, httpStatus={}, status=OK, elapsedMs={}",
                    uri.getHost(), httpStatus, (System.nanoTime() - started) / 1_000_000);
            LOG.debug("天气接口响应长度：stage=provider_size, host={}, bodyUtf16Units={}",
                    uri.getHost(), response.body().length());
            return body;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("天气接口请求被中断：stage=provider_end, host={}, code=INTERRUPTED, elapsedMs={}",
                    uri.getHost(), (System.nanoTime() - started) / 1_000_000);
            throw new Failure("INTERRUPTED");
        } catch (IOException ex) {
            LOG.warn("天气接口网络请求失败：stage=provider_end, code=PROVIDER_UNAVAILABLE, host={}, exceptionType={}, elapsedMs={}",
                    uri.getHost(), ex.getClass().getSimpleName(), (System.nanoTime() - started) / 1_000_000);
            throw new Failure("PROVIDER_UNAVAILABLE");
        } catch (Failure ex) {
            LOG.warn("天气接口响应被拒绝：stage=provider_end, host={}, httpStatus={}, code={}, elapsedMs={}",
                    uri.getHost(), httpStatus, ex.getMessage(), (System.nanoTime() - started) / 1_000_000);
            throw ex;
        } catch (RuntimeException ex) {
            LOG.warn("天气接口响应解析失败：stage=provider_end, host={}, httpStatus={}, code=INVALID_RESPONSE,"
                            + " exceptionType={}, elapsedMs={}",
                    uri.getHost(), httpStatus, ex.getClass().getName(), (System.nanoTime() - started) / 1_000_000);
            throw new Failure("INVALID_RESPONSE");
        }
    }

    /** 将应用定义的错误码作为异常消息，不携带 URL、凭据或远端错误正文。 */
    static final class Failure extends RuntimeException {
        /** Java 异常序列化版本。 */
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 创建供天气工具转换为失败结果的异常。
         *
         * @param code 调用方选择的应用预定义错误码，本构造器不校验取值
         */
        Failure(String code) {
            super(code);
        }
    }
}

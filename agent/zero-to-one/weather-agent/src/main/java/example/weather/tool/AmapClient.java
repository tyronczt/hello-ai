package example.weather.tool;

import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
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

/**
 * 高德行政区与天气预报的只读客户端，凭据仅在实际 HTTP 请求中添加。
 * 限定服务地址、连接与请求超时，不自动重试或跟随重定向。
 */
final class AmapClient {
    /** 记录固定服务域名、状态、错误类型及耗时，不记录凭据、URL 参数或远端正文。 */
    private static final Logger LOG = LoggerFactory.getLogger(AmapClient.class);

    /** 唯一允许访问的服务域名；模型不能提供请求地址。 */
    private static final String HOST = "restapi.amap.com";

    /** 本示例需要的两个只读接口，不开放其他服务路径。 */
    private static final Set<String> PATHS = Set.of("/v3/config/district", "/v3/weather/weatherInfo");

    /** 仅供实际 HTTP 请求使用的 Web 服务 Key，不进入工具参数、返回、报告或日志。 */
    private final String apiKey;

    /** 在本客户端内复用连接，连接预算为 20 秒，禁止自动跟随重定向。 */
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** 使用工程已有的 Jackson 解析服务返回，不回传服务端错误正文。 */
    private final JsonMapper json = JsonMapper.builder().build();

    /** 从 JVM 启动参数 -Damap.api.key 读取凭据，构造阶段不发送网络请求。 */
    AmapClient() {
        this(System.getProperty("amap.api.key"));
    }

    /**
     * 注入 Web 服务 Key，供包内协议检查使用；构造阶段不发送网络请求。
     *
     * @param key Web 服务 Key；null 或去除首尾空白后为空时，在请求阶段报告 MISSING_API_KEY
     */
    AmapClient(String key) {
        this.apiKey = key == null ? "" : key.strip();
    }

    /**
     * 获取高德 JSON 响应，在客户端内部追加 Key 并校验 HTTP 与业务状态。
     * 单次请求预算为 30 秒，连接预算为 20 秒；中断后恢复标记，不自动重试。
     *
     * @param uri 应用构造的无 Key HTTPS 地址，限允许的域名与路径，不带用户信息、端口或片段
     * @return 业务状态成功的 JSON 对象，具体行政区和天气字段由工具层继续校验
     * @throws Failure 凭据缺失、地址无效、服务拒绝、网络失败、中断或响应无效
     */
    JsonNode get(URI uri) {
        validateEndpoint(uri);
        if (apiKey.isEmpty()) {
            LOG.warn("天气接口缺少凭据：stage=provider_rejected, code=MISSING_API_KEY,"
                    + " requiredJvmOption=-Damap.api.key");
            throw new Failure("MISSING_API_KEY");
        }
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        URI requestUri = URI.create(uri + (uri.getRawQuery() == null ? "?" : "&") + "key=" + encodedKey);
        var request = HttpRequest.newBuilder(requestUri).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").GET().build();
        long started = System.nanoTime();
        int httpStatus = 0;
        LOG.info("天气接口请求开始：stage=provider_start, host={}, requestTimeoutMs=30000", HOST);
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            httpStatus = response.statusCode();
            if (httpStatus == 429) {
                throw new Failure("RATE_LIMITED");
            }
            if (httpStatus != 200) {
                throw new Failure("PROVIDER_HTTP_ERROR");
            }
            // 接收完成后才检查大小，不代表下载过程具有流式大小限制。
            if (response.body().length() > 1_000_000) {
                throw new Failure("INVALID_RESPONSE");
            }
            JsonNode body = json.readTree(response.body());
            validateResponse(body);
            // 服务若意外回显凭据，拒绝整个响应，避免凭据进入后续工具结果。
            if (response.body().contains(apiKey) || response.body().contains(encodedKey)
                    || body.toString().contains(apiKey)) {
                throw new Failure("INVALID_RESPONSE");
            }
            LOG.info("天气接口请求完成：stage=provider_end, host={}, httpStatus={}, status=OK, elapsedMs={}",
                    HOST, httpStatus, (System.nanoTime() - started) / 1_000_000);
            LOG.debug("天气接口响应长度：stage=provider_size, host={}, bodyUtf16Units={}",
                    HOST, response.body().length());
            return body;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("天气接口请求被中断：stage=provider_end, host={}, code=INTERRUPTED, elapsedMs={}",
                    HOST, (System.nanoTime() - started) / 1_000_000);
            throw new Failure("INTERRUPTED");
        } catch (IOException ex) {
            LOG.warn("天气接口网络请求失败：stage=provider_end, code=PROVIDER_UNAVAILABLE, host={},"
                            + " exceptionType={}, elapsedMs={}",
                    HOST, ex.getClass().getSimpleName(), (System.nanoTime() - started) / 1_000_000);
            throw new Failure("PROVIDER_UNAVAILABLE");
        } catch (Failure ex) {
            LOG.warn("天气接口响应被拒绝：stage=provider_end, host={}, httpStatus={}, code={}, elapsedMs={}",
                    HOST, httpStatus, ex.getMessage(), (System.nanoTime() - started) / 1_000_000);
            throw ex;
        } catch (RuntimeException ex) {
            LOG.warn("天气接口响应解析失败：stage=provider_end, host={}, httpStatus={}, code=INVALID_RESPONSE,"
                            + " exceptionType={}, elapsedMs={}",
                    HOST, httpStatus, ex.getClass().getName(), (System.nanoTime() - started) / 1_000_000);
            throw new Failure("INVALID_RESPONSE");
        }
    }

    /**
     * 检查高德统一业务状态，供真实 HTTP 响应和工具层注入的响应共同使用。
     *
     * @param body 服务返回的 JSON 对象
     * @throws Failure 响应结构无效、Key 无效或服务拒绝请求
     */
    static void validateResponse(JsonNode body) {
        if (body == null || !body.isObject() || !body.path("status").isString()) {
            throw new Failure("INVALID_RESPONSE");
        }
        String status = body.path("status").asString();
        if ("0".equals(status)) {
            throw new Failure("10001".equals(body.path("infocode").asString(""))
                    ? "AUTH_ERROR" : "PROVIDER_REJECTED");
        }
        if (!"1".equals(status)) {
            throw new Failure("INVALID_RESPONSE");
        }
    }

    /**
     * 拒绝未经允许的服务地址及调用方提供的 Key，确保凭据只由客户端追加。
     *
     * @param uri 应用构造的服务地址
     * @throws Failure 地址、路径、查询编码或 Key 参数不符合限制
     */
    private void validateEndpoint(URI uri) {
        boolean valid = uri != null && "https".equals(uri.getScheme()) && HOST.equals(uri.getHost())
                && PATHS.contains(uri.getRawPath()) && uri.getUserInfo() == null && uri.getPort() == -1
                && uri.getRawFragment() == null;
        if (valid && uri.getRawQuery() != null) {
            try {
                for (String parameter : uri.getRawQuery().split("&")) {
                    String name = parameter.split("=", 2)[0];
                    if ("key".equalsIgnoreCase(URLDecoder.decode(name, StandardCharsets.UTF_8))) {
                        valid = false;
                        break;
                    }
                }
            } catch (IllegalArgumentException ex) {
                valid = false;
            }
        }
        if (!valid) {
            LOG.warn("天气接口地址被拒绝：stage=provider_rejected, code=INVALID_ENDPOINT");
            throw new Failure("INVALID_ENDPOINT");
        }
    }

    /** 只携带应用预定义错误码，不保存 URL、凭据、远端正文或原始异常。 */
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

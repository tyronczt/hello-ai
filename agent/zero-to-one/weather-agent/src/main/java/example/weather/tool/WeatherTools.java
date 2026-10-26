package example.weather.tool;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 每项任务独立的高德天气工具，负责行政区搜索、地点登记和预报结果校验。
 * 地点 ID 必须来自本任务的搜索结果；不查询实况，也不以模拟天气填补缺失数据。
 */
public final class WeatherTools {
    /** 记录固定工具名、参数摘要、结果码与耗时，不记录 Key 或原始远端响应。 */
    private static final Logger LOG = LoggerFactory.getLogger(WeatherTools.class);

    /** 本适配层为中国行政区和预报明确采用的业务时区。 */
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Shanghai");

    /** 本示例支持登记的行政区级别，不登记国家、街道及其他级别。 */
    private static final Set<String> LEVELS = Set.of("province", "city", "district");

    /** 高德预报发布时间的格式，按真实日历严格校验。 */
    private static final DateTimeFormatter REPORT_TIME_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    /** 接收无 Key 地址的 JSON 读取函数；正式客户端在内部追加凭据。 */
    private final Function<URI, JsonNode> fetch;

    /** 记录应用获取时间与中国当地查询时间，不依赖服务器默认时区。 */
    private final Clock clock;

    /** 本次任务检索到的候选地点，ID 为六位行政区划编码的 long 值，不跨任务共享。 */
    private final Map<Long, Map<String, Object>> locations = new LinkedHashMap<>();

    /** 工具实际输入和规范化结果的教学记录，不含请求 Header 或 Key。 */
    private final List<Map<String, Object>> events = new ArrayList<>();

    /** 工具返回使用 JSON 编码，不以字符串拼接外部内容。 */
    private final JsonMapper json = JsonMapper.builder().build();

    /** 创建访问高德真实接口的工具实例；构造阶段不发送请求。 */
    public WeatherTools() {
        this(new AmapClient()::get);
    }

    /**
     * 注入 JSON 读取函数，供包内协议检查使用；正式入口使用高德客户端。
     *
     * @param fetch 接收应用构造的无 Key URI 并返回 JSON 对象的非空函数
     * @throws NullPointerException 读取函数为空
     */
    WeatherTools(Function<URI, JsonNode> fetch) {
        this(fetch, Clock.systemUTC());
    }

    /**
     * 注入读取函数和查询时钟，供包内协议及跨时区日期检查使用。
     *
     * @param fetch 接收应用构造的无 Key URI 并返回 JSON 对象的非空函数
     * @param clock 提供应用查询时刻的非空时钟，输出当地时间固定使用 Asia/Shanghai
     * @throws NullPointerException 读取函数或时钟为空
     */
    WeatherTools(Function<URI, JsonNode> fetch, Clock clock) {
        this.fetch = Objects.requireNonNull(fetch);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * 搜索中国省、市或区县候选，保留行政区名称和级别供模型判断同名地点。
     * 全部支持级别的候选校验通过后才登记；不自动选中第一项，不返回坐标。
     *
     * @param city 城市或行政区名称；去除首尾空白后至少 2 个、原文最多 80 个 UTF-16 单元
     * @return 状态为 OK 的候选 JSON，或状态为 ERROR 的错误 JSON
     */
    @Tool(description = "通过高德搜索中国省、市或区县，返回候选 ID、六位行政区划编码、名称、级别和时区。"
            + "不支持国家或街道级查询。同名地点需结合用户提供的地区判断；无法确定时追问，不默认取第一项。")
    public synchronized String searchLocations(
            @ToolParam(description = "城市或行政区名称，例如北京；有同名歧义时补充用户给出的地区") String city) {
        long started = System.nanoTime();
        LOG.info("工具开始：stage=tool_start, tool=searchLocations, cityUtf16Units={}", city == null ? 0 : city.length());
        if (city == null || city.strip().length() < 2 || city.length() > 80) {
            return record("searchLocations", Map.of("city", city == null ? "" : city),
                    error("INVALID_ARGUMENT", "城市名称需要 2～80 个字符。"), started);
        }
        Map<String, Object> args = Map.of("city", city.strip());
        URI uri = URI.create("https://restapi.amap.com/v3/config/district?keywords="
                + URLEncoder.encode(city.strip(), StandardCharsets.UTF_8)
                + "&subdistrict=0&page=1&offset=5&extensions=base&output=JSON");
        try {
            JsonNode body = fetch.apply(uri);
            AmapClient.validateResponse(body);
            List<Map<String, Object>> results = parseLocations(body);
            if (results.isEmpty()) {
                return record("searchLocations", args, error("NOT_FOUND", "没有找到支持的省、市或区县候选。"), started);
            }
            // 全部候选校验后再登记，防止部分无效响应污染本次可用地点。
            for (var location : results) {
                locations.put((Long) location.get("id"), location);
            }
            return record("searchLocations", args, Map.of("status", "OK", "source", "高德地图",
                    "sourceUrl", uri.toString(), "retrievedAt", clock.instant().toString(),
                    "locations", results), started);
        } catch (AmapClient.Failure ex) {
            return record("searchLocations", args,
                    error(ex.getMessage(), failureMessage(ex, "城市服务未返回可用结果；请检查配置或补充地点。")), started);
        }
    }

    /**
     * 读取本任务已登记行政区的完整预报，返回多少个有效日期就保留多少天。
     * 只查询预报，不查询实况；白天与夜间温度不解释为最高与最低温度。
     *
     * @param locationId 本次 searchLocations 返回的精确地点 ID，即六位 adcode 的 long 值
     * @return 状态为 OK 的天气 JSON，或状态为 ERROR 的错误 JSON；缺失温度和降水概率保持 null
     */
    @Tool(description = "查询本次 searchLocations 已返回的地点 ID，取得高德天气预报，不查询实况。"
            + "返回各日期的白天和夜间天气、温度、应用查询当地时间、预报发布时间、单位和来源。"
            + "白天和夜间温度不是最高和最低温度；服务未提供降水概率，该值为 null，不能编造。"
            + "超出实际返回日期范围的信息未知。")
    public synchronized String getWeather(
            @ToolParam(description = "本次城市搜索实际返回的精确 ID；虽与六位 adcode 相同，也不能绕过搜索自行填写")
            long locationId) {
        long started = System.nanoTime();
        LOG.info("工具开始：stage=tool_start, tool=getWeather, locationId={}", locationId);
        Map<String, Object> args = Map.of("locationId", locationId);
        var location = locations.get(locationId);
        if (location == null) {
            return record("getWeather", args, error("UNKNOWN_LOCATION", "先搜索城市，再使用实际返回的 ID。"), started);
        }
        URI uri = URI.create("https://restapi.amap.com/v3/weather/weatherInfo?city=" + location.get("adcode")
                + "&extensions=all&output=JSON");
        try {
            JsonNode body = fetch.apply(uri);
            AmapClient.validateResponse(body);
            JsonNode forecast = findForecast(body, (String) location.get("adcode"));
            List<Map<String, Object>> daily = parseDaily(forecast);
            return record("getWeather", args, createWeatherResult(forecast, daily, location, uri), started);
        } catch (AmapClient.Failure ex) {
            return record("getWeather", args,
                    error(ex.getMessage(), failureMessage(ex, "天气服务未返回可用结果，不能据此生成天气数值。")), started);
        }
    }

    /**
     * 复制本任务的事件列表，供显式运行的教学报告读取。
     *
     * @return 不可修改的列表副本；内部 Map 仍共享，不是深度不可变快照
     */
    public synchronized List<Map<String, Object>> events() {
        return List.copyOf(events);
    }

    /**
     * 解析行政区候选，不在校验过程中修改地点登记表，不保留服务返回的坐标。
     *
     * @param body 已通过业务状态校验的行政区响应
     * @return 按服务顺序排列的支持级别候选；无候选或仅含不支持级别时返回空列表
     * @throws AmapClient.Failure 候选结构、名称、编码格式或编码唯一性不符合要求
     */
    private List<Map<String, Object>> parseLocations(JsonNode body) {
        JsonNode candidates = body.path("districts");
        if (!candidates.isArray()) {
            throw new AmapClient.Failure("INVALID_RESPONSE");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode item : candidates) {
            if (!item.isObject() || !item.path("level").isString()) {
                throw new AmapClient.Failure("INVALID_RESPONSE");
            }
            String level = item.path("level").asString();
            if (!LEVELS.contains(level)) {
                continue;
            }
            String adcode = requiredText(item, "adcode");
            if (!adcode.matches("[1-9][0-9]{5}") || !seen.add(adcode)) {
                throw new AmapClient.Failure("INVALID_RESPONSE");
            }
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("id", Long.parseLong(adcode));
            location.put("adcode", adcode);
            location.put("name", requiredText(item, "name"));
            location.put("level", level);
            location.put("country", "中国");
            location.put("country_code", "CN");
            location.put("timezone", TIMEZONE.getId());
            results.add(location);
        }
        return results;
    }

    /**
     * 找到与本次已登记行政区编码唯一匹配的预报，拒绝错配和重复匹配。
     *
     * @param body 已通过业务状态校验的天气响应
     * @param adcode 本任务登记的六位行政区划编码
     * @return 编码匹配且发布时间格式有效的预报对象
     * @throws AmapClient.Failure 预报数组、编码匹配或发布时间不符合要求
     */
    private JsonNode findForecast(JsonNode body, String adcode) {
        JsonNode forecasts = body.path("forecasts");
        if (!forecasts.isArray()) {
            throw new AmapClient.Failure("INVALID_RESPONSE");
        }
        JsonNode selected = null;
        for (JsonNode forecast : forecasts) {
            if (!forecast.isObject()) {
                throw new AmapClient.Failure("INVALID_RESPONSE");
            }
            if (forecast.path("adcode").isString() && adcode.equals(forecast.path("adcode").asString())) {
                if (selected != null) {
                    throw new AmapClient.Failure("INVALID_RESPONSE");
                }
                selected = forecast;
            }
        }
        if (selected == null) {
            throw new AmapClient.Failure("INVALID_RESPONSE");
        }
        try {
            LocalDateTime.parse(requiredText(selected, "reporttime"), REPORT_TIME_FORMAT);
        } catch (DateTimeException ex) {
            throw new AmapClient.Failure("INVALID_RESPONSE");
        }
        return selected;
    }

    /**
     * 逐日校验并转换预报，保留服务完整日期范围，拒绝重复或无效日期。
     *
     * @param forecast 与登记地点匹配且发布时间有效的预报对象
     * @return 有效的逐日结果；空温度字符串为 null，未提供的降水概率固定为 null
     * @throws AmapClient.Failure 逐日数组、日期、天气描述或温度不符合要求
     */
    private List<Map<String, Object>> parseDaily(JsonNode forecast) {
        JsonNode casts = forecast.path("casts");
        if (!casts.isArray() || casts.isEmpty()) {
            throw new AmapClient.Failure("INVALID_RESPONSE");
        }
        List<Map<String, Object>> daily = new ArrayList<>();
        Set<LocalDate> dates = new HashSet<>();
        for (JsonNode cast : casts) {
            String date = requiredText(cast, "date");
            try {
                LocalDate parsed = LocalDate.parse(date);
                if (!parsed.toString().equals(date) || !dates.add(parsed)) {
                    throw new AmapClient.Failure("INVALID_RESPONSE");
                }
            } catch (DateTimeException ex) {
                throw new AmapClient.Failure("INVALID_RESPONSE");
            }
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date);
            day.put("dayWeather", requiredText(cast, "dayweather"));
            day.put("nightWeather", requiredText(cast, "nightweather"));
            day.put("dayTemperature", parseTemperature(cast.path("daytemp")));
            day.put("nightTemperature", parseTemperature(cast.path("nighttemp")));
            day.put("precipitationProbability", null);
            daily.add(day);
        }
        return daily;
    }

    /**
     * 读取高德温度字符串，保持空字符串为未知，不把无效值转换为零。
     *
     * @param value daytemp 或 nighttemp 字段，单位按接口约定为摄氏度
     * @return 十进制温度数值；仅空字符串返回 null
     * @throws AmapClient.Failure 字段不是字符串，或不是可表示为有限数值的十进制温度
     */
    private BigDecimal parseTemperature(JsonNode value) {
        if (!value.isString()) {
            throw new AmapClient.Failure("INVALID_RESPONSE");
        }
        String text = value.asString();
        if (text.isEmpty()) {
            return null;
        }
        if (!text.matches("-?[0-9]+(?:\\.[0-9]+)?")) {
            throw new AmapClient.Failure("INVALID_RESPONSE");
        }
        try {
            BigDecimal temperature = new BigDecimal(text);
            if (!Double.isFinite(temperature.doubleValue())) {
                throw new AmapClient.Failure("INVALID_RESPONSE");
            }
            return temperature;
        } catch (NumberFormatException ex) {
            throw new AmapClient.Failure("INVALID_RESPONSE");
        }
    }

    /**
     * 获取必须存在的非空文本，不把缺失或其他类型转换为默认文本。
     *
     * @param object 含目标字段的服务 JSON 对象
     * @param field 应用指定的字段名
     * @return 保留原始内容的非空字符串
     * @throws AmapClient.Failure 字段缺失、类型错误或仅含空白
     */
    private String requiredText(JsonNode object, String field) {
        JsonNode value = object.path(field);
        if (!value.isString() || value.asString().isBlank()) {
            throw new AmapClient.Failure("INVALID_RESPONSE");
        }
        return value.asString();
    }

    /**
     * 组装适配层天气结果，明确查询时刻、服务发布时间以及字段与单位的解释边界。
     *
     * @param forecast 已校验的高德预报对象
     * @param daily 已校验并转换的完整逐日预报
     * @param location 本任务已登记的地点
     * @param uri 不含 Key 的本次来源地址
     * @return 状态为 OK 的规范化结果，不含实况、坐标或未经提供的降水概率数值
     */
    private Map<String, Object> createWeatherResult(JsonNode forecast, List<Map<String, Object>> daily,
            Map<String, Object> location, URI uri) {
        Instant retrievedAt = clock.instant();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("source", "高德地图");
        result.put("sourceUrl", uri.toString());
        result.put("retrievedAt", retrievedAt.toString());
        result.put("location", location);
        result.put("timezone", TIMEZONE.getId());
        result.put("localTime", retrievedAt.atZone(TIMEZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        result.put("reportTime", forecast.path("reporttime").asString());
        result.put("daily", daily);
        result.put("units", Map.of("dayTemperature", "°C", "nightTemperature", "°C",
                "precipitationProbability", "%"));
        result.put("interpretation", "本结果由适配层整理高德预报，字段结构、摄氏度单位和 Asia/Shanghai 时区按接口及应用约定提供。"
                + "dayTemperature/nightTemperature 是白天/夜间温度，不是最高/最低温度；空温度为 null。"
                + "服务未提供降水概率，precipitationProbability 为 null，不能解释为 0%。"
                + "localTime 是应用查询时钟的当地时间，reportTime 是服务返回的预报发布时间（Asia/Shanghai）。"
                + "本结果仅包含预报，不包含实况；超出 daily 返回日期范围的信息未知。");
        return result;
    }

    /**
     * 为缺少凭据提供固定配置提示，其他失败保留工具指定的缺口说明。
     *
     * @param failure 仅含应用预定义错误码的失败
     * @param fallback 工具为其他错误定义的固定说明
     * @return 不含凭据值的恢复提示
     */
    private String failureMessage(AmapClient.Failure failure, String fallback) {
        if ("MISSING_API_KEY".equals(failure.getMessage())) {
            return "缺少高德 Web 服务 Key，请通过 JVM 启动参数 -Damap.api.key 配置后重试。";
        }
        return fallback;
    }

    /**
     * 组装明确失败的工具结果，不附带模拟天气或默认数值。
     *
     * @param code 应用定义的固定错误码
     * @param message 面向模型的缺口或恢复提示
     * @return status 为 ERROR 的结果对象
     */
    private Map<String, Object> error(String code, String message) {
        return Map.of("status", "ERROR", "code", code, "message", message);
    }

    /**
     * 序列化结果并追加本任务事件，保持既有 tool、arguments、result 记录结构。
     *
     * @param tool 已开放的工具名称
     * @param args 本次工具参数，包括被工具拒绝的参数，不含 Key
     * @param result 规范化成功结果或明确错误结果，不含 Key
     * @param started 当前工具开始执行时的单调时钟读数，单位纳秒
     * @return 提供给工具调用框架的 JSON 文本
     */
    private String record(String tool, Map<String, Object> args, Map<String, Object> result, long started) {
        String encoded = json.writeValueAsString(result);
        events.add(Map.of("tool", tool, "arguments", args, "result", result));
        LOG.atLevel("OK".equals(result.get("status")) ? Level.INFO : Level.WARN).log(
                "工具结束：stage=tool_end, tool={}, status={}, code={}, registeredLocations={}, elapsedMs={}",
                tool, result.get("status"), result.getOrDefault("code", "NONE"), locations.size(),
                (System.nanoTime() - started) / 1_000_000);
        return encoded;
    }
}

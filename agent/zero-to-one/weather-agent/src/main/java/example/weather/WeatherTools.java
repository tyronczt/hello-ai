package example.weather;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 每项任务独立的真实天气工具；城市 ID 必须来自本次实际搜索，失败不返回模拟天气。 */
public final class WeatherTools {
    /** 应用构造 URL 后使用的 JSON 读取函数；包内构造器用于协议检查。 */
    private final Function<URI, JsonNode> fetch;
    /** 本次任务检索到的候选地点，不跨任务共享。 */
    private final Map<Long, Map<String, Object>> locations = new LinkedHashMap<>();
    /** 工具实际输入与结果的教学记录，无请求 Header。 */
    private final List<Map<String, Object>> events = new ArrayList<>();
    /** 工具返回使用 JSON 编码，不以字符串拼接外部内容。 */
    private final JsonMapper json = JsonMapper.builder().build();

    /** 创建访问 Open-Meteo 真实接口的工具实例。 */
    public WeatherTools() {
        this(new OpenMeteoClient()::get);
    }

    /** @param fetch 包内检查可注入替身，正式入口始终使用真实客户端 */
    WeatherTools(Function<URI, JsonNode> fetch) {
        this.fetch = java.util.Objects.requireNonNull(fetch);
    }

    /**
     * 搜索城市候选，保留行政区、国家与时区供模型判断同名地点。
     * @param city 2～80 个字符的城市名称，可含行政区限定
     * @return 候选地点或带错误码的 JSON，不自动选中第一项
     */
    @Tool(description = "通过 Open-Meteo 搜索城市，返回候选 ID、国家、行政区、坐标和时区。"
            + "同名地点需结合用户提供的地区判断；无法确定时追问，不默认取第一项。")
    public synchronized String searchLocations(
            @ToolParam(description = "城市名称，例如北京；有同名歧义时补充用户给出的地区") String city) {
        if (city == null || city.strip().length() < 2 || city.length() > 80) {
            return record("searchLocations", Map.of("city", city == null ? "" : city),
                    error("INVALID_ARGUMENT", "城市名称需要 2～80 个字符。"));
        }
        Map<String, Object> args = Map.of("city", city.strip());
        URI uri = URI.create("https://geocoding-api.open-meteo.com/v1/search?name="
                + URLEncoder.encode(city.strip(), StandardCharsets.UTF_8) + "&count=5&language=zh&format=json");
        try {
            JsonNode body = fetch.apply(uri);
            var results = new ArrayList<Map<String, Object>>();
            JsonNode candidates = body.path("results");
            if (!candidates.isMissingNode() && !candidates.isArray()) throw new OpenMeteoClient.Failure("INVALID_RESPONSE");
            for (JsonNode item : candidates) {
                long id = item.path("id").asLong(-1);
                double lat = item.path("latitude").asDouble(Double.NaN);
                double lon = item.path("longitude").asDouble(Double.NaN);
                if (id <= 0 || !Double.isFinite(lat) || !Double.isFinite(lon)
                        || Math.abs(lat) > 90 || Math.abs(lon) > 180 || !item.path("name").isString()) {
                    throw new OpenMeteoClient.Failure("INVALID_RESPONSE");
                }
                Map<String, Object> location = new LinkedHashMap<>();
                location.put("id", id);
                for (String field : List.of("name", "admin1", "admin2", "country", "country_code", "timezone")) {
                    location.put(field, item.path(field).asString(""));
                }
                location.put("latitude", lat);
                location.put("longitude", lon);
                results.add(location);
            }
            if (results.isEmpty()) return record("searchLocations", args, error("NOT_FOUND", "没有找到城市候选。"));
            // 全部候选校验完成才登记，避免部分无效响应污染本次可用地点。
            for (var location : results) locations.put((Long) location.get("id"), location);
            return record("searchLocations", args, Map.of("status", "OK", "source", "Open-Meteo / GeoNames",
                    "sourceUrl", uri.toString(), "retrievedAt", Instant.now().toString(), "locations", results));
        } catch (OpenMeteoClient.Failure ex) {
            return record("searchLocations", args, error(ex.getMessage(), "城市服务未返回可用结果；请稍后重试或补充地点。"));
        }
    }

    /**
     * 读取已检索地点的当前条件与三天预报；日期与单位来自实际服务响应。
     * @param locationId 本次 searchLocations 返回的地点 ID
     * @return 真实天气响应的选定字段；null 保持未知，错误不会被补成晴天或零
     */
    @Tool(description = "查询本次 searchLocations 已返回的地点 ID，取得当前天气条件及包含今天的三天预报。"
            + "返回当地时间、单位、来源和获取时间。current 基于气象模型数据，并非现场测量；"
            + "daily 是预报，超出返回日期范围的信息未知。不能把现在的天气当作明天的预报。")
    public synchronized String getWeather(
            @ToolParam(description = "从本次城市搜索结果选出的精确 ID，不是经纬度或行政区划编码") long locationId) {
        Map<String, Object> args = Map.of("locationId", locationId);
        var location = locations.get(locationId);
        if (location == null) return record("getWeather", args, error("UNKNOWN_LOCATION", "先搜索城市，再使用实际返回的 ID。"));
        URI uri = URI.create("https://api.open-meteo.com/v1/forecast?latitude=" + location.get("latitude")
                + "&longitude=" + location.get("longitude")
                + "&current=temperature_2m,weather_code"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&timezone=auto&forecast_days=3");
        try {
            JsonNode body = fetch.apply(uri);
            JsonNode times = body.path("daily").path("time");
            if (!times.isArray() || times.isEmpty() || !body.path("current").isObject()
                    || !body.path("timezone").isString() || !body.path("daily_units").isObject()
                    || !body.path("current_units").isObject()) throw new OpenMeteoClient.Failure("INVALID_RESPONSE");
            if (!body.path("current").path("time").isString()) throw new OpenMeteoClient.Failure("INVALID_RESPONSE");
            try { java.time.LocalDateTime.parse(body.path("current").path("time").asString()); }
            catch (java.time.DateTimeException ex) { throw new OpenMeteoClient.Failure("INVALID_RESPONSE"); }
            for (String field : List.of("temperature_2m", "weather_code")) {
                JsonNode value = body.path("current").path(field);
                if ((!value.isNull() && !value.isNumber()) || !body.path("current_units").path(field).isString()) {
                    throw new OpenMeteoClient.Failure("INVALID_RESPONSE");
                }
            }
            for (String field : List.of("weather_code", "temperature_2m_max", "temperature_2m_min", "precipitation_probability_max")) {
                JsonNode values = body.path("daily").path(field);
                if (!values.isArray() || values.size() != times.size() || !body.path("daily_units").path(field).isString()) {
                    throw new OpenMeteoClient.Failure("INVALID_RESPONSE");
                }
                for (JsonNode value : values) {
                    if (!value.isNull() && !value.isNumber()) throw new OpenMeteoClient.Failure("INVALID_RESPONSE");
                }
            }
            for (JsonNode time : times) {
                if (!time.isString()) throw new OpenMeteoClient.Failure("INVALID_RESPONSE");
                try { java.time.LocalDate.parse(time.asString()); }
                catch (java.time.DateTimeException ex) { throw new OpenMeteoClient.Failure("INVALID_RESPONSE"); }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "OK");
            result.put("source", "Open-Meteo");
            result.put("sourceUrl", uri.toString());
            result.put("retrievedAt", Instant.now().toString());
            result.put("location", location);
            for (String field : List.of("timezone", "utc_offset_seconds", "current", "current_units", "daily", "daily_units")) {
                result.put(field, body.path(field));
            }
            result.put("currentWeatherDescription", describeCode(body.path("current").path("weather_code")));
            List<String> descriptions = new ArrayList<>();
            for (JsonNode code : body.path("daily").path("weather_code")) descriptions.add(describeCode(code));
            result.put("dailyWeatherDescriptions", descriptions);
            result.put("interpretation", "current 是气象模型的当前条件；daily 是预报。"
                    + "precipitation_probability_max 是当天降水概率最大值，不代表一定下雨；null 为未知。"
                    + "天气编码解释见 https://open-meteo.com/en/docs#weathervariables 。");
            return record("getWeather", args, result);
        } catch (OpenMeteoClient.Failure ex) {
            return record("getWeather", args, error(ex.getMessage(), "天气服务未返回可用结果，不能据此生成天气数值。"));
        }
    }

    /** @return 本次调用记录的快照；仅用于显式运行的教学报告 */
    public synchronized List<Map<String, Object>> events() {
        return List.copyOf(events);
    }

    /** @param code 服务返回的 WMO 编码 @return 按 Open-Meteo 官方表映射的描述，未知编码不猜测 */
    private String describeCode(JsonNode code) {
        if (!code.isIntegralNumber()) return "未知";
        return switch (code.asInt()) {
            case 0 -> "晴";
            case 1 -> "大部晴朗";
            case 2 -> "局部多云";
            case 3 -> "阴";
            case 45 -> "雾";
            case 48 -> "雾凇雾";
            case 51, 53, 55 -> "毛毛雨（强度按原始 WMO 编码区分）";
            case 56, 57 -> "冻毛毛雨";
            case 61 -> "小雨";
            case 63 -> "中雨";
            case 65 -> "大雨";
            case 66, 67 -> "冻雨";
            case 71 -> "小雪";
            case 73 -> "中雪";
            case 75 -> "大雪";
            case 77 -> "米雪";
            case 80, 81, 82 -> "阵雨（强度按原始 WMO 编码区分）";
            case 85, 86 -> "阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "伴冰雹的雷暴";
            case 97 -> "强雷暴";
            default -> "未知天气编码：" + code.asInt();
        };
    }

    /** @param code 固定错误码 @param message 面向模型的恢复提示 @return 明确失败的结果 */
    private Map<String, Object> error(String code, String message) {
        return Map.of("status", "ERROR", "code", code, "message", message);
    }

    /** @param tool 工具名 @param args 已校验或明确失败的参数 @param result 实际结果 @return JSON 文本 */
    private String record(String tool, Map<String, Object> args, Map<String, Object> result) {
        String encoded = json.writeValueAsString(result);
        events.add(Map.of("tool", tool, "arguments", args, "result", result));
        return encoded;
    }
}

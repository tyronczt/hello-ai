package example.weather;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tools.jackson.databind.json.JsonMapper;

/** 独立命令行天气实践；不会启动 HTTP 服务或改变 first-agent 的入口。 */
@SpringBootApplication
public class WeatherApplication {
    /** 教学报告编码器，只写问题、消息和公开天气结果。 */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * 显式选择真实天气连通检查或模型实验；无参数只打印用法。
     * @param args --weather-check，或 --ask=问题 [--prompt=rules] [--context=full]
     * @throws Exception 配置、网络或报告写入失败，命令以非零状态结束
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("天气工具检查：--weather-check（真实 Open-Meteo，不调用模型）\n"
                    + "模型实验：--ask=问题 --prompt=basic|rules|examples --context=full|state|compact\n"
                    + "模型实验需要 DEEPSEEK_API_KEY；报告会保存本次问题，请使用公开教学问题。");
            return;
        }
        Map<String, String> choices = parse(args);
        Map<String, Object> report;
        if (choices.containsKey("weather-check")) {
            if (choices.size() != 1) throw new IllegalArgumentException("--weather-check 需要独立运行。");
            WeatherTools tools = new WeatherTools();
            String locations = tools.searchLocations("北京");
            // 这是固定公开地点的连通检查；1816670 来自已核验的 Open-Meteo 北京候选。
            // 正常 Agent 入口由模型结合用户要求选 ID，不执行这段固定选择。
            String weather = tools.getWeather(1816670);
            boolean ok = "OK".equals(JSON.readTree(locations).path("status").asString(""))
                    && "OK".equals(JSON.readTree(weather).path("status").asString(""));
            report = new LinkedHashMap<>(Map.of("kind", "LIVE_WEATHER_TOOL_CHECK", "status", ok ? "COMPLETED" : "STOPPED",
                    "toolEvents", tools.events(), "answer", ok ? "真实城市与天气接口返回成功。" : "天气接口检查失败，请查看工具错误码。"));
        } else {
            String question = choices.get("ask");
            String prompt = choices.getOrDefault("prompt", "rules");
            String context = choices.getOrDefault("context", "full");
            if (question == null || question.isBlank() || question.length() > 1000) throw new IllegalArgumentException("请提供 1～1000 字符的问题。");
            // 创建模型客户端前验证选择，非法参数不触发外部调用。
            new WeatherContext(WeatherPrompts.of(prompt), question, context);
            SpringApplication app = new SpringApplication(WeatherApplication.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            try (var spring = app.run()) {
                report = new WeatherAgent(spring.getBean(ChatModel.class)).run(question, prompt, context);
                report.put("kind", "MODEL_RUN");
            }
        }
        Path root = Path.of("target", "weather-runs").toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path output = Files.createTempDirectory(root, "run-").resolve("report.json");
        Files.writeString(output, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
        System.out.println(report.get("status") + "：" + report.get("answer"));
        System.out.println("报告：" + output);
        if (!"COMPLETED".equals(report.get("status"))) throw new IllegalStateException("任务未完成，报告已保存。");
    }

    /** @param args 原始参数 @return 校验后的有限选项，不接受任意配置注入 */
    private static Map<String, String> parse(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) throw new IllegalArgumentException("参数需使用 --名称=值。");
            String[] pair = arg.substring(2).split("=", 2);
            String name = pair[0];
            if (!List.of("ask", "prompt", "context", "weather-check").contains(name)
                    || result.containsKey(name) || (name.equals("weather-check") ? pair.length != 1 : pair.length != 2)) {
                throw new IllegalArgumentException("未知、重复或格式错误的参数。");
            }
            result.put(name, pair.length == 2 ? pair[1] : "");
        }
        return result;
    }
}

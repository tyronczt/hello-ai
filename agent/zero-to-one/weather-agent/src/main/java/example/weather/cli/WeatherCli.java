package example.weather.cli;

import example.weather.WeatherApplication;
import example.weather.agent.WeatherContext;
import example.weather.agent.WeatherPrompts;
import example.weather.dto.WeatherAskDTO;
import example.weather.dto.WeatherResultDTO;
import example.weather.service.WeatherReportStore;
import example.weather.service.WeatherService;
import example.weather.tool.WeatherTools;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import tools.jackson.databind.json.JsonMapper;

/**
 * 可选的命令行辅助工具；每次执行一项检查或模型实验，不启动 HTTP 服务。
 * 正常业务调用使用 WeatherApplication 启动服务，本入口仅用于排查和对照实验。
 */
public final class WeatherCli {
    /** 教学报告编码器；报告包含用户问题、消息和工具结果，调用方应使用公开教学问题。 */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 命令行入口只通过 main 使用，不创建工具类实例。 */
    private WeatherCli() {
    }

    /**
     * 选择帮助、真实天气连通检查或一次性模型实验；无参数只打印用法。
     *
     * @param args --help、--weather-check，或 --ask=问题及其模式选项
     * @throws IllegalArgumentException 命令行参数或任务选项不符合约定
     * @throws IllegalStateException 一次性任务未完成，报告已保存
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        Map<String, String> choices = parse(args);
        if (choices.containsKey("help")) {
            if (choices.size() != 1) {
                throw new IllegalArgumentException("--help 需要独立运行。");
            }
            printUsage();
            return;
        }
        WeatherResultDTO result;
        if (choices.containsKey("weather-check")) {
            if (choices.size() != 1) {
                throw new IllegalArgumentException("--weather-check 需要独立运行。");
            }
            Map<String, Object> report = runWeatherCheck();
            String taskId = new WeatherReportStore().save(report);
            result = new WeatherResultDTO(taskId, (String) report.get("status"), (String) report.get("answer"));
        } else {
            result = runModelTask(choices);
        }
        printResult(result);
    }

    /** 打印默认服务入口和可选的一次性命令，不启动 Spring 或调用外部服务。 */
    private static void printUsage() {
        System.out.println("HTTP 服务：在 IDEA 中运行 WeatherApplication；本 WeatherCli 仅执行一次性任务。\n"
                + "HTTP 接口：POST http://127.0.0.1:8081/api/weather/ask（默认地址）\n"
                + "天气工具检查：--weather-check（真实高德接口，不调用模型）\n"
                + "模型实验：--ask=问题 --prompt=basic|rules|examples --context=full|state|compact\n"
                + "服务及模型实验需要 DEEPSEEK_API_KEY；天气工具需 JVM 参数 -Damap.api.key=你的Key。\n"
                + "报告会保存本次问题，请使用公开教学问题。");
    }

    /**
     * 搜索北京并从本次结果选取唯一同名候选，检查真实行政区与预报接口。
     * 需要 JVM 系统属性 amap.api.key，不调用模型或使用模拟数据兜底；候选缺失或有歧义时不查询天气。
     *
     * @return 包含工具记录的报告；两项工具结果均为 OK 时状态才为 COMPLETED
     */
    private static Map<String, Object> runWeatherCheck() {
        WeatherTools tools = new WeatherTools();
        String locations = tools.searchLocations("北京");
        var result = JSON.readTree(locations);
        List<Long> matches = new ArrayList<>();
        for (var candidate : result.path("locations")) {
            String name = candidate.path("name").asString("");
            if ("北京".equals(name) || "北京市".equals(name)) {
                matches.add(candidate.path("id").asLong());
            }
        }
        boolean searched = "OK".equals(result.path("status").asString(""));
        String weather = searched && matches.size() == 1 ? tools.getWeather(matches.getFirst()) : null;
        boolean ok = weather != null && "OK".equals(JSON.readTree(weather).path("status").asString(""));
        return new LinkedHashMap<>(Map.of(
                "kind", "LIVE_WEATHER_TOOL_CHECK",
                "status", ok ? "COMPLETED" : "STOPPED",
                "toolEvents", tools.events(),
                "answer", ok ? "高德行政区与预报接口返回成功。"
                        : "高德工具检查未完成，请查看工具错误码或候选歧义。"));
    }

    /**
     * 校验模型任务选项后启动 Spring，执行一次任务并关闭应用上下文。
     *
     * @param choices 已通过参数名及格式校验的命令行选项
     * @return 已保存报告的任务摘要；COMPLETED 仅表示产生非空候选回答，不表示事实核验通过
     * @throws IllegalArgumentException 问题为空、超过 1000 个 UTF-16 单元或模式不受支持
     */
    private static WeatherResultDTO runModelTask(Map<String, String> choices) {
        String question = choices.get("ask");
        String prompt = choices.getOrDefault("prompt", "rules");
        String context = choices.getOrDefault("context", "full");
        if (question == null || question.isBlank() || question.length() > 1000) {
            throw new IllegalArgumentException("请提供 1～1000 字符的问题。");
        }
        // 在创建模型客户端前拒绝非法模式，避免无效任务触发外部调用。
        new WeatherContext(WeatherPrompts.of(prompt), question, context);
        SpringApplication app = new SpringApplication(WeatherApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var spring = app.run()) {
            return spring.getBean(WeatherService.class).process(new WeatherAskDTO(question, prompt, context));
        }
    }

    /**
     * 打印一次性任务的摘要与本地报告路径；STOPPED 报告已经保存，再以异常结束命令。
     *
     * @param result 天气检查或模型任务生成的摘要，包含服务生成的任务编号、状态和答案
     * @throws IllegalStateException 报告状态不是 COMPLETED，报告已保存
     */
    private static void printResult(WeatherResultDTO result) {
        Path output = Path.of("target", "weather-runs", result.getTaskId(), "report.json")
                .toAbsolutePath().normalize();
        System.out.println(result.getStatus() + "：" + result.getAnswer());
        System.out.println("报告：" + output);
        if (!"COMPLETED".equals(result.getStatus())) {
            throw new IllegalStateException("任务未完成，报告已保存。");
        }
    }

    /**
     * 解析有限的命令行选项，拒绝未知名称、重复项和错误格式。
     *
     * @param args 原始命令行参数；help 和 weather-check 无值，其余选项使用 --名称=值
     * @return 已校验名称与格式的选项；问题内容和模式值由任务入口继续校验
     * @throws IllegalArgumentException 参数名称、重复情况或格式不符合约定
     */
    private static Map<String, String> parse(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("参数需使用 --名称=值。");
            }
            String[] pair = arg.substring(2).split("=", 2);
            String name = pair[0];
            if (!List.of("ask", "prompt", "context", "weather-check", "help").contains(name)
                    || result.containsKey(name)
                    || (List.of("weather-check", "help").contains(name) ? pair.length != 1 : pair.length != 2)) {
                throw new IllegalArgumentException("未知、重复或格式错误的参数。");
            }
            result.put(name, pair.length == 2 ? pair[1] : "");
        }
        return result;
    }
}

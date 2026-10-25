package example.weather.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 保存本次公开教学任务的详细报告；目录由服务生成，不接受客户端指定存储路径。 */
@Component
public class WeatherReportStore {
    /** 报告 JSON 编码器，不写入模型客户端的 Header 或密钥配置。 */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 相对于模块工作目录的报告根目录；每个任务使用独立子目录。 */
    private final Path root = Path.of("target", "weather-runs").toAbsolutePath().normalize();

    /**
     * 保存一次模型任务或天气接口检查的完整报告。
     * 报告包含原始问题与工具结果，调用方应使用公开教学问题。
     *
     * @param report 当前任务独占的报告对象，包含 status 和 answer
     * @return 服务生成的任务目录名，例如 run- 后接唯一目录标识；不包含绝对路径
     * @throws UncheckedIOException 目录创建或报告文件写入失败，不返回伪造的成功任务
     */
    public String save(Map<String, Object> report) {
        try {
            Files.createDirectories(root);
            Path directory = Files.createTempDirectory(root, "run-");
            Path output = directory.resolve("report.json");
            Files.writeString(output, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report),
                    StandardCharsets.UTF_8);
            return directory.getFileName().toString();
        } catch (IOException ex) {
            throw new UncheckedIOException("天气任务报告保存失败。", ex);
        }
    }
}

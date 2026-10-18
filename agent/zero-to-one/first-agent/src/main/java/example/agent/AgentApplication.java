package example.agent;

import example.agent.context.ContextLab;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** HTTP 与教学入口；离线上下文实验在创建 Spring 容器前单独运行。 */
@SpringBootApplication
public class AgentApplication {
    /**
     * 无教学参数时启动 HTTP 服务；上下文离线实验不读取模型配置、不创建 Spring 容器。
     * 其他教学模式创建非 Web 容器，由 DemoRunner 显式触发模型调用。
     *
     * @param args 原始启动参数，继续传给 SpringApplication 与 DemoRunner
     * @throws java.io.IOException 离线实验的本机服务或报告无法访问
     */
    public static void main(String[] args) throws java.io.IOException {
        var offline = Arrays.stream(args).filter(arg -> arg.equals("--context-lab")
                || arg.startsWith("--context-lab=")).findFirst();
        if (offline.isPresent()) {
            if (args.length != 1) throw new IllegalArgumentException("--context-lab 需要独立运行。");
            String option = offline.get();
            ContextLab.offline(option.equals("--context-lab") ? "all" : option.substring("--context-lab=".length()));
            return;
        }
        var app = new SpringApplication(AgentApplication.class);
        if (Arrays.stream(args).anyMatch(arg -> arg.equals("--guided")
                || arg.equals("--stage") || arg.startsWith("--stage=")
                || arg.equals("--context-live") || arg.startsWith("--context-live="))) {
            // 教学模式仍会创建 Spring 容器并运行 DemoRunner，但不启动内嵌 Web 服务器；
            // 因此不会监听 8080 端口，POST /api/agent/ask 也无法在此模式下调用。
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }
}

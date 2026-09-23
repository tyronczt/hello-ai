package example.agent;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** HTTP 服务入口；教学参数仅决定是否以非 Web 模式启动。 */
@SpringBootApplication
public class AgentApplication {
    /** 无教学参数时启动 HTTP 服务；传入 --guided 或 --stage 时只运行本地教学流程。 */
    public static void main(String[] args) {
        var app = new SpringApplication(AgentApplication.class);
        if (Arrays.stream(args).anyMatch(arg -> arg.equals("--guided")
                || arg.equals("--stage") || arg.startsWith("--stage="))) {
            // 教学模式仍会创建 Spring 容器并运行 DemoRunner，但不启动内嵌 Web 服务器；
            // 因此不会监听 8080 端口，POST /api/agent/ask 也无法在此模式下调用。
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }
}

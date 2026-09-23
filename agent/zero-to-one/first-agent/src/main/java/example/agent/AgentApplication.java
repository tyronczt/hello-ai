package example.agent;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** HTTP 服务入口；教学参数仅决定是否以非 Web 模式启动。 */
@SpringBootApplication
public class AgentApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(AgentApplication.class);
        if (Arrays.stream(args).anyMatch(arg -> arg.equals("--guided")
                || arg.equals("--stage") || arg.startsWith("--stage="))) {
            app.setWebApplicationType(WebApplicationType.NONE);
        }
        app.run(args);
    }
}

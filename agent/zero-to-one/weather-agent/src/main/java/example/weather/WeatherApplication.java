package example.weather;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 天气 Agent 的 Spring Boot 启动类；请求处理与任务执行由子包中的组件负责。 */
@SpringBootApplication
public class WeatherApplication {
    /**
     * 启动 HTTP 服务，在收到请求后由 Controller 进入天气任务流程。
     *
     * @param args Spring Boot 启动参数；IDEA 常规调试时留空
     */
    public static void main(String[] args) {
        SpringApplication.run(WeatherApplication.class, args);
    }
}

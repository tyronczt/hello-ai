package example.conversation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 本地教学服务，默认监听 127.0.0.1:8081。 */
@SpringBootApplication
public class ConversationApplication {
    /**
     * 启动同步教学 HTTP 服务；监听地址和端口以配置及其环境变量覆盖值为准。
     *
     * @param args 传给 SpringApplication 的原始启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ConversationApplication.class, args);
    }
}

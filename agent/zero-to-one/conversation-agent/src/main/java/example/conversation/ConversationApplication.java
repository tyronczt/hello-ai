package example.conversation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 本地教学服务，默认监听 127.0.0.1:8081。 */
@SpringBootApplication
public class ConversationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConversationApplication.class, args);
    }
}

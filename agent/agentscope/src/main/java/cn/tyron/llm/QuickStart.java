package cn.tyron.llm;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;

/**
 * @description: 创建一个智能体对象
 * @author: tyron
 * @create: 2026-03-27
 **/
public class QuickStart {
    public static void main(String[] args) {
        String apiKey = requireEnv("OPENAI_API_KEY");
        String baseUrl = requireEnv("OPENAI_BASE_URL");
        String modelName = requireEnv("OPENAI_MODEL");

        // 准备工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools());

        // 创建智能体
        ReActAgent jarvis = ReActAgent.builder()
                .name("Jarvis")
                .sysPrompt("你是一个名为 Jarvis 的助手")
                .model(OpenAIChatModel.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .modelName(modelName)
                        .build())
                .toolkit(toolkit)
                .build();

        // 发送消息
        Msg msg = Msg.builder()
                .textContent("你好！Jarvis，现在几点了？")
                .build();

        Msg response = jarvis.call(msg).block();
        System.out.println(response.getTextContent());
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("请先设置环境变量 " + name);
        }
        return value;
    }
}

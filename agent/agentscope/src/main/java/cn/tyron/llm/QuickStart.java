package cn.tyron.llm;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.AnthropicChatModel;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;

import java.awt.*;

/**
 * @description: 创建一个智能体对象
 * @author: chenzt
 * @create: 2026-03-27
 **/
public class QuickStart {
    public static void main(String[] args) {
        // 准备工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools());

        // 创建智能体
        ReActAgent jarvis = ReActAgent.builder()
                .name("Jarvis")
                .sysPrompt("你是一个名为 Jarvis 的助手")
                .model(OpenAIChatModel.builder()
                        .baseUrl("https://api.aigcly.top/v1")
                        .apiKey("sk-KNXGOMFJXgeqrd4VHLbZ86oNkHZS5hTFMNo0yTXk5gLaNAVw")
                        .modelName("gpt-5.4")
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
}

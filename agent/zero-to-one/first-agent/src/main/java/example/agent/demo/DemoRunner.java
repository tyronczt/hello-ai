package example.agent.demo;

import example.agent.dto.AgentResultDTO;
import example.agent.dto.AskDTO;
import example.agent.service.DocAgent;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 命令行教学流程；仅在显式传入 --guided 或 --stage 时运行，不参与 HTTP 请求。 */
@Component
public class DemoRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);
    private final DocAgent agent;

    public DemoRunner(DocAgent agent) {
        this.agent = agent;
    }

    /** 按启动参数选择四阶段交互练习或单阶段复查；HTTP 模式下不触发模型请求。 */
    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("guided")) {
            guided();
            return;
        }
        if (!args.containsOption("stage")) return;
        var values = args.getOptionValues("stage");
        int stage;
        try {
            stage = Integer.parseInt(values.isEmpty() ? "" : values.getFirst());
        } catch (NumberFormatException ex) {
            log.info("停止：stage 只能是 0、1、2 或 3。");
            return;
        }
        if (args.getNonOptionArgs().isEmpty()) {
            log.info("请在 --stage 后输入本次问题。");
            return;
        }
        String question = String.join(" ", args.getNonOptionArgs());
        log.info("问题：{}", question);
        log.info("阶段：{}", stage);
        show(new AskDTO(question), stage);
    }

    /** 同一个用户问题依次对照四个阶段；每轮先预测，再由用户决定是否调用线上模型。 */
    private void guided() {
        // 同一个问题跑四轮，读者才能把答案变化归因于规则、资料和工具。
        var input = new Scanner(System.in);
        log.info("四轮对照练习：每轮先预测，再决定是否调用线上模型。每次调用都会产生用量。");
        String chosen = read(input, "请输入你的问题，例如：订单查询的分页参数怎么传？\n> ");
        if (chosen == null) return;
        String question = chosen.strip();
        if (question.isEmpty()) {
            log.info("停止：请先输入问题。");
            return;
        }
        if (question.length() > 1000) {
            log.info("停止：问题不能超过 1000 个字符。");
            return;
        }
        String[] titles = {"0 只有问题", "1 增加任务规则", "2 放入两份文档", "3 按需调用工具"};
        String[] checks = {
                "模型没有项目资料；即使答对数值，也找不到项目依据。",
                "规则要求引用，但本轮没有正文或工具；检查它是否承认资料不足。",
                "两份正文已随问题发送；核对 pageNo、pageSize 和未规定的默认值。",
                "初始消息没有正文；看 searchDocs/readDoc 的请求、返回值和下一轮决定。"};
        for (int stage = 0; stage < titles.length; stage++) {
            log.info("=== 阶段 {} ===", titles[stage]);
            log.info("本轮模型收到：");
            for (var message : DocAgent.initialMessages(question, stage)) {
                log.info("[{}] {}", message instanceof SystemMessage ? "system" : "user", message.getText());
            }
            if (stage == 3) {
                log.info("[工具定义] searchDocs(keyword)：返回 ID/标题；readDoc(docId)：返回正文。");
            }
            // 可以在预测处直接跳过或退出；写下预测后仍由用户决定是否调用线上模型。
            String prediction = read(input, "你预测它会怎样回答或行动？输入 s 跳过，输入 q 退出：\n> ");
            if (prediction == null || prediction.equalsIgnoreCase("q")) return;
            if (prediction.equalsIgnoreCase("s")) continue;
            String choice = read(input, "回车运行；输入 s 跳过本阶段；输入 q 退出：");
            if (choice == null || choice.equalsIgnoreCase("q")) return;
            if (choice.equalsIgnoreCase("s")) continue;
            if (!choice.isBlank()) {
                log.info("未识别的选择，已停止；本轮没有发起请求。");
                return;
            }
            log.info("实际执行：");
            show(new AskDTO(question), stage);
            log.info("你的预测：{}", prediction.isBlank() ? "未填写" : prediction);
            log.info("对照重点：{}", checks[stage]);
            if (read(input, "写一句你观察到的差异，或直接回车继续：\n> ") == null) return;
        }
        log.info("练习结束。请用实际读取的文档核对最终答案。不同模型运行路径可能不同。");
    }

    /** 运行指定阶段并展示最终结果；执行轨迹由 DocAgent 实时写入日志。 */
    private void show(AskDTO request, int stage) {
        AgentResultDTO result = agent.runStage(request, stage);
        // DocAgent 已在事件发生时输出轨迹；这里仅展示最终答案，避免同一条轨迹打印两次。
        if (result.status() == AgentResultDTO.Status.COMPLETED) {
            log.info("候选答案（请核对来源）：\n{}", result.answer());
        } else {
            log.info("停止：{}", result.answer());
        }
    }

    private static String read(Scanner input, String prompt) {
        log.info("{}", prompt);
        // IDEA 运行窗口或管道关闭输入时安全退出；不在 Agent 中处理终端交互。
        return input.hasNextLine() ? input.nextLine() : null;
    }
}

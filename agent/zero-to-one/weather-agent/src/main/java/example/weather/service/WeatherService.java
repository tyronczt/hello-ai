package example.weather.service;

import example.weather.agent.WeatherAgent;
import example.weather.dto.WeatherAskDTO;
import example.weather.dto.WeatherResultDTO;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/** 天气任务应用服务：执行 Agent、保存教学报告，再返回 HTTP 与命令行共用的任务摘要。 */
@Service
public class WeatherService {
    /** 记录任务阶段、编号、状态和耗时，不记录问题、答案、工具正文或模型密钥。 */
    private static final Logger LOG = LoggerFactory.getLogger(WeatherService.class);

    /** 复用模型客户端的执行器；每次 run 的工具、消息及计数仍为任务内局部状态。 */
    private final WeatherAgent agent;

    /** 为每次请求生成独立报告目录的存储组件。 */
    private final WeatherReportStore reports;

    /**
     * 注入模型客户端和报告存储，构造阶段不调用模型或查询天气。
     *
     * @param model Spring 配置的模型客户端
     * @param reports 教学报告存储组件
     */
    public WeatherService(ChatModel model, WeatherReportStore reports) {
        this.agent = new WeatherAgent(model);
        this.reports = reports;
    }

    /**
     * 同步处理一次提问；每次调用独立创建工具和消息，不接续前一个 HTTP 请求。
     * COMPLETED 表示取得非空候选，STOPPED 表示未完成；两者均保存报告，不自动核验答案。
     *
     * @param request 本次问题与模式，由 Controller 或命令行入口转换；不包含用户身份或存储路径
     * @return 任务目录编号、停止状态及候选答案或停止原因
     * @throws IllegalArgumentException 请求为空，或问题及模式不符合 Agent 的要求
     * @throws UncheckedIOException 本次报告无法保存
     */
    public WeatherResultDTO process(WeatherAskDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("天气任务请求不能为空。");
        }
        long started = System.nanoTime();
        LOG.info("天气任务开始：stage=task_start");
        Map<String, Object> report = agent.run(
                request.getQuestion(), request.getPromptMode(), request.getContextMode());
        report.put("kind", "MODEL_RUN");
        LOG.info("Agent 已返回，准备保存报告：stage=task_report, status={}, modelCalls={}, requestedToolCalls={}",
                report.get("status"), ((List<?>) report.get("rounds")).size(),
                report.get("requestedToolCalls"));
        String taskId = reports.save(report);
        String status = (String) report.get("status");
        LOG.info("天气任务结束：stage=task_end, taskId={}, status={}, elapsedMs={}",
                taskId, status, (System.nanoTime() - started) / 1_000_000);
        return new WeatherResultDTO(taskId, status, (String) report.get("answer"));
    }
}

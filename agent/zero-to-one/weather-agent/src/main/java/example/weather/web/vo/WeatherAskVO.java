package example.weather.web.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 天气任务的 HTTP 结果摘要；合法任务的 COMPLETED 和 STOPPED 状态都使用 HTTP 200 返回。 */
@Getter
@AllArgsConstructor
public class WeatherAskVO implements Serializable {
    /** Java 对象流的类版本，不控制 JSON 字段契约。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 服务创建的本地报告目录名，供关联本次任务；不暴露本机绝对路径。 */
    private final String taskId;

    /** 执行状态：COMPLETED 为已产生候选回答，STOPPED 为停止；不代表答案已经自动验真。 */
    private final String status;

    /** 来自 Agent 的候选回答或停止原因；候选回答可能要求用户补充地点等必要条件。 */
    private final String answer;
}

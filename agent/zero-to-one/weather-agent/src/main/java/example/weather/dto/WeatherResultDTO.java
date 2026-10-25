package example.weather.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 服务执行并保存天气任务报告后返回的结果摘要，不暴露原始报告或本机绝对路径。 */
@Getter
@AllArgsConstructor
public class WeatherResultDTO implements Serializable {
    /** Java 对象流的类版本，不控制 JSON 字段契约。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 本地任务报告目录名，由服务创建；不包含父目录或绝对路径。 */
    private final String taskId;

    /** 执行状态：COMPLETED 表示已产生候选回答，STOPPED 表示停止；二者均不代表事实自动核验。 */
    private final String status;

    /** Agent 产生的候选回答或停止原因；候选回答也可能是需要用户补充条件的追问。 */
    private final String answer;
}

package example.weather.web.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** HTTP 拒绝或未预期异常的固定错误结构，不携带输入值、异常堆栈或本机路径。 */
@Getter
@AllArgsConstructor
public class WeatherErrorVO implements Serializable {
    /** Java 对象流的类版本，不控制 JSON 字段契约。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 由异常处理器选择的固定错误码，用于区分请求错误、协议拒绝与内部故障。 */
    private final String code;

    /** 面向调用方的固定说明，不直接使用异常消息或字段校验中的拒绝值。 */
    private final String message;
}

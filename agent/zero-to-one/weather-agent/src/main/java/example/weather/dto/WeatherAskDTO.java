package example.weather.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 服务内部使用的天气任务输入，由 Web 层完成请求绑定和校验后转换得到。 */
@Getter
@AllArgsConstructor
public class WeatherAskDTO implements Serializable {
    /** Java 对象流的类版本，不控制 JSON 字段契约。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户原始问题；Web 入口要求非空白且长度不超过 1000 个 UTF-16 编码单元。 */
    private final String question;

    /** 提示词模式，支持 basic、rules、examples；Web 层已应用默认值并校验。 */
    private final String promptMode;

    /** 上下文模式，支持 full、state、compact；Web 层已应用默认值并校验。 */
    private final String contextMode;
}

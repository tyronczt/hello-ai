package example.weather.web.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 天气查询接口的 JSON 请求体；省略模式字段时使用默认值，显式 null 或空白值校验失败。 */
@Getter
@Setter
@NoArgsConstructor
public class WeatherAskQuery implements Serializable {
    /** Java 对象流的类版本，不控制 JSON 字段契约。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户原始问题，非空白且最多 1000 个 UTF-16 编码单元，与当前 Agent 的长度限制一致。 */
    @NotBlank(message = "question 不能为空白。")
    @Size(max = 1000, message = "question 长度不能超过 1000。")
    private String question;

    /** 提示词模式，省略时为 rules；只接受 basic、rules 或 examples，不自动修剪或转换大小写。 */
    @NotBlank(message = "promptMode 不能为空白。")
    @Pattern(regexp = "basic|rules|examples", message = "promptMode 不是支持的模式。")
    private String promptMode = "rules";

    /** 上下文模式，省略时为 full；只接受 full、state 或 compact，不自动修剪或转换大小写。 */
    @NotBlank(message = "contextMode 不能为空白。")
    @Pattern(regexp = "full|state|compact", message = "contextMode 不是支持的模式。")
    private String contextMode = "full";
}

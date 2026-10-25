package example.weather.web;

import example.weather.dto.WeatherAskDTO;
import example.weather.dto.WeatherResultDTO;
import example.weather.service.WeatherService;
import example.weather.web.query.WeatherAskQuery;
import example.weather.web.vo.WeatherAskVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 天气教学接口的 Web 边界，只负责请求校验和 Query、DTO、VO 转换；任务执行与报告保存由服务完成。 */
@RestController
@RequestMapping("/api/weather")
public class WeatherController {
    /** 执行天气任务并保存本地报告的服务，不在控制器中直接调用模型或天气接口。 */
    private final WeatherService weatherService;

    /**
     * 注入负责天气任务处理的服务。
     *
     * @param weatherService 由应用容器提供的天气任务服务
     */
    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /**
     * POST /api/weather/ask：接收 application/json 请求并返回 application/json 任务摘要。
     * 省略模式字段时使用 rules 和 full；显式 null、空白或不支持的模式被拒绝。
     * 请求体缺失、JSON 错误及字段校验失败由 Spring MVC 在调用本方法前返回 400；
     * 方法不支持返回 405，媒体类型不支持返回 415，未预期异常返回 500。
     * 本教学接口不执行身份认证，监听范围由应用配置限制。
     *
     * @param query 已完成 JSON 绑定和字段校验的请求，不包含原始报告或客户端指定的任务 ID
     * @return HTTP 200 的任务摘要；COMPLETED 和 STOPPED 都是合法执行结果，不代表答案自动验真
     */
    @PostMapping(path = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public WeatherAskVO ask(@Valid @RequestBody WeatherAskQuery query) {
        WeatherAskDTO input = new WeatherAskDTO(query.getQuestion(), query.getPromptMode(), query.getContextMode());
        WeatherResultDTO result = weatherService.process(input);
        return new WeatherAskVO(result.getTaskId(), result.getStatus(), result.getAnswer());
    }
}

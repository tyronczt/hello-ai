package example.weather.web;

import example.weather.web.vo.WeatherErrorVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 统一天气应用的 HTTP 错误结构，保留协议状态及响应头，避免将异常消息或输入内容回显给调用方。 */
@RestControllerAdvice
public class WeatherExceptionHandler extends ResponseEntityExceptionHandler {
    /** 记录 HTTP 拒绝和失败的状态及异常类型，不记录请求正文、异常消息或堆栈。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(WeatherExceptionHandler.class);

    /**
     * 将 Spring MVC 的绑定、校验和协议错误转换成固定错误正文。
     * 保留框架选择的状态及 Allow 等响应头，统一使用 application/json。
     *
     * @param exception Spring MVC 已分类的异常，不用于拼接响应正文
     * @param body 框架准备的正文，可能为 null；本方法用固定错误结构替换
     * @param headers 框架提供的响应头
     * @param statusCode 框架决定的 HTTP 状态
     * @param request 当前请求上下文，仅交由父类处理响应提交状态
     * @return 固定错误响应；响应已经提交时按父类约定返回 null
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpHeaders responseHeaders = HttpHeaders.copyOf(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        LOGGER.warn("HTTP 请求被拒绝：stage=http_rejected, httpStatus={}, exceptionType={}",
                statusCode.value(), exception.getClass().getName());
        return super.handleExceptionInternal(exception, errorFor(statusCode), responseHeaders, statusCode, request);
    }

    /**
     * 将未被 Spring MVC 标准异常处理覆盖的异常转换为内部错误，不泄露服务实现细节。
     *
     * @param exception 未预期的处理异常，仅提取类型用于日志
     * @return HTTP 500 和固定错误码、说明
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<WeatherErrorVO> handleUnexpectedException(Exception exception) {
        LOGGER.error("天气 HTTP 处理失败：stage=http_failed, httpStatus=500, exceptionType={}",
                exception.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorFor(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * 按 HTTP 状态选择不包含用户输入和内部异常信息的固定错误描述。
     *
     * @param statusCode 实际返回的 HTTP 状态
     * @return 对应状态的错误码和固定说明
     */
    private WeatherErrorVO errorFor(HttpStatusCode statusCode) {
        if (statusCode.is5xxServerError()) {
            return new WeatherErrorVO("INTERNAL_ERROR", "天气任务处理失败，请稍后重试。");
        }
        return switch (statusCode.value()) {
            case 400 -> new WeatherErrorVO("INVALID_REQUEST", "请求体缺失、格式错误或字段校验失败。");
            case 405 -> new WeatherErrorVO("METHOD_NOT_ALLOWED", "请求方法不支持。");
            case 415 -> new WeatherErrorVO("UNSUPPORTED_MEDIA_TYPE", "请求媒体类型不支持，请使用 application/json。");
            default -> new WeatherErrorVO("REQUEST_REJECTED", "请求无法完成，请检查请求方式和内容。");
        };
    }
}

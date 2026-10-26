package example.weather.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为天气 HTTP 请求生成日志关联编号，覆盖参数拒绝、同步任务执行和响应结束。 */
@Component
public class WeatherRequestLoggingFilter extends OncePerRequestFilter {
    /** 只记录应用生成的编号、固定接口、HTTP 状态与耗时，不读取正文或请求头。 */
    private static final Logger LOG = LoggerFactory.getLogger(WeatherRequestLoggingFilter.class);

    /**
     * 仅跟踪天气提问接口，不为其他地址建立天气请求上下文。
     *
     * @param request 当前 Servlet 请求，仅检查路径，不写入日志
     * @return 当前请求是否不属于天气提问接口
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals(request.getContextPath() + "/api/weather/ask");
    }

    /**
     * 在同步处理期间传递 requestId，并在退出时恢复线程原有的 MDC 值，避免线程复用串号。
     * 编号始终由应用生成，不采用客户端提供的请求头。
     *
     * @param request 当前天气请求，不读取正文或凭据
     * @param response 当前响应，用于记录实际 HTTP 状态
     * @param filterChain 后续 MVC 和业务处理链
     * @throws ServletException 后续 Servlet 处理异常，记录类型后原样传播
     * @throws IOException 后续请求或响应发生 I/O 异常，记录类型后原样传播
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String previous = MDC.get("requestId");
        MDC.put("requestId", UUID.randomUUID().toString());
        long started = System.nanoTime();
        try {
            LOG.info("HTTP 请求开始：stage=http_request, endpoint=/api/weather/ask");
            filterChain.doFilter(request, response);
            LOG.atLevel(response.getStatus() >= 400 ? Level.WARN : Level.INFO).log(
                    "HTTP 请求结束：stage=http_response, httpStatus={}, elapsedMs={}",
                    response.getStatus(), (System.nanoTime() - started) / 1_000_000);
        } catch (IOException | ServletException | RuntimeException ex) {
            LOG.error("HTTP 请求异常退出：stage=http_failed, exceptionType={}, elapsedMs={}",
                    ex.getClass().getName(), (System.nanoTime() - started) / 1_000_000);
            throw ex;
        } finally {
            if (previous == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previous);
            }
        }
    }
}

package cn.tyron.llm;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * @description: 工具类
 * @author: chenzt
 * @create: 2026-03-27
 **/
public class SimpleTools {
    @Tool(name = "get_time", description = "获取当前时间")
    public String getTime(
            @ToolParam(name = "zone", description = "时区，例如：北京") String zone) {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

package example.agent.context;

import java.util.Locale;

/** 第 03 篇的逐项对照开关；只在显式上下文实验入口中使用。 */
public enum ContextMode {
    /** 原有系统规则和工具历史。 */
    BASELINE,
    /** 用任务流程重新组织规则。 */
    RULES,
    /** 同时加入两份 Skill 全文。 */
    EAGER,
    /** 只公布目录，通过 readSkill 加载正文。 */
    ONDEMAND,
    /** 在按需加载基础上追加由执行记录产生的状态。 */
    STATE,
    /** 在状态模式基础上对完整工具批次做确定性去重与裁剪。 */
    COMPACT;

    /**
     * 将命令行模式转换为枚举，不为非法输入选择默认模式。
     * @param value baseline、rules、eager、ondemand、state 或 compact
     * @return 已识别的模式
     */
    public static ContextMode parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    /** @return 当前模式是否提供 Skill 目录和读取工具 */
    public boolean usesSkills() {
        return this == EAGER || this == ONDEMAND || this == STATE || this == COMPACT;
    }

    /** @return 当前模式是否向模型提供应用维护的状态 */
    public boolean usesState() {
        return this == STATE || this == COMPACT;
    }
}

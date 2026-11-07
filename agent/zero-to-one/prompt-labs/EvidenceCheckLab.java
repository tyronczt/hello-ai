import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 实验 3-2：固定天气证据，逐项改变候选，观察核验程序如何判断。
 *
 * <p>无参数展示问题，--predict 配合 --reveal 展示实际核验，--self-check 执行固定回归案例。
 * verify 是不打印、不修改输入的核验函数，返回固定错误码。
 * 输入已经是 Java 值对象，不包含 JSON 解析、自然语言提取或真实模型评估。
 */
public class EvidenceCheckLab {
    /** 候选对“是否提供预报”的声明，不代表工具执行状态。 */
    enum Outcome {
        /** 候选声称提供目标日期的预报，应同时给出事实字段。 */
        ANSWER,
        /** 候选声称没有可用预报，应不附带天气事实。 */
        UNAVAILABLE
    }

    /**
     * 已取得的单日证据，或候选回答声称的单日事实；仅为进程内不可变教学值对象。
     *
     * <p>构造器校验单个字段的合法性；字段是否属于当前任务，由 verify 继续判断。
     * source 允许为空，以便构造“候选遗漏来源”或“证据没有来源”的对照。
     *
     * @param locationId 地点 ID，必须为正数
     * @param date 地点时区中的预报日期，不能为空
     * @param timezone 有效 IANA 时区，不能为空
     * @param source 来源标签；本实验中的“高德地图”只是合成夹具的标签
     * @param rainPercent 降水概率，单位 %；允许 0～100 或表示未知的 null
     */
    record Facts(long locationId, LocalDate date, String timezone, String source, Integer rainPercent) {
        /**
         * 校验事实值对象的输入边界；不把来源缺失或概率未知自动补成其他值。
         *
         * @param locationId 正数地点 ID
         * @param date 非空预报日期
         * @param timezone 非空、有效的时区标识
         * @param source 来源标签，允许为空以构造核验反例
         * @param rainPercent 0～100 的概率或表示未知的 null
         * @throws IllegalArgumentException 地点 ID 非正数或概率超出 0～100
         * @throws NullPointerException 日期或时区为空
         * @throws java.time.DateTimeException 时区标识无效
         */
        Facts {
            if (locationId <= 0) {
                throw new IllegalArgumentException("地点 ID 必须为正数。");
            }
            Objects.requireNonNull(date, "date");
            ZoneId.of(Objects.requireNonNull(timezone, "timezone"));
            if (rainPercent != null && (rainPercent < 0 || rainPercent > 100)) {
                throw new IllegalArgumentException("概率必须为 0～100 或 null。");
            }
        }
    }

    /**
     * 已经转成字段形式、尚待核验的候选回答。
     *
     * <p>保留“声称作答但事实为空”等不一致组合，交给核验器产生可观察的错误码。
     *
     * @param outcome 候选的处理状态，不能为空
     * @param facts 候选事实；UNAVAILABLE 时预期为 null
     */
    record Candidate(Outcome outcome, Facts facts) {
        /**
         * 只校验状态非空，不在构造阶段代替结果核验。
         *
         * @param outcome 非空候选状态
         * @param facts 候选声称的事实，允许为空以构造完整性反例
         * @throws NullPointerException 候选状态为空
         */
        Candidate {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /**
     * 先显示输入与问题，再按读者选择揭晓；自检与探索不共享预期答案。
     *
     * @param args --case、候选字段、--predict、--reveal 或单独的 --self-check
     */
    public static void main(String[] args) {
        if (args.length == 1 && "--self-check".equals(args[0])) {
            runSelfChecks();
            return;
        }
        Map<String, String> options = parseOptions(args);
        String caseName = options.getOrDefault("case", "fields");
        LocalDate target = LocalDate.of(2026, 10, 22);
        Facts evidence = new Facts(110000, target, "Asia/Shanghai", "高德地图", null);
        Facts claimed = evidence;
        String prose = "降水概率未提供。";
        if ("fields".equals(caseName)) {
            String probability = options.getOrDefault("probability", "null");
            claimed = new Facts(Long.parseLong(options.getOrDefault("location", "110000")),
                    target.plusDays(Integer.parseInt(options.getOrDefault("date-offset", "0"))),
                    options.getOrDefault("timezone", "Asia/Shanghai"),
                    "null".equals(options.get("source")) ? null : options.getOrDefault("source", "高德地图"),
                    "null".equals(probability) ? null : Integer.valueOf(probability));
            evidence = new Facts(Long.parseLong(options.getOrDefault("evidence-location", "110000")),
                    target, "Asia/Shanghai", "高德地图", null);
        } else if ("no-evidence".equals(caseName)) {
            evidence = null;
        } else if ("prose-conflict".equals(caseName)) {
            prose = "明天降水概率为 0%，可以放心不带伞。";
        } else if ("forged-evidence".equals(caseName)) {
            // 故意同时伪造依据与候选，展示来源字符串不能证明采集过程真实。
            evidence = new Facts(110000, target, "Asia/Shanghai", "虚构来源", 20);
            claimed = evidence;
            prose = "降水概率为 20%（与伪造字段一致，来源未获验证）。";
        }
        Outcome outcome = Outcome.valueOf(options.getOrDefault("outcome", "ANSWER"));
        Candidate candidate = new Candidate(outcome, outcome == Outcome.UNAVAILABLE ? null : claimed);
        System.out.println("实验 3-2：" + caseName + "；目标地点=110000；目标日期=" + target);
        System.out.println("证据：" + describeFacts(evidence));
        System.out.println("候选：" + candidate);
        System.out.println("候选附带文字：" + prose + "（此文字未传入 verify）");
        System.out.println("全部天气数据为合成教学输入，不代表真实预报。");
        System.out.println("问题：会接受还是拒绝？字段核验能证明附带文字与来源真实可靠吗？");
        System.out.println("用相同参数加 --predict=你的预测 --reveal 查看实际错误码。");
        if (!options.containsKey("reveal")) {
            return;
        }
        List<String> errors = verify(110000, target, evidence, candidate);
        System.out.println("我的预测：" + options.get("predict"));
        System.out.println("实际错误码=" + errors + "；核验结论=" + (errors.isEmpty() ? "接受候选字段" : "拒绝候选"));
        if ("prose-conflict".equals(caseName)) {
            System.out.println("盲区：verify 没有收到附带文字，字段通过不能证明自然语言正确。");
        } else if ("forged-evidence".equals(caseName)) {
            System.out.println("盲区：依据和候选一起伪造仍可字段相等；来源真实性需要受控采集轨迹。");
        }
        System.out.println("空错误码只证明当前字段检查通过；本轮探索不与作者预设答案做断言。");
    }

    /**
     * 校验探索选项和场景适用性，拒绝拼写错误、重复项及未记录预测的揭晓。
     *
     * @param args 不重复的 --名称=值，以及无值开关 --reveal
     * @return 已校验选项
     */
    private static Map<String, String> parseOptions(String[] args) {
        Set<String> fieldOptions = Set.of("location", "date-offset", "timezone", "source",
                "probability", "evidence-location");
        Map<String, String> options = new LinkedHashMap<>();
        for (String argument : args) {
            String[] pair = "--reveal".equals(argument) ? new String[]{"reveal", "true"}
                    : argument.startsWith("--") ? argument.substring(2).split("=", 2) : new String[0];
            if (pair.length != 2 || (!fieldOptions.contains(pair[0])
                    && !Set.of("case", "outcome", "predict", "reveal").contains(pair[0]))
                    || ("reveal".equals(pair[0]) && !"--reveal".equals(argument))
                    || pair[1].isBlank() || options.putIfAbsent(pair[0], pair[1]) != null) {
                throw new IllegalArgumentException("使用不重复的 --名称=值；自检请单独使用 --self-check。");
            }
        }
        String caseName = options.getOrDefault("case", "fields");
        if (!Set.of("fields", "no-evidence", "prose-conflict", "forged-evidence").contains(caseName)
                || (!"fields".equals(caseName) && fieldOptions.stream().anyMatch(options::containsKey))
                || !Set.of("ANSWER", "UNAVAILABLE").contains(options.getOrDefault("outcome", "ANSWER"))
                || (options.containsKey("reveal") && !options.containsKey("predict"))) {
            throw new IllegalArgumentException("检查 case、outcome 与字段选项；揭晓前用 --predict=文字 记录预测。");
        }
        return options;
    }

    /** 使用固定夹具执行字段、证据、完整性及输入边界的 13 项检查。 */
    private static void runSelfChecks() {
        LocalDate targetDate = LocalDate.of(2026, 10, 22);
        Facts evidence = new Facts(110000, targetDate, "Asia/Shanghai", "高德地图", null);
        int passedChecks = compareCandidateFields(evidence, targetDate);
        passedChecks += compareEvidenceAvailability(evidence, targetDate);
        passedChecks += compareCandidateCompleteness(evidence, targetDate);
        passedChecks += checkProbabilityBoundary(targetDate);
        System.out.println("PASS: " + passedChecks + " 个合成案例检查；不是模型评估成绩。");
    }

    /**
     * 固定证据与目标，每次只改变候选中的一个字段，并保留一个完全一致的基线。
     *
     * @param evidence 概率未知但其余字段适用的固定证据
     * @param targetDate 本次预报目标日期
     * @return 本组实际通过的案例数
     */
    private static int compareCandidateFields(Facts evidence, LocalDate targetDate) {
        System.out.println("\n步骤 1：固定证据，只改变候选字段。");
        int passedChecks = runCase("未知仍为未知", evidence, new Candidate(Outcome.ANSWER, evidence),
                targetDate, List.of(), "候选与证据一致；null 仍表示未知。");

        Facts zeroProbability = new Facts(110000, targetDate, "Asia/Shanghai", "高德地图", 0);
        passedChecks += runCase("未知被填成零", evidence, new Candidate(Outcome.ANSWER, zeroProbability),
                targetDate, List.of("PROBABILITY"), "0% 是确定数值，不能代替未知概率。");

        Facts wrongDate = new Facts(110000, targetDate.plusDays(1), "Asia/Shanghai", "高德地图", null);
        passedChecks += runCase("回答日期错位", evidence, new Candidate(Outcome.ANSWER, wrongDate),
                targetDate, List.of("DATE"), "相邻一天的预报不能冒充目标日期。");

        Facts wrongLocation = new Facts(310000, targetDate, "Asia/Shanghai", "高德地图", null);
        passedChecks += runCase("回答地点错位", evidence, new Candidate(Outcome.ANSWER, wrongLocation),
                targetDate, List.of("LOCATION"), "数值是否合理不能替代本次地点匹配。");

        Facts missingSource = new Facts(110000, targetDate, "Asia/Shanghai", null, null);
        passedChecks += runCase("遗漏来源", evidence, new Candidate(Outcome.ANSWER, missingSource),
                targetDate, List.of("SOURCE"), "候选遗漏了证据中的来源。");

        Facts wrongTimezone = new Facts(110000, targetDate, "UTC", "高德地图", null);
        passedChecks += runCase("时区错位", evidence, new Candidate(Outcome.ANSWER, wrongTimezone),
                targetDate, List.of("TIMEZONE"), "合法时区不一定是本次地点对应的时区。");
        return passedChecks;
    }

    /**
     * 区分“没有证据”与“有证据但不适用于目标”，避免用错误依据继续评判答案。
     *
     * @param evidence 原本适用的固定证据，也用于构造与新目标日期不匹配的情况
     * @param targetDate 原目标日期
     * @return 本组实际通过的案例数
     */
    private static int compareEvidenceAvailability(Facts evidence, LocalDate targetDate) {
        System.out.println("\n步骤 2：先判断有没有适用的证据。");
        int passedChecks = runCase("服务失败如实说明", null, new Candidate(Outcome.UNAVAILABLE, null),
                targetDate, List.of(), "未取得预报，候选也没有附带天气事实。");
        passedChecks += runCase("服务失败仍给预报", null, new Candidate(Outcome.ANSWER, evidence),
                targetDate, List.of("NO_EVIDENCE"), "没有取得预报，不能靠候选自称有结果。");
        passedChecks += runCase("证据不含目标日期", evidence, new Candidate(Outcome.ANSWER, evidence),
                targetDate.plusDays(1), List.of("EVIDENCE_MISMATCH"),
                "候选虽与证据一致，但二者都不属于当前目标日期。");
        Facts wrongLocation = new Facts(310000, targetDate, "Asia/Shanghai", "高德地图", null);
        passedChecks += runCase("证据地点不属于目标", wrongLocation,
                new Candidate(Outcome.ANSWER, wrongLocation), targetDate, List.of("EVIDENCE_MISMATCH"),
                "即使候选与证据相同，错误地点的证据也不能作为本次依据。");
        return passedChecks;
    }

    /**
     * 在证据适用的前提下，检查候选是否确实作答、是否带有所声称的事实。
     *
     * @param evidence 适用于目标日期的固定证据
     * @param targetDate 本次目标日期
     * @return 本组实际通过的案例数
     */
    private static int compareCandidateCompleteness(Facts evidence, LocalDate targetDate) {
        System.out.println("\n步骤 3：有适用证据后，检查候选是否完整。");
        int passedChecks = runCase("有适用证据却称不可用", evidence,
                new Candidate(Outcome.UNAVAILABLE, null), targetDate, List.of("MISSING_ANSWER"),
                "本实验要求交付已取得的字段；概率未知不等于整份预报不可用。");
        passedChecks += runCase("ANSWER 没有事实", evidence, new Candidate(Outcome.ANSWER, null),
                targetDate, List.of("MISSING_FACTS"), "声明提供预报，却没有可供核对的事实对象。");
        return passedChecks;
    }

    /**
     * 演示字段边界与结果核验的区别：非法概率在事实对象构造阶段就被拒绝。
     *
     * @param targetDate 合成夹具使用的日期
     * @return 非法概率按预期被拒绝时返回 1
     * @throws AssertionError 越界概率没有被拒绝
     */
    private static int checkProbabilityBoundary(LocalDate targetDate) {
        System.out.println("\n步骤 4：区分输入非法与候选不符合证据。");
        try {
            new Facts(110000, targetDate, "Asia/Shanghai", "高德地图", 101);
        } catch (IllegalArgumentException expected) {
            System.out.println("输入：降水概率=101%；构造阶段拒绝，还没有进入 verify。");
            System.out.println("案例检查通过：预期的输入拒绝已经发生。");
            return 1;
        }
        throw new AssertionError("应拒绝越界概率。");
    }

    /**
     * 先检查证据是否适用，再核对候选；返回错误码，不修改证据或候选。
     *
     * <p>没有证据时，只接受 UNAVAILABLE 且不附带事实的候选。
     * 证据不属于目标或缺少来源时，返回 EVIDENCE_MISMATCH，不继续比较候选字段。
     * 证据适用后，依次检查 MISSING_ANSWER、MISSING_FACTS，最后累计各字段差异。
     *
     * @param locationId 本次已确认的地点 ID，必须为正数
     * @param target 本次目标日期，不能为空
     * @param observed 已取得证据；null 表示没有取得预报，来源由受控采集层提供
     * @param candidate 已转成字段形式的候选回答，不能为空
     * @return 固定错误码列表；空列表只表示本实验覆盖的检查通过
     * @throws IllegalArgumentException 本次地点 ID 非正数
     * @throws NullPointerException 目标日期或候选为空
     */
    static List<String> verify(long locationId, LocalDate target, Facts observed, Candidate candidate) {
        if (locationId <= 0) {
            throw new IllegalArgumentException("地点 ID 无效。");
        }
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(candidate, "candidate");

        // 第一步：没有证据时，核对候选是否如实表示不可用，不能进入字段比较。
        if (observed == null) {
            boolean admitsUnavailable = candidate.outcome() == Outcome.UNAVAILABLE && candidate.facts() == null;
            return admitsUnavailable ? List.of() : List.of("NO_EVIDENCE");
        }

        // 第二步：证据必须属于当前地点、日期，并有来源；候选与错误证据一致也不能通过。
        if (observed.locationId() != locationId || !target.equals(observed.date())
                || observed.source() == null || observed.source().isBlank()) {
            return List.of("EVIDENCE_MISMATCH");
        }

        // 第三步：已有适用证据，再检查候选的处理状态和事实完整性。
        if (candidate.outcome() != Outcome.ANSWER) {
            return List.of("MISSING_ANSWER");
        }
        Facts claimedFacts = candidate.facts();
        if (claimedFacts == null) {
            return List.of("MISSING_FACTS");
        }

        // 第四步：字段错误可以同时出现，所以累计差异，而不是遇到第一个差异就返回。
        List<String> errors = new ArrayList<>();
        if (claimedFacts.locationId() != locationId) {
            errors.add("LOCATION");
        }
        if (!target.equals(claimedFacts.date())) {
            errors.add("DATE");
        }
        if (!observed.timezone().equals(claimedFacts.timezone())) {
            errors.add("TIMEZONE");
        }
        if (!observed.source().equals(claimedFacts.source())) {
            errors.add("SOURCE");
        }
        // Objects.equals 保留 null 与零的区别，避免拆箱时空指针或错误的默认值。
        if (!Objects.equals(observed.rainPercent(), claimedFacts.rainPercent())) {
            errors.add("PROBABILITY");
        }
        return List.copyOf(errors);
    }

    /**
     * 展示一个案例的输入、核验结论和解释，再检查程序行为是否符合预期。
     *
     * @param label 本案例要观察的变化
     * @param observed 固定或刻意改变的证据；null 表示未取得预报
     * @param candidate 待核验候选
     * @param target 该案例的目标日期
     * @param expected 预期错误码；空列表表示预期接受候选
     * @param explanation 对本案例结论的教学解释
     * @return 实际错误码与预期完全一致时返回 1
     * @throws AssertionError 核验程序的实际行为与预期不一致
     */
    private static int runCase(String label, Facts observed, Candidate candidate, LocalDate target,
                               List<String> expected, String explanation) {
        List<String> actual = verify(110000, target, observed, candidate);
        System.out.println("\n用例：" + label + "；目标日期=" + target);
        System.out.println("  证据：" + describeFacts(observed));
        System.out.println("  候选：状态=" + candidate.outcome() + "；" + describeFacts(candidate.facts()));
        System.out.println("  预期错误码=" + expected + "；实际错误码=" + actual);
        System.out.println("  核验结论：" + (actual.isEmpty() ? "接受候选" : "拒绝候选"));
        System.out.println("  解释：" + explanation);
        if (!actual.equals(expected)) {
            throw new AssertionError(label + "：预期 " + expected + "，实际 " + actual);
        }
        System.out.println("  案例检查通过：核验行为符合预期。");
        return 1;
    }

    /**
     * 用业务字段说明合成数据，把“没有事实对象”与“单个字段未知”分别展示。
     *
     * @param facts 证据或候选事实；允许 null
     * @return 只含本实验教学字段的可读文本
     */
    private static String describeFacts(Facts facts) {
        if (facts == null) {
            return "没有天气事实对象";
        }
        String probability = facts.rainPercent() == null ? "未知(null)" : facts.rainPercent() + "%";
        return "地点=" + facts.locationId() + "，日期=" + facts.date() + "，时区=" + facts.timezone()
                + "，来源=" + facts.source() + "，降水概率=" + probability;
    }
}

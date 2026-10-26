import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 合成天气证据的字段核验实验；不解析自然语言、不调用天气或模型服务。 */
public class EvidenceCheckLab {
    /** ANSWER 为提供预报，UNAVAILABLE 为未取得可用目标预报。 */
    enum Outcome { ANSWER, UNAVAILABLE }

    /**
     * 已取得的单日事实，或候选回答声称的单日事实。
     * @param locationId 地点 ID，需与本次检索选择一致
     * @param date 地点时区中的预报日期
     * @param timezone 服务返回的 IANA 时区
     * @param source 数据来源标识，候选可缺失以演示校验
     * @param rainPercent 降水概率，单位 %；null 为未知，不等于 0
     */
    record Facts(long locationId, LocalDate date, String timezone, String source, Integer rainPercent) {
        Facts {
            if (locationId <= 0) throw new IllegalArgumentException("地点 ID 必须为正数。");
            Objects.requireNonNull(date, "date");
            ZoneId.of(Objects.requireNonNull(timezone, "timezone"));
            if (rainPercent != null && (rainPercent < 0 || rainPercent > 100)) {
                throw new IllegalArgumentException("概率必须为 0～100 或 null。");
            }
        }
    }

    /** @param outcome 候选处理状态 @param facts 候选事实，UNAVAILABLE 时应为 null */
    record Candidate(Outcome outcome, Facts facts) {
        Candidate { Objects.requireNonNull(outcome, "outcome"); }
    }

    public static void main(String[] args) {
        LocalDate target = LocalDate.of(2026, 10, 22);
        Facts unknown = new Facts(110000, target, "Asia/Shanghai", "高德地图", null);
        check("未知仍为未知", unknown, new Candidate(Outcome.ANSWER, unknown), target, List.of());
        check("未知被填成零", unknown, new Candidate(Outcome.ANSWER,
                new Facts(110000, target, "Asia/Shanghai", "高德地图", 0)), target, List.of("PROBABILITY"));
        check("回答日期错位", unknown, new Candidate(Outcome.ANSWER,
                new Facts(110000, target.plusDays(1), "Asia/Shanghai", "高德地图", null)), target, List.of("DATE"));
        check("回答地点错位", unknown, new Candidate(Outcome.ANSWER,
                new Facts(310000, target, "Asia/Shanghai", "高德地图", null)), target, List.of("LOCATION"));
        check("遗漏来源", unknown, new Candidate(Outcome.ANSWER,
                new Facts(110000, target, "Asia/Shanghai", null, null)), target, List.of("SOURCE"));
        check("时区错位", unknown, new Candidate(Outcome.ANSWER,
                new Facts(110000, target, "UTC", "高德地图", null)), target, List.of("TIMEZONE"));
        check("服务失败如实说明", null, new Candidate(Outcome.UNAVAILABLE, null), target, List.of());
        check("服务失败仍给预报", null, new Candidate(Outcome.ANSWER, unknown), target, List.of("NO_EVIDENCE"));
        check("证据不含目标日期", unknown, new Candidate(Outcome.ANSWER, unknown),
                target.plusDays(1), List.of("EVIDENCE_MISMATCH"));
        check("有完整证据却称不可用", unknown, new Candidate(Outcome.UNAVAILABLE, null),
                target, List.of("MISSING_ANSWER"));
        check("ANSWER 没有事实", unknown, new Candidate(Outcome.ANSWER, null), target, List.of("MISSING_FACTS"));
        try {
            new Facts(110000, target, "Asia/Shanghai", "高德地图", 101);
            throw new AssertionError("应拒绝越界概率");
        } catch (IllegalArgumentException expected) {
            System.out.println("PASS: 越界概率被拒绝");
        }
        System.out.println("PASS: 12 个合成案例检查；不是模型评估成绩。");
    }

    /**
     * 先核对证据是否适用，再比较候选；source 由受控采集层提供。
     * @param locationId 本次已确认的地点 ID
     * @param target 目标日期
     * @param observed 已取得证据，null 表示未取得预报
     * @param candidate 已解析的候选回答
     * @return 有限的字段错误码；空列表只表示本实验覆盖的字段一致
     */
    static List<String> verify(long locationId, LocalDate target, Facts observed, Candidate candidate) {
        if (locationId <= 0) throw new IllegalArgumentException("地点 ID 无效。");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(candidate, "candidate");
        if (observed == null) {
            return candidate.outcome() == Outcome.UNAVAILABLE && candidate.facts() == null
                    ? List.of() : List.of("NO_EVIDENCE");
        }
        if (observed.locationId() != locationId || !target.equals(observed.date())
                || observed.source() == null || observed.source().isBlank()) {
            return List.of("EVIDENCE_MISMATCH");
        }
        if (candidate.outcome() != Outcome.ANSWER) return List.of("MISSING_ANSWER");
        Facts claimed = candidate.facts();
        if (claimed == null) return List.of("MISSING_FACTS");
        List<String> errors = new ArrayList<>();
        if (claimed.locationId() != locationId) errors.add("LOCATION");
        if (!target.equals(claimed.date())) errors.add("DATE");
        if (!observed.timezone().equals(claimed.timezone())) errors.add("TIMEZONE");
        if (!observed.source().equals(claimed.source())) errors.add("SOURCE");
        if (!Objects.equals(observed.rainPercent(), claimed.rainPercent())) errors.add("PROBABILITY");
        return List.copyOf(errors);
    }

    private static void check(String label, Facts observed, Candidate candidate,
                              LocalDate target, List<String> expected) {
        List<String> actual = verify(110000, target, observed, candidate);
        if (!actual.equals(expected)) throw new AssertionError(label + ": " + actual);
        System.out.println("PASS: " + label + " -> " + actual);
    }
}

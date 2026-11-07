import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 实验 3-1：固定候选分数，分别观察温度、top-p 和贪心选择。
 *
 * <p>无参数先展示实验问题；填写预测并加 --reveal 后观察结果。
 * --self-check 单独运行固定输入的四组工程检查；探索参数不改变这些预期。
 * A、B、C 只是三个候选的位置标签；分数是人工输入，不是某次模型调用的结果。
 * 本程序计算概率分布和最高分位置，不执行随机抽样，不评价真实模型的回答质量。
 */
public class SamplingLab {
    /** 判断概率归一化是否成立的浮点误差容限，不是模型质量阈值。 */
    private static final double PROBABILITY_TOLERANCE = 1e-12;

    /**
     * 分开处理读者探索与固定自检，防止合法参数变化触发旧断言。
     *
     * @param args --logits、--temperature、--top-p、--predict、--reveal 或单独的 --self-check
     */
    public static void main(String[] args) {
        if (args.length == 1 && "--self-check".equals(args[0])) {
            runSelfChecks();
            return;
        }
        Map<String, String> options = parseOptions(args);
        double[] logits = Arrays.stream(options.getOrDefault("logits", "2,1,0").split(",", -1))
                .mapToDouble(Double::parseDouble).toArray();
        if (logits.length != 3) {
            throw new IllegalArgumentException("本实验固定三个候选，请提供三个逗号分隔的分数。");
        }
        double temperature = Double.parseDouble(options.getOrDefault("temperature", "1"));
        double threshold = Double.parseDouble(options.getOrDefault("top-p", "0.8"));
        // 即使暂不揭晓，也先校验输入；非法条件不能形成有效实验。
        double[] baseline = softmax(logits, 1);
        double[] adjusted = softmax(logits, temperature);
        double[] filtered = topP(adjusted, threshold);
        System.out.println("实验 3-1：固定分数，只改变选择参数。");
        System.out.println("输入 logits=" + Arrays.toString(logits)
                + "；T=" + temperature + "；top-p=" + threshold);
        System.out.println("候选语义仅为教学假设：A=编造恢复时间，B=保留未知，C=请求补充材料。");
        System.out.println("问题：与 T=1 的基线相比，A 的概率怎样变？筛选后剩几个候选？");
        System.out.println("先记录预测，再用相同参数加 --predict=你的预测 --reveal 揭晓。");
        if (!options.containsKey("reveal")) {
            return;
        }
        System.out.println("我的预测：" + options.get("predict"));
        printDistribution("T=1 基线（未筛选）", baseline);
        printDistribution("所选温度（未筛选）", adjusted);
        printDistribution("所选温度 + top-p", filtered);
        long kept = Arrays.stream(filtered).filter(probability -> probability > 0).count();
        System.out.printf(Locale.ROOT, "A 的概率变化=%+.2f 个百分点；保留候选=%d；贪心索引=%d%n",
                (adjusted[0] - baseline[0]) * 100, kept, argmax(logits));
        System.out.println("并列最高分时，argmax 选择原顺序中的第一项。");
        System.out.println("观察的是分布，不是模型实测质量；集中选择错误候选仍然会错。");
    }

    /**
     * 校验教学选项；要求揭晓前有书面预测，未知或重复选项直接拒绝。
     *
     * @param args 不重复的 --名称=值，以及无值开关 --reveal
     * @return 已校验选项
     */
    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String argument : args) {
            String[] pair = "--reveal".equals(argument) ? new String[]{"reveal", "true"}
                    : argument.startsWith("--") ? argument.substring(2).split("=", 2) : new String[0];
            if (pair.length != 2 || !Set.of("logits", "temperature", "top-p", "predict", "reveal").contains(pair[0])
                    || ("reveal".equals(pair[0]) && !"--reveal".equals(argument))
                    || pair[1].isBlank() || options.putIfAbsent(pair[0], pair[1]) != null) {
                throw new IllegalArgumentException("使用不重复的 --名称=值；自检请单独使用 --self-check。");
            }
        }
        if (options.containsKey("reveal") && !options.containsKey("predict")) {
            throw new IllegalArgumentException("揭晓前请用 --predict=文字 记录预测。");
        }
        return options;
    }

    /** 使用独立的固定输入执行 10 项回归检查，不读取探索参数。 */
    private static void runSelfChecks() {
        double[] logits = {2, 1, 0};
        int passedChecks = compareTemperatures(logits);
        passedChecks += compareTopP(logits);
        passedChecks += compareGreedyAndScoreShift(logits);
        passedChecks += checkInputBoundaries(logits);
        System.out.println("PASS: " + passedChecks + " 项数值与边界检查；不代表任何模型的准确率。");
    }

    /**
     * 固定 logits，只改变正温度，比较分布集中程度与最高分位置。
     *
     * @param logits 人工设定的非空、有限候选分数
     * @return 实际通过的检查数
     */
    private static int compareTemperatures(double[] logits) {
        System.out.println("\n步骤 1：只改变温度，保持候选分数不变。");
        double[] lowTemperature = softmax(logits, 0.5);
        double[] baseline = softmax(logits, 1);
        double[] highTemperature = softmax(logits, 2);

        printDistribution("T=0.5", lowTemperature);
        printDistribution("T=1.0（基线）", baseline);
        printDistribution("T=2.0", highTemperature);
        System.out.println("观察：A 始终排第一；温度越低，分配给 A 的概率越集中。");

        int passedChecks = check(isNormalized(baseline), "基线概率之和为 1");
        passedChecks += check(lowTemperature[0] > baseline[0] && baseline[0] > highTemperature[0],
                "降低温度提高 A 的概率，升高温度降低 A 的概率");
        passedChecks += check(argmax(lowTemperature) == 0 && argmax(highTemperature) == 0,
                "正温度缩放不改变这组固定分数的最高分位置");
        return passedChecks;
    }

    /**
     * 固定温度为 1，比较 top-p 的候选筛选、重新归一化和两个边界设置。
     *
     * @param logits 与温度实验相同的人工分数
     * @return 实际通过的检查数
     */
    private static int compareTopP(double[] logits) {
        System.out.println("\n步骤 2：固定 T=1，只改变 top-p。");
        double[] baseline = softmax(logits, 1);
        double[] filtered = topP(baseline, 0.8);

        printDistribution("筛选前", baseline);
        System.out.printf(Locale.ROOT, "累计 A=%.4f；累计 A+B=%.4f；阈值=0.8000%n",
                baseline[0], baseline[0] + baseline[1]);
        printDistribution("top-p=0.8", filtered);
        printDistribution("top-p=0.1", topP(baseline, 0.1));
        printDistribution("top-p=1.0", topP(baseline, 1));
        System.out.println("观察：0.8 保留 A、B 并排除 C，再把 A、B 的概率重新归一化。");

        int passedChecks = check(filtered[0] > 0 && filtered[1] > 0 && filtered[2] == 0
                && isNormalized(filtered), "保留达到阈值的最小前缀，并重新归一化");
        passedChecks += check(topP(baseline, 0.1)[0] == 1, "阈值很小时仍至少保留最高概率候选");
        passedChecks += check(Arrays.equals(topP(baseline, 1), baseline), "top-p=1 保留完整分布");
        return passedChecks;
    }

    /**
     * 区分候选分数变化、贪心选择及不改变 softmax 结果的共同分数平移。
     *
     * @param logits 原始人工分数
     * @return 实际通过的检查数
     */
    private static int compareGreedyAndScoreShift(double[] logits) {
        System.out.println("\n步骤 3：直接选最高分，再分别改变相对分数与共同偏移量。");
        double[] changedLogits = {0, 2, 1};
        double[] shiftedLogits = {10002, 10001, 10000};

        System.out.println(Arrays.toString(logits) + " 的贪心结果：A（索引 " + argmax(logits) + "）");
        System.out.println(Arrays.toString(changedLogits) + " 的贪心结果：B（索引 "
                + argmax(changedLogits) + "）");
        printDistribution("原分数，T=1", softmax(logits, 1));
        printDistribution("各项同时加 10000，T=1", softmax(shiftedLogits, 1));
        System.out.println("观察：改变相对分数会影响贪心结果；共同平移分数不改变 softmax 概率。");

        return check(Arrays.equals(softmax(shiftedLogits, 1), softmax(logits, 1)),
                "减去最大值的稳定计算保留共同平移前后的结果");
    }

    /**
     * 展示公式不接受哪些输入，避免将参数拒绝误解为一次有效的概率计算。
     *
     * @param logits 用于构造非法温度请求的正常分数
     * @return 实际通过的拒绝检查数
     */
    private static int checkInputBoundaries(double[] logits) {
        System.out.println("\n步骤 4：检查公式与输入边界。");
        int passedChecks = rejects(() -> softmax(logits, 0), "T=0 不直接代入 softmax；贪心选择单独处理");
        passedChecks += rejects(() -> softmax(new double[]{Double.NaN}, 1), "拒绝非有限候选分数");
        passedChecks += rejects(() -> topP(new double[]{0.6, 0.6}, 0.8), "拒绝总和不为 1 的概率输入");
        return passedChecks;
    }

    /**
     * 计算 softmax(logits / temperature)，返回与输入位置一一对应的新概率数组。
     *
     * <p>实现先减去最大分数：指数的自变量不大于零，避免直接计算大正数的指数。
     * 每项减去相同值不会改变归一化后的数学结果。
     *
     * @param logits 非空数组，各分数必须为有限值
     * @param temperature 有限正数；本方法不把 0 解释成某个服务的贪心参数
     * @return 归一化概率，不修改原数组
     * @throws IllegalArgumentException 分数为空、包含非有限值，或温度不是有限正数
     */
    static double[] softmax(double[] logits, double temperature) {
        if (!Double.isFinite(temperature) || temperature <= 0) {
            throw new IllegalArgumentException("temperature 必须为有限正数；贪心选择单独处理。");
        }
        double maximumLogit = logits[argmax(logits)];
        double[] weights = new double[logits.length];
        for (int index = 0; index < logits.length; index++) {
            double scaledDifference = (logits[index] - maximumLogit) / temperature;
            weights[index] = Math.exp(scaledDifference);
        }

        double totalWeight = Arrays.stream(weights).sum();
        double[] probabilities = new double[weights.length];
        for (int index = 0; index < weights.length; index++) {
            probabilities[index] = weights[index] / totalWeight;
        }
        return probabilities;
    }

    /**
     * 返回最大值首次出现的位置，对应本实验单独展示的贪心选择。
     *
     * @param values 非空、元素均为有限值的数组，可以是分数或概率
     * @return 最大值的零起始索引；并列时保留原数组中先出现的一项
     * @throws IllegalArgumentException 数组为空或包含非有限值
     */
    static int argmax(double[] values) {
        requireFiniteValues(values);
        int bestIndex = 0;
        for (int index = 1; index < values.length; index++) {
            if (values[index] > values[bestIndex]) {
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    /**
     * 按概率降序保留累计概率达到阈值的最小前缀，并对保留项重新归一化。
     *
     * <p>排序的是索引，不移动输入；返回数组仍与原候选顺序一致。
     * 概率并列时使用原顺序，threshold=1 时直接复制完整分布。
     *
     * @param probabilities 非空、非负、有限且总和在误差容限内为 1 的概率数组
     * @param threshold top-p 阈值，必须在 (0, 1] 内
     * @return 与输入等长的新数组，被排除候选的概率为零
     * @throws IllegalArgumentException 阈值不合法或输入不是有效概率分布
     */
    static double[] topP(double[] probabilities, double threshold) {
        if (!Double.isFinite(threshold) || threshold <= 0 || threshold > 1) {
            throw new IllegalArgumentException("top-p 必须在 (0, 1] 内。");
        }
        requireFiniteValues(probabilities);
        for (double probability : probabilities) {
            if (probability < 0) {
                throw new IllegalArgumentException("输入必须是归一化概率分布。");
            }
        }
        if (Math.abs(Arrays.stream(probabilities).sum() - 1) > PROBABILITY_TOLERANCE) {
            throw new IllegalArgumentException("输入必须是归一化概率分布。");
        }
        if (threshold == 1) {
            return probabilities.clone();
        }

        Integer[] sortedIndices = new Integer[probabilities.length];
        for (int index = 0; index < probabilities.length; index++) {
            sortedIndices[index] = index;
        }
        Arrays.sort(sortedIndices,
                (leftIndex, rightIndex) -> Double.compare(probabilities[rightIndex], probabilities[leftIndex]));

        double[] keptProbabilities = new double[probabilities.length];
        double keptTotal = 0;
        for (int index : sortedIndices) {
            keptProbabilities[index] = probabilities[index];
            keptTotal += probabilities[index];
            if (keptTotal >= threshold) {
                break;
            }
        }
        for (int index = 0; index < keptProbabilities.length; index++) {
            keptProbabilities[index] /= keptTotal;
        }
        return keptProbabilities;
    }

    /**
     * 校验用于比较或计算的分数/概率数组，不修改输入。
     *
     * @param values 待检查的数组
     * @throws IllegalArgumentException 数组为空或任一元素不是有限值
     */
    private static void requireFiniteValues(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("候选不能为空。");
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("候选值必须有限。");
            }
        }
    }

    /**
     * 判断概率总和是否在浮点误差容限内为 1；元素合法性由调用方另行检查。
     *
     * @param probabilities 已完成元素合法性检查的概率数组
     * @return 总和与 1 的差值小于容限时为 true
     */
    private static boolean isNormalized(double[] probabilities) {
        return Math.abs(Arrays.stream(probabilities).sum() - 1) < PROBABILITY_TOLERANCE;
    }

    /**
     * 以候选标签打印概率，便于把数组位置与实验叙述对应起来。
     *
     * @param label 本行的实验条件
     * @param probabilities 本实验 A、B、C 对应的三个概率
     */
    private static void printDistribution(String label, double[] probabilities) {
        System.out.printf(Locale.ROOT, "%-24s A=%.4f  B=%.4f  C=%.4f%n",
                label, probabilities[0], probabilities[1], probabilities[2]);
    }

    /**
     * 核对一项计算性质并打印其含义，不依赖 JVM 的 -ea 开关。
     *
     * @param passed 计算是否满足该项预期
     * @param label 本项检查的含义
     * @return 检查通过时返回 1，用于累计实际检查数量
     * @throws AssertionError 检查不通过
     */
    private static int check(boolean passed, String label) {
        if (!passed) {
            throw new AssertionError(label);
        }
        System.out.println("检查通过：" + label);
        return 1;
    }

    /**
     * 确认非法输入被拒绝；拒绝是本项检查的预期结果。
     *
     * @param action 应抛出 IllegalArgumentException 的操作
     * @param label 拒绝该输入的原因
     * @return 操作按预期被拒绝时返回 1
     * @throws AssertionError 操作未抛出预期异常
     */
    private static int rejects(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            System.out.println("检查通过：输入已拒绝；" + label);
            return 1;
        }
        throw new AssertionError(label);
    }
}

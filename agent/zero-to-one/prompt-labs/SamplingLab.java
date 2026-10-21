import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.IntStream;

/** 人工 logits 的采样分布实验；不加载模型、不测量提示词质量。 */
public class SamplingLab {
    public static void main(String[] args) {
        double[] logits = {2, 1, 0};
        for (double temperature : new double[]{0.5, 1, 2}) {
            double[] probabilities = softmax(logits, temperature);
            System.out.printf(Locale.ROOT, "T=%.1f -> [%.4f, %.4f, %.4f]%n",
                    temperature, probabilities[0], probabilities[1], probabilities[2]);
        }
        double[] probabilities = softmax(logits, 1);
        System.out.println("top_p=0.8 -> " + Arrays.toString(topP(probabilities, 0.8)));
        System.out.println("greedy index=" + argmax(logits));
        System.out.println("changed logits [0, 2, 1], greedy index=" + argmax(new double[]{0, 2, 1}));

        check(Math.abs(Arrays.stream(probabilities).sum() - 1) < 1e-12, "归一化");
        check(softmax(logits, 0.5)[0] > probabilities[0]
                && probabilities[0] > softmax(logits, 2)[0], "温度改变集中程度");
        check(argmax(softmax(logits, 0.5)) == 0 && argmax(softmax(logits, 2)) == 0,
                "相同 logits 下正温度不改变排序");
        double[] filtered = topP(probabilities, 0.8);
        check(filtered[0] > 0 && filtered[1] > 0 && filtered[2] == 0
                && Math.abs(Arrays.stream(filtered).sum() - 1) < 1e-12, "top-p 保留并归一化最小前缀");
        check(topP(probabilities, 0.1)[0] == 1, "top-p 至少保留一个候选");
        check(Arrays.equals(topP(probabilities, 1), probabilities), "top-p=1 保留全量");
        check(Arrays.equals(softmax(new double[]{10002, 10001, 10000}, 1), probabilities),
                "减去最大值避免指数溢出");
        rejects(() -> softmax(logits, 0), "T=0 不能直接代入 softmax");
        rejects(() -> softmax(new double[]{Double.NaN}, 1), "拒绝非有限 logits");
        rejects(() -> topP(new double[]{0.6, 0.6}, 0.8), "拒绝非概率分布");
        System.out.println("PASS: 10 个数值与边界检查；不代表任何模型的准确率。");
    }

    static double[] softmax(double[] logits, double temperature) {
        if (!Double.isFinite(temperature) || temperature <= 0) {
            throw new IllegalArgumentException("temperature 必须为有限正数；贪心选择单独处理。");
        }
        double max = logits[argmax(logits)];
        double[] values = Arrays.stream(logits).map(z -> Math.exp((z - max) / temperature)).toArray();
        double total = Arrays.stream(values).sum();
        return Arrays.stream(values).map(v -> v / total).toArray();
    }

    static int argmax(double[] values) {
        if (values == null || values.length == 0) throw new IllegalArgumentException("候选不能为空。");
        int best = 0;
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i])) throw new IllegalArgumentException("候选值必须有限。");
            if (values[i] > values[best]) best = i;
        }
        return best;
    }

    static double[] topP(double[] probabilities, double threshold) {
        if (!Double.isFinite(threshold) || threshold <= 0 || threshold > 1) {
            throw new IllegalArgumentException("top-p 必须在 (0, 1] 内。");
        }
        argmax(probabilities);
        if (Arrays.stream(probabilities).anyMatch(p -> p < 0)
                || Math.abs(Arrays.stream(probabilities).sum() - 1) > 1e-12) {
            throw new IllegalArgumentException("输入必须是归一化概率分布。");
        }
        if (threshold == 1) return probabilities.clone();
        Integer[] order = IntStream.range(0, probabilities.length).boxed()
                .sorted(Comparator.<Integer>comparingDouble(i -> probabilities[i]).reversed())
                .toArray(Integer[]::new);
        double[] kept = new double[probabilities.length];
        double sum = 0;
        for (int i : order) {
            kept[i] = probabilities[i];
            sum += probabilities[i];
            if (sum >= threshold) break;
        }
        for (int i = 0; i < kept.length; i++) kept[i] /= sum;
        return kept;
    }

    private static void check(boolean passed, String label) {
        if (!passed) throw new AssertionError(label);
    }

    private static void rejects(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label);
    }
}

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 两层、单头因果注意力的数值实验；固定教学向量，无模型权重、位置编码或外部服务。 */
public class KvCacheLab {
    private static final int LAYERS = 2;
    private static final double[][] WQ = {{1.0, 0.2}, {0.1, 0.9}};
    private static final double[][] WK = {{0.8, 0.1}, {0.3, 1.0}};
    private static final double[][] WV = {{1.0, 0.4}, {0.2, 0.7}};

    public static void main(String[] args) {
        double[][] tokens = {{1, 0}, {0, 1}, {1, 1}, {0.5, 1.5}};
        Cache cache = new Cache();
        long fullProjections = 0;
        for (int length = 1; length <= tokens.length; length++) {
            double[][] full = full(Arrays.copyOf(tokens, length));
            double[] incremental = cache.append(tokens[length - 1]);
            requireClose(full[length - 1], incremental);
            fullProjections += 2L * LAYERS * length;
        }

        double[][] beforeAppend = full(Arrays.copyOf(tokens, 3));
        double[][] afterAppend = full(tokens);
        for (int i = 0; i < beforeAppend.length; i++) {
            requireClose(beforeAppend[i], afterAppend[i]);
        }

        double[][] changed = {{3, -1}, tokens[1], tokens[2], tokens[3]};
        double[] stale = cache.last;
        double[] expected = full(changed)[changed.length - 1];
        if (distance(stale, expected) < 1e-6) {
            throw new AssertionError("修改前缀的反例没有产生差异");
        }
        Cache rebuilt = new Cache();
        for (double[] token : changed) rebuilt.append(token);
        requireClose(expected, rebuilt.last);

        if (fullProjections != 40 || cache.kvProjections != 16) {
            throw new AssertionError("K/V 投影计数不符合固定实验");
        }
        System.out.println("PASS: incremental output equals full recomputation");
        System.out.println("PASS: appending preserves earlier causal outputs");
        System.out.println("PASS: edited prefix needs invalidation and recomputation");
        System.out.printf("K/V vector projections: full=%d, cached=%d%n",
                fullProjections, cache.kvProjections);
        System.out.println("Counts cover this toy attention only; no latency or provider cache measured.");
    }

    /** 完整前缀重算：每一层计算所有位置，然后将输出作为下一层输入。 */
    private static double[][] full(double[][] tokens) {
        double[][] hidden = tokens;
        for (int layer = 0; layer < LAYERS; layer++) {
            List<double[]> keys = new ArrayList<>();
            List<double[]> values = new ArrayList<>();
            for (double[] x : hidden) {
                keys.add(project(x, WK));
                values.add(project(x, WV));
            }
            double[][] next = new double[hidden.length][];
            for (int t = 0; t < hidden.length; t++) {
                next[t] = attend(project(hidden[t], WQ), keys, values, t + 1);
            }
            hidden = next;
        }
        return hidden;
    }

    /** 每个实例只处理一条追加式序列；它不是并发安全的跨请求缓存。 */
    private static final class Cache {
        private final List<List<double[]>> keys = new ArrayList<>();
        private final List<List<double[]>> values = new ArrayList<>();
        private long kvProjections;
        private double[] last;

        private Cache() {
            for (int layer = 0; layer < LAYERS; layer++) {
                keys.add(new ArrayList<>());
                values.add(new ArrayList<>());
            }
        }

        private double[] append(double[] token) {
            double[] hidden = token;
            for (int layer = 0; layer < LAYERS; layer++) {
                double[] q = project(hidden, WQ);
                keys.get(layer).add(project(hidden, WK));
                values.get(layer).add(project(hidden, WV));
                kvProjections += 2;
                hidden = attend(q, keys.get(layer), values.get(layer), keys.get(layer).size());
            }
            last = hidden;
            return hidden;
        }
    }

    private static double[] project(double[] x, double[][] weights) {
        return new double[] {
                x[0] * weights[0][0] + x[1] * weights[1][0],
                x[0] * weights[0][1] + x[1] * weights[1][1]
        };
    }

    /** 只读取当前位置及其之前的 K/V；减去最大分数以稳定 softmax。 */
    private static double[] attend(double[] q, List<double[]> keys, List<double[]> values, int visible) {
        double[] scores = new double[visible];
        for (int i = 0; i < visible; i++) {
            double[] k = keys.get(i);
            scores[i] = (q[0] * k[0] + q[1] * k[1]) / Math.sqrt(q.length);
        }
        double max = Arrays.stream(scores).max().orElseThrow();
        double sum = 0;
        for (int i = 0; i < visible; i++) sum += scores[i] = Math.exp(scores[i] - max);
        double[] result = new double[2];
        for (int i = 0; i < visible; i++) {
            double[] v = values.get(i);
            result[0] += scores[i] / sum * v[0];
            result[1] += scores[i] / sum * v[1];
        }
        return result;
    }

    private static double distance(double[] a, double[] b) {
        return Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1]));
    }

    private static void requireClose(double[] expected, double[] actual) {
        if (!Double.isFinite(actual[0]) || !Double.isFinite(actual[1])
                || distance(expected, actual) > 1e-12) {
            throw new AssertionError("全量与增量结果不一致");
        }
    }
}

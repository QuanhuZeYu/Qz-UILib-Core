package club.heiqi.uilib.ui.scene.motion;

import club.heiqi.uilib.testing.TestApplication;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** 用独立有理数期望与有限距离往返保护拾取曲线，不依赖宿主或墙钟。 */
public final class ElasticMappingChecks extends TestApplication {
    private static final MathContext ORACLE_PRECISION = new MathContext(80, RoundingMode.HALF_EVEN);
    private static final BigDecimal ORACLE_RESISTANCE = new BigDecimal(.55);
    private static final BigDecimal MAXIMUM_INPUT = new BigDecimal(Double.MAX_VALUE);

    @Override protected void runTests() throws Exception {
        scenario("弹性正反求的独立数值与零点", this::knownPairs);
        scenario("独立高精度正反求覆盖混合极端比例", this::mixedRatios);
        scenario("双向拾取、上限附近与有限边界投影", this::pickup);
        scenario("三种策略与极端有限上限不产生非有限值", this::finiteLimits);
    }

    private void knownPairs() {
        ElasticOptions options = new ElasticOptions(32, MotionSpec.instant());
        // 期望来自有理数 352/27、352/19、1056/49 与 88/15，不由被测曲线生成。
        pair(options, MotionPolicy.NORMAL, 40, 13.037037037037037);
        pair(options, MotionPolicy.NORMAL, 80, 18.526315789473684);
        pair(options, MotionPolicy.NORMAL, 120, 21.551020408163265);
        pair(options, MotionPolicy.REDUCED, 40, 5.866666666666667);
        near(options.displacement(1e-18, MotionPolicy.NORMAL), 5.5e-19, "小值被零点相消");
        near(options.overshoot(16, MotionPolicy.NORMAL), 58.18181818181818, "半幅反求");
        near(options.overshoot(8, MotionPolicy.NORMAL), 19.393939393939394, "四分之一幅反求");
        require(options.displacement(0, MotionPolicy.NORMAL) == 0 && options.overshoot(0, MotionPolicy.NORMAL) == 0,
                "曲线零点错误");
        require(Double.doubleToRawLongBits(options.displacement(-0d, MotionPolicy.NORMAL)) == Long.MIN_VALUE
                && Double.doubleToRawLongBits(options.overshoot(-0d, MotionPolicy.NORMAL)) == Long.MIN_VALUE,
                "有符号零丢失方向");
    }

    private void mixedRatios() {
        double half = new ElasticOptions(Double.MAX_VALUE, MotionSpec.instant()).displacement(.5, MotionPolicy.NORMAL);
        double tiny = new ElasticOptions(1e300, MotionSpec.instant()).displacement(1e-20, MotionPolicy.NORMAL);
        System.out.println("独立前向 max=Double.MAX_VALUE raw=.5 actual=" + half + " expected=.275");
        System.out.println("独立前向 max=1e300 raw=1e-20 actual=" + tiny + " expected=5.5e-21");
        oracleNear(half, .275, "最大上限与普通输入被中间比例溢出归零");
        oracleNear(tiny, 5.5e-21, "大上限与小输入被中间比例溢出归零");
        int forwardCases = 0, inverseCases = 0;
        double[] samples = {0, Double.MIN_VALUE, 2 * Double.MIN_VALUE, Double.MIN_NORMAL, 1e-300, 1e-20, .5, 1, 32, 1e300, Double.MAX_VALUE};
        for (double maximum : new double[] {Double.MIN_VALUE, 2 * Double.MIN_VALUE, 3 * Double.MIN_VALUE,
                Double.MIN_NORMAL, 1e-300, 32, 1e300, Double.MAX_VALUE}) {
            ElasticOptions options = new ElasticOptions(maximum, MotionSpec.instant());
            for (MotionPolicy policy : MotionPolicy.values()) {
                for (double amount : samples) {
                    for (int direction : new int[] {-1, 1}) {
                        double signed = direction * amount;
                        String context = "maximum=" + maximum + ", policy=" + policy + ", input=" + signed;
                        oracleNear(options.displacement(signed, policy), forwardOracle(maximum, signed, policy), "独立正向 " + context);
                        oracleNear(options.overshoot(signed, policy), inverseOracle(maximum, signed, policy), "独立反向 " + context);
                        forwardCases++;
                        inverseCases++;
                    }
                }
                double limit = oracleLimit(maximum, policy).doubleValue();
                for (double shown : new double[] {limit / 2, Math.nextDown(limit), limit}) {
                    for (int direction : new int[] {-1, 1}) {
                        double signed = direction * shown;
                        oracleNear(options.overshoot(signed, policy), inverseOracle(maximum, signed, policy),
                                "独立反向上限附近 maximum=" + maximum + ", policy=" + policy + ", shown=" + signed);
                        inverseCases++;
                    }
                }
            }
        }
        System.out.println("独立高精度 oracle 正向=" + forwardCases + " 反向=" + inverseCases);
    }

    private static BigDecimal oracleLimit(double maximum, MotionPolicy policy) {
        return new BigDecimal(policy == MotionPolicy.OFF ? 0 : maximum * (policy == MotionPolicy.REDUCED ? .25 : 1));
    }

    private static double forwardOracle(double maximum, double raw, MotionPolicy policy) {
        BigDecimal limit = oracleLimit(maximum, policy);
        if (limit.signum() == 0) return 0;
        // 用无限指数范围直接计算原有理式，不经过生产代码的比例缩放或往返结果。
        BigDecimal scaled = new BigDecimal(Math.abs(raw)).multiply(ORACLE_RESISTANCE);
        double amount = limit.multiply(scaled).divide(limit.add(scaled), ORACLE_PRECISION).doubleValue();
        return Math.copySign(amount, raw);
    }

    private static double inverseOracle(double maximum, double shown, MotionPolicy policy) {
        BigDecimal limit = oracleLimit(maximum, policy);
        if (limit.signum() == 0) return 0;
        BigDecimal distance = new BigDecimal(Math.abs(shown));
        BigDecimal remaining = limit.subtract(distance);
        if (remaining.signum() <= 0) return Math.copySign(Double.MAX_VALUE, shown);
        BigDecimal input = distance.multiply(limit).divide(remaining.multiply(ORACLE_RESISTANCE), ORACLE_PRECISION);
        return Math.copySign(input.min(MAXIMUM_INPUT).doubleValue(), shown);
    }

    private static void oracleNear(double actual, double expected, String message) {
        // 不能以正反求同时归零的自洽结果跳过；只有独立 oracle 的最终舍入为零才允许零。
        require(expected == 0 ? actual == 0 : actual != 0, message + ": actual=" + actual + ", expected=" + expected);
        near(actual, expected, message);
    }

    private void pair(ElasticOptions options, MotionPolicy policy, double raw, double expected) {
        for (int direction : new int[] {-1, 1}) {
            near(options.displacement(direction * raw, policy), direction * expected, "正向独立期望");
            near(options.overshoot(direction * expected, policy), direction * raw, "反向独立期望");
        }
    }

    private void pickup() {
        ElasticOptions options = new ElasticOptions(32, MotionSpec.instant());
        for (MotionPolicy policy : new MotionPolicy[] {MotionPolicy.NORMAL, MotionPolicy.REDUCED}) {
            double limit = policy == MotionPolicy.NORMAL ? 32 : 8;
            for (double visible : new double[] {1e-18, .125, 1, limit / 2, limit - .25, Math.nextDown(limit)}) {
                for (int direction : new int[] {-1, 1}) {
                    double input = options.overshoot(direction * visible, policy);
                    require(Double.isFinite(input) && direction * input > 0, "反求丢失有限性或方向");
                    near(options.displacement(input, policy), direction * visible, "拾取重复压缩当前形变");
                }
            }
            for (int direction : new int[] {-1, 1}) {
                require(options.overshoot(direction * limit, policy) == direction * Double.MAX_VALUE
                        && options.overshoot(direction * Double.MAX_VALUE, policy) == direction * Double.MAX_VALUE,
                        "不可达边界未投影到有限输入");
                near(options.displacement(direction * Double.MAX_VALUE, policy), direction * limit, "有限边界正向");
            }
        }
        ElasticOptions huge = new ElasticOptions(Double.MAX_VALUE, MotionSpec.instant());
        require(huge.overshoot(Double.MAX_VALUE / 2, MotionPolicy.NORMAL) == Double.MAX_VALUE,
                "上限以内但有限输入不可达的距离没有投影");
        near(huge.displacement(huge.overshoot(Double.MAX_VALUE / 8, MotionPolicy.NORMAL), MotionPolicy.NORMAL),
                Double.MAX_VALUE / 8, "大数反求中间乘积溢出");
    }

    private void finiteLimits() {
        for (double maximum : new double[] {Double.MIN_VALUE, 2 * Double.MIN_VALUE, Double.MIN_NORMAL, 1e-300, 32, 1e300, Double.MAX_VALUE}) {
            ElasticOptions options = new ElasticOptions(maximum, MotionSpec.instant());
            for (MotionPolicy policy : MotionPolicy.values()) {
                double previous = 0;
                double limit = policy == MotionPolicy.OFF ? 0 : maximum * (policy == MotionPolicy.REDUCED ? .25 : 1);
                for (double raw : new double[] {0, Double.MIN_VALUE, 1e-18, 1, 40, Double.MAX_VALUE}) {
                    double shown = options.displacement(raw, policy);
                    require(Double.isFinite(shown) && shown >= previous && shown <= limit,
                            "正向下溢、溢出或阻力失去单调性");
                    previous = shown;
                    for (int direction : new int[] {-1, 1}) {
                        double restored = options.overshoot(direction * shown, policy);
                        require(Double.isFinite(restored), "反求产生非有限值");
                        near(options.displacement(restored, policy), direction * shown, "极端有限值往返");
                        if (policy == MotionPolicy.OFF)
                            require(options.displacement(direction * raw, policy) == 0
                                    && options.overshoot(direction * raw, policy) == 0, "关闭策略残留形变");
                    }
                }
                for (double invalid : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
                    rejects(() -> options.displacement(invalid, policy));
                    rejects(() -> options.overshoot(invalid, policy));
                }
            }
        }
    }

    private static void near(double actual, double expected, String message) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= 8 * Math.ulp(expected),
                message + ": actual=" + actual + ", expected=" + expected);
    }

    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("非有限输入未被拒绝");
    }
}

package club.heiqi.uilib.ui.scene.text.math;

import club.heiqi.uilib.ui.scene.text.math.MathLexer.Token;

/** 每式共用生成、驻留与累计工作账本，暂停队列继续计入驻留额度。 */
final class MathExpansionBudget {
    static final int SOURCE_UNITS = 16384;

    // 生成量与同时待处理量分别按源长上限的四倍设限。
    // 累计访问/复制允许十六轮完整额度，防止少量替换反复搬运大参数。
    // 新生成、同时驻留与累计成本分别计费。
    private static final long TOKEN_LIMIT = 4L * SOURCE_UNITS;
    private static final long WORK_LIMIT = 16L * TOKEN_LIMIT;
    private long generated, pending, work;

    void generate(long count, Token origin) {
        checkPending(count, origin);
        if (count > TOKEN_LIMIT - generated) throw exceeded(origin, "累计生成token");
        generated += count;
    }

    void enqueue(int count, Token origin) {
        checkPending(count, origin);
        work(count, origin);
        pending += count;
    }

    private void checkPending(long count, Token origin) {
        if (count > TOKEN_LIMIT - pending) throw exceeded(origin, "待处理token");
    }

    void release(int count) {
        pending -= count;
    }

    // 工作量按 token 文本长度计费，包含长控制词与 verb 正文。
    static int weight(Token token) {
        return Math.max(1, token.text.length());
    }

    void work(long count, Token origin) {
        if (count > WORK_LIMIT - work) throw exceeded(origin, "累计token工作量");
        work += count;
    }

    private MathLexer.Failure exceeded(Token origin, String resource) {
        return new MathLexer.Failure(origin, "宏展开资源超过界限：" + resource);
    }
}

package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.scene.motion.MotionSpec;

/** 邮箱只声明参照中的曲线和时长；时间、进度、反向及策略限制归正式运动绑定。 */
final class MailMotion {
    static final MotionSpec STANDARD = MotionSpec.tween(150_000_000L, MailMotion::standard);
    static final MotionSpec SEARCH_BACKGROUND = MotionSpec.tween(100_000_000L, MailMotion::easeIn);
    static final MotionSpec RECEIVE_BACKGROUND = MotionSpec.tween(80_000_000L, MailMotion::standard);
    static final MotionSpec RECEIVE_SHADOW = MotionSpec.tween(80_000_000L, MotionSpec.LINEAR);

    private MailMotion() {}

    static double standard(double progress) { return bezier(progress, .4, .2); }
    static double easeIn(double progress) { return bezier(progress, .42, 1); }

    private static double bezier(double progress, double x1, double x2) {
        if (progress <= 0) return 0;
        if (progress >= 1) return 1;
        double low = 0, high = 1, t = 0;
        for (int i = 0; i < 20; i++) {
            t = (low + high) / 2;
            double x = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t;
            if (x < progress) low = t; else high = t;
        }
        return 3 * (1 - t) * t * t + t * t * t;
    }
}

package club.heiqi.uilib.ui.scene.text.math;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析时保留原单位，布局分别换算普通长度与 mu。 */
final class MathDimension {
    final double number;
    final String unit;
    final boolean blank;
    private static final Pattern SIZE =
            Pattern.compile(
                    "^\\s*([+-]?)\\s*((?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))\\s*([a-z]{2})\\s*$");

    MathDimension(double number, String unit, boolean blank) {
        this.number = number;
        this.unit = unit;
        this.blank = blank;
    }

    static MathDimension parse(String value, boolean allowBlank) {
        if (allowBlank && value.trim().isEmpty()) return new MathDimension(0, "pt", true);
        Matcher m = SIZE.matcher(value);
        if (!m.matches()) throw new IllegalArgumentException("Invalid size: " + value);
        String unit = m.group(3);
        if (!(" pt mm cm in bp pc dd cc nd nc sp px ex em mu ").contains(" " + unit + " "))
            throw new IllegalArgumentException("Invalid unit: " + unit);
        double number = Double.parseDouble(m.group(1) + m.group(2));
        if (Double.isNaN(number) || Double.isInfinite(number))
            throw new IllegalArgumentException("Invalid size: " + value);
        return new MathDimension(number, unit, false);
    }
}

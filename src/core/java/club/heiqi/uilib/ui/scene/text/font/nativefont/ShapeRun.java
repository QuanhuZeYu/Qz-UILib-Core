package club.heiqi.uilib.ui.scene.text.font.nativefont;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;

/** 按 HB 输出顺序保存不可变字形值载荷。 */
public final class ShapeRun extends AbstractList<ShapedGlyph> implements RandomAccess {
    public static final ShapeRun EMPTY = new ShapeRun(Collections.<ShapedGlyph>emptyList());
    private final List<ShapedGlyph> glyphs;

    public ShapeRun(List<ShapedGlyph> glyphs) {
        this.glyphs = Collections.unmodifiableList(new ArrayList<ShapedGlyph>(glyphs));
    }

    @Override
    public ShapedGlyph get(int index) {
        return glyphs.get(index);
    }

    @Override
    public int size() {
        return glyphs.size();
    }
}

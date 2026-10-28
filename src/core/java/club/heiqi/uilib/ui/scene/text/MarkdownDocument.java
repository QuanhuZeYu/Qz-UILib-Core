package club.heiqi.uilib.ui.scene.text;

import club.heiqi.uilib.ui.scene.text.markdown.MarkdownAdapter;

import java.util.Objects;

/** 显式 Markdown 转换入口，返回供呈现复用的共同文档。 */
public final class MarkdownDocument {
    private MarkdownDocument() {}

    public static DocumentContent parse(String source) {
        return parse(TextInput.string(source));
    }

    public static DocumentContent parse(TextInput input) {
        return new MarkdownAdapter(Objects.requireNonNull(input, "input")).parse();
    }
}

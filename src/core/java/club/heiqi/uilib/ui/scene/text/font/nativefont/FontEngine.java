package club.heiqi.uilib.ui.scene.text.font.nativefont;

/** 可跨线程共享的延迟工厂；调用方在实际使用线程创建并持有 native session。 */
public interface FontEngine {
    FontSession openSession();
}

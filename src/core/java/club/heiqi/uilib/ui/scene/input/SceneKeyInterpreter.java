package club.heiqi.uilib.ui.scene.input;

/** 将中性键统一解释为编辑或导航意图。 */
public final class SceneKeyInterpreter {
    private SceneKeyInterpreter() {}
    public static SceneEditEvent edit(SceneKeyEvent event) {
        if (event.phase != SceneKeyEvent.Phase.DOWN || event.alt || event.extra) return null;
        SceneEditEvent.Command command = null;
        if (event.primary) {
            switch (event.key) {
                case LEFT: command = SceneEditEvent.Command.WORD_LEFT; break;
                case RIGHT: command = SceneEditEvent.Command.WORD_RIGHT; break;
                case BACKSPACE: command = SceneEditEvent.Command.WORD_BACKSPACE; break;
                case DELETE: command = SceneEditEvent.Command.WORD_DELETE; break;
                case A: command = SceneEditEvent.Command.SELECT_ALL; break;
                case C: command = SceneEditEvent.Command.COPY; break;
                case X: command = SceneEditEvent.Command.CUT; break;
                case V: command = SceneEditEvent.Command.PASTE; break;
                case Z: command = event.shift ? SceneEditEvent.Command.REDO : SceneEditEvent.Command.UNDO; break;
                case Y: command = SceneEditEvent.Command.REDO; break;
                case HOME: command = SceneEditEvent.Command.DOCUMENT_HOME; break;
                case END: command = SceneEditEvent.Command.DOCUMENT_END; break;
                case UP: command = SceneEditEvent.Command.UP; break;
                case DOWN: command = SceneEditEvent.Command.DOWN; break;
                case PAGE_UP: command = SceneEditEvent.Command.PAGE_UP; break;
                case PAGE_DOWN: command = SceneEditEvent.Command.PAGE_DOWN; break;
                default: break;
            }
        } else {
            switch (event.key) {
                case LEFT: command = SceneEditEvent.Command.LEFT; break;
                case RIGHT: command = SceneEditEvent.Command.RIGHT; break;
                case UP: command = SceneEditEvent.Command.UP; break;
                case DOWN: command = SceneEditEvent.Command.DOWN; break;
                case HOME: command = SceneEditEvent.Command.HOME; break;
                case END: command = SceneEditEvent.Command.END; break;
                case BACKSPACE: command = SceneEditEvent.Command.BACKSPACE; break;
                case DELETE: command = SceneEditEvent.Command.DELETE; break;
                case PAGE_UP: command = SceneEditEvent.Command.PAGE_UP; break;
                case PAGE_DOWN: command = SceneEditEvent.Command.PAGE_DOWN; break;
                default: break;
            }
        }
        if (event.key == SceneKey.TAB) command = SceneEditEvent.Command.TAB;
        if (!event.repeat && (event.key == SceneKey.ENTER || event.key == SceneKey.KEYPAD_ENTER))
            command = event.primary ? SceneEditEvent.Command.SUBMIT : SceneEditEvent.Command.CONFIRM;
        if (!event.repeat && event.plainSpace()) command = SceneEditEvent.Command.ACTIVATE_ONLY;
        return command == null ? null : SceneEditEvent.command(command, event.shift, event.clipboard);
    }

    /** 将设备中性方向投影为编辑键意图，物理按压仍由宿主入口维护。 */
    public static SceneKeyEvent direction(SceneNavigationEvent direction) {
        switch (direction) {
            case LEFT: return new SceneKeyEvent(SceneKey.LEFT, false, false, false, false, false, null);
            case RIGHT: return new SceneKeyEvent(SceneKey.RIGHT, false, false, false, false, false, null);
            case UP: return new SceneKeyEvent(SceneKey.UP, false, false, false, false, false, null);
            case DOWN: return new SceneKeyEvent(SceneKey.DOWN, false, false, false, false, false, null);
            default: throw new IllegalArgumentException("需要四方向意图");
        }
    }

    public static SceneNavigationEvent navigation(SceneKeyEvent event) {
        if (event.phase != SceneKeyEvent.Phase.DOWN || event.alt || event.extra) return null;
        if (event.key == SceneKey.TAB) return event.shift ? SceneNavigationEvent.PREVIOUS : SceneNavigationEvent.NEXT;
        if (!event.primary) {
            switch (event.key) {
                case LEFT: return SceneNavigationEvent.LEFT;
                case RIGHT: return SceneNavigationEvent.RIGHT;
                case UP: return SceneNavigationEvent.UP;
                case DOWN: return SceneNavigationEvent.DOWN;
                default: break;
            }
        }
        if (!event.repeat && (event.key == SceneKey.ENTER || event.key == SceneKey.KEYPAD_ENTER)) return SceneNavigationEvent.ACTIVATE;
        return null;
    }
}

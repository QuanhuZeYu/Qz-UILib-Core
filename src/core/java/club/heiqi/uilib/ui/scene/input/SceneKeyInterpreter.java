package club.heiqi.uilib.ui.scene.input;

/** 中性键只解释为既有编辑或导航意图；宿主不再各自复制编辑语义表。 */
public final class SceneKeyInterpreter {
    private SceneKeyInterpreter() {}
    public static SceneEditEvent edit(SceneKeyEvent event) {
        if (event.alt || event.extra) return null;
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
        if (!event.repeat && event.key == SceneKey.SPACE) command = SceneEditEvent.Command.ACTIVATE_ONLY;
        return command == null ? null : SceneEditEvent.command(command, event.shift, event.clipboard);
    }

    /** 设备提供中性方向，沿相同编辑语义；不建立键盘物理段或伪造宿主事实。 */
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
        if (event.alt || event.extra) return null;
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
        if (!event.repeat && (event.key == SceneKey.ENTER || event.key == SceneKey.KEYPAD_ENTER || event.key == SceneKey.SPACE)) return SceneNavigationEvent.ACTIVATE;
        return null;
    }
}

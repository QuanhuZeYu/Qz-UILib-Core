package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.List;

/** 列表只把正式按钮焦点移动到下一项；字段和组字仍由原编辑目标处理。 */
final class FilePickerListNavigation extends SceneAppearance.Flow {
    private final FilePickerSelection selection;
    private final SceneTypeahead typeahead = new SceneTypeahead(800_000_000L, 128);
    FilePickerListNavigation(FilePickerSelection selection){this.selection=selection;}

    @Override public boolean editActivator(SceneNode node,SceneNode focused,SceneEditEvent event){
        if(event.compositionEvent||event.compositionSource!=null||focused.control()==null||focused.control().acceptsTextInput(focused))return false;
        if(event.command==null)return event.text!=null&&!event.text.isEmpty();
        switch(event.command){case UP:case DOWN:case HOME:case END:case PAGE_UP:case PAGE_DOWN:return true;default:return false;}
    }
    @Override public boolean edit(SceneNode node,SceneEditEvent event,SceneGeometryView geometry){
        if(selection.busy.get())return true;
        List<SceneNode> targets=targets(node);
        if(targets.isEmpty())return true;
        int current=targets.indexOf(node.runtime().focusRouter().focused().get());
        Integer next=next(node,targets,current,event);
        if(next==null)return false;
        if(next>=0)node.runtime().focusRouter().navigateTo(node.runtime().captureMount(targets.get(next)));
        return true;
    }
    private Integer next(SceneNode node,List<SceneNode> targets,int current,SceneEditEvent event){
        if(event.command==null){
            int found=typeahead.find(targets,SceneNode::text,current,event.text,node.runtime().inputNanos());
            return found<0?current:found;
        }
        return command(event.command,current,targets.size());
    }
    private static List<SceneNode> targets(SceneNode node){
        List<SceneNode> result=new ArrayList<>();
        for(SceneNode row:node.children())for(SceneNode child:row.children()){
            if(child.focusable()&&child.clickAction()!=null&&node.runtime().interactive(child))result.add(child);
        }
        return result;
    }
    private static Integer command(SceneEditEvent.Command command,int current,int size){
        switch(command){
            case UP:return Math.max(0,current-1);
            case DOWN:return Math.min(size-1,current+1);
            case HOME:return 0;
            case END:return size-1;
            case PAGE_UP:return Math.max(0,current-8);
            case PAGE_DOWN:return Math.min(size-1,current+8);
            default:return null;
        }
    }
}

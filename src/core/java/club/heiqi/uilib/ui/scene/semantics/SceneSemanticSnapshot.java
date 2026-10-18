package club.heiqi.uilib.ui.scene.semantics;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 与呈现同代的不可变语义；所有节点关系和动作目标均为纯挂载ID。 */
public final class SceneSemanticSnapshot {
    public static final SceneSemanticSnapshot EMPTY = new SceneSemanticSnapshot(0,Collections.emptyList(),null,null,Collections.emptyList(),Collections.emptyList(),Collections.emptyList());
    public static final class Entry {
        public final SceneSemanticId id,parent;
        public final SceneSemantics.Values values;
        public final boolean enabled,focusable,focused,rangeInteractive,offscreen;
        public final LayoutBox box,clip;
        public final List<SceneSemanticId> labelledBy,describedBy;
        public final Set<SceneSemantics.Action> actions;
        public Entry(SceneSemanticId id,SceneSemanticId parent,SceneSemantics.Values values,boolean enabled,boolean focusable,boolean focused,
                boolean rangeInteractive,boolean offscreen,LayoutBox box,LayoutBox clip,List<SceneSemanticId> labels,List<SceneSemanticId> descriptions,Set<SceneSemantics.Action> actions){
            this.id=id;this.parent=parent;this.values=values;this.enabled=enabled;this.focusable=focusable;this.focused=focused;
            this.rangeInteractive=rangeInteractive;this.offscreen=offscreen;this.box=box;this.clip=clip;
            labelledBy=immutable(labels);describedBy=immutable(descriptions);
            EnumSet<SceneSemantics.Action> copy=EnumSet.noneOf(SceneSemantics.Action.class);copy.addAll(actions);this.actions=Collections.unmodifiableSet(copy);
        }
        @Override public boolean equals(Object other){
            if(!(other instanceof Entry))return false;Entry e=(Entry)other;
            return Objects.equals(id,e.id)&&Objects.equals(parent,e.parent)&&values.equals(e.values)&&enabled==e.enabled&&focusable==e.focusable
                    &&focused==e.focused&&rangeInteractive==e.rangeInteractive&&offscreen==e.offscreen&&same(box,e.box)&&same(clip,e.clip)
                    &&labelledBy.equals(e.labelledBy)&&describedBy.equals(e.describedBy)&&actions.equals(e.actions);
        }
        private static boolean same(LayoutBox a,LayoutBox b){return a==b||a!=null&&b!=null&&a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height;}
        @Override public int hashCode(){return id.hashCode();}
    }
    public final long revision;
    public final List<Entry> entries;
    public final SceneSemanticId focused,currentModal;
    public final List<SceneSemanticId> added,updated,removed;
    private final Map<SceneSemanticId,Entry> index;
    public SceneSemanticSnapshot(long revision,List<Entry> entries,SceneSemanticId focused,SceneSemanticId modal,
            List<SceneSemanticId> added,List<SceneSemanticId> updated,List<SceneSemanticId> removed){
        this.revision=revision;this.entries=immutable(entries);this.focused=focused;currentModal=modal;
        this.added=immutable(added);this.updated=immutable(updated);this.removed=immutable(removed);
        Map<SceneSemanticId,Entry> map=new LinkedHashMap<>();for(Entry entry:entries)map.put(entry.id,entry);index=Collections.unmodifiableMap(map);
    }
    public Entry get(SceneSemanticId id){return index.get(id);}
    public SceneSemanticSnapshot cleared(){return entries.isEmpty()?this:new SceneSemanticSnapshot(revision+1,Collections.emptyList(),null,null,Collections.emptyList(),Collections.emptyList(),new ArrayList<>(index.keySet()));}
    private static <T> List<T> immutable(List<T> source){return Collections.unmodifiableList(new ArrayList<>(source));}
}

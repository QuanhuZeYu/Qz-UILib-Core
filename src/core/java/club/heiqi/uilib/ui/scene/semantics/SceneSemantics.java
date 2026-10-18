package club.heiqi.uilib.ui.scene.semantics;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 控件的可追踪语义声明；节点关系仅存在于活声明，快照只发布解析后的挂载ID。 */
public final class SceneSemantics {
    public enum Role { GROUP, LABEL, BUTTON, IMAGE, CHECKBOX, SWITCH, RADIO_GROUP, RADIO, TEXT_FIELD, TEXT_AREA,
        SLIDER, SCROLLBAR, LIST, LIST_ITEM, TABLE, ROW, COLUMN_HEADER, CELL, TREE, TREE_ITEM, TAB_LIST, TAB, TAB_PANEL,
        SELECT, LISTBOX, OPTION, MENU, MENU_ITEM }
    public enum Action { FOCUS, ACTIVATE, SET_TEXT, SUBMIT, INCREMENT, DECREMENT, SET_VALUE, EXPAND, COLLAPSE, SELECT }
    /** 名称和值等纯数据可安全保留在旧快照，不含动作闭包。null表示这一项未声明。 */
    public static final class Values {
        public final Role role;
        public final String name, description, value;
        public final Boolean readOnly, selected, checked, mixed, expanded;
        public final Integer index, count, level, row, column, rowCount, columnCount;
        public final Double minimum, maximum, current;
        private Values(Builder b) {
            role=b.role;name=b.name;description=b.description;value=b.value;readOnly=b.readOnly;selected=b.selected;
            checked=b.checked;mixed=b.mixed;expanded=b.expanded;index=b.index;count=b.count;level=b.level;
            row=b.row;column=b.column;rowCount=b.rowCount;columnCount=b.columnCount;minimum=b.minimum;maximum=b.maximum;current=b.current;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Values)) return false; Values v=(Values)other;
            return Arrays.equals(fields(),v.fields());
        }
        private Object[] fields() { return new Object[]{role,name,description,value,readOnly,selected,checked,mixed,expanded,
                index,count,level,row,column,rowCount,columnCount,minimum,maximum,current}; }
        @Override public int hashCode() { return Arrays.hashCode(fields()); }
    }
    public final Values values;
    public final List<SceneNode> labelledBy, describedBy;
    public final Set<Action> actions;
    public final Boolean excluded;
    public final SceneNode focusTarget;
    private SceneSemantics(Builder b) {
        values=new Values(b);labelledBy=copy(b.labelledBy);describedBy=copy(b.describedBy);
        actions=b.actions==null?null:Collections.unmodifiableSet(EnumSet.copyOf(b.actions));excluded=b.excluded;focusTarget=b.focusTarget;
    }
    private static <T> List<T> copy(List<T> source) { return source==null?null:Collections.unmodifiableList(new ArrayList<>(source)); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(Role role) { return new Builder().role(role); }
    public Builder toBuilder() { return new Builder(this); }
    public static SceneSemantics merge(SceneSemantics defaults, SceneSemantics override) {
        if(defaults==null)return override;if(override==null)return defaults;
        Builder b=defaults.toBuilder(); Values v=override.values;
        if(v.role!=null)b.role=v.role;if(v.name!=null)b.name=v.name;if(v.description!=null)b.description=v.description;if(v.value!=null)b.value=v.value;
        if(v.readOnly!=null)b.readOnly=v.readOnly;if(v.selected!=null)b.selected=v.selected;if(v.checked!=null)b.checked=v.checked;
        if(v.mixed!=null)b.mixed=v.mixed;if(v.expanded!=null)b.expanded=v.expanded;
        if(v.index!=null)b.index=v.index;if(v.count!=null)b.count=v.count;if(v.level!=null)b.level=v.level;
        if(v.row!=null)b.row=v.row;if(v.column!=null)b.column=v.column;if(v.rowCount!=null)b.rowCount=v.rowCount;if(v.columnCount!=null)b.columnCount=v.columnCount;
        if(v.minimum!=null)b.minimum=v.minimum;if(v.maximum!=null)b.maximum=v.maximum;if(v.current!=null)b.current=v.current;
        if(override.labelledBy!=null)b.labelledBy=override.labelledBy;if(override.describedBy!=null)b.describedBy=override.describedBy;
        if(override.actions!=null){b.actions=EnumSet.noneOf(Action.class);b.actions.addAll(override.actions);}if(override.excluded!=null)b.excluded=override.excluded;if(override.focusTarget!=null)b.focusTarget=override.focusTarget;
        return b.build();
    }
    @Override public boolean equals(Object other) {
        if(!(other instanceof SceneSemantics))return false;SceneSemantics v=(SceneSemantics)other;
        return values.equals(v.values)&&Objects.equals(labelledBy,v.labelledBy)&&Objects.equals(describedBy,v.describedBy)
                &&Objects.equals(actions,v.actions)&&Objects.equals(excluded,v.excluded)&&focusTarget==v.focusTarget;
    }
    @Override public int hashCode(){return Objects.hash(values,labelledBy,describedBy,actions,excluded,focusTarget);}
    public static final class Builder {
        private SceneNode focusTarget;
        private Role role; private String name,description,value; private Boolean readOnly,selected,checked,mixed,expanded,excluded;
        private Integer index,count,level,row,column,rowCount,columnCount;private Double minimum,maximum,current;
        private List<SceneNode> labelledBy,describedBy;private EnumSet<Action> actions;
        private Builder() { }
        private Builder(SceneSemantics s) {
            Values v=s.values;role=v.role;name=v.name;description=v.description;value=v.value;readOnly=v.readOnly;selected=v.selected;
            checked=v.checked;mixed=v.mixed;expanded=v.expanded;index=v.index;count=v.count;level=v.level;
            row=v.row;column=v.column;rowCount=v.rowCount;columnCount=v.columnCount;minimum=v.minimum;maximum=v.maximum;current=v.current;
            labelledBy=s.labelledBy;describedBy=s.describedBy;actions=s.actions==null?null:EnumSet.noneOf(Action.class);if(actions!=null)actions.addAll(s.actions);excluded=s.excluded;focusTarget=s.focusTarget;
        }
        public Builder focusTarget(SceneNode node){focusTarget=node;return this;}
        public Builder role(Role v){role=v;return this;} public Builder name(String v){name=v;return this;}
        public Builder description(String v){description=v;return this;} public Builder value(String v){value=v;return this;}
        public Builder readOnly(boolean v){readOnly=v;return this;}public Builder selected(boolean v){selected=v;return this;}
        public Builder checked(boolean v){checked=v;return this;}public Builder mixed(boolean v){mixed=v;return this;}
        public Builder expanded(boolean v){expanded=v;return this;}public Builder excluded(boolean v){excluded=v;return this;}
        /** 位置为从1开始的逻辑位置，数量来自业务投影而非当前挂载行数。 */
        public Builder position(int index,int count){this.index=index;this.count=count;return this;}
        public Builder level(int v){level=v;return this;}
        public Builder count(int v){count=v;return this;}
        public Builder tableSize(int rows,int columns){rowCount=rows;columnCount=columns;return this;}
        public Builder cell(int row,int column,int rows,int columns){this.row=row;this.column=column;rowCount=rows;columnCount=columns;return this;}
        public Builder range(double minimum,double maximum,double current){this.minimum=minimum;this.maximum=maximum;this.current=current;return this;}
        public Builder labelledBy(SceneNode... nodes){labelledBy=Arrays.asList(nodes.clone());return this;}
        public Builder describedBy(SceneNode... nodes){describedBy=Arrays.asList(nodes.clone());return this;}
        public Builder actions(Action... values){actions=EnumSet.noneOf(Action.class);actions.addAll(Arrays.asList(values));return this;}
        public SceneSemantics build(){return new SceneSemantics(this);}
    }
}

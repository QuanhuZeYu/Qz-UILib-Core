package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneLayer;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticId;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticRequest;
import club.heiqi.uilib.ui.scene.semantics.SceneSemanticSnapshot;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 挂载期描述观察和当批纯值投影；语义不拥有另一棵控件或焦点树。 */
final class SceneSemanticCoordinator {
    private static final class Mounted {
        final SceneNode node;
        final SceneSemanticId id=SceneSemanticId.create();
        final List<ReadableSignal<SceneSemantics>> overrides=new ArrayList<>(),defaults=new ArrayList<>();
        final club.heiqi.uilib.ui.reactive.Signal<Long> clock;
        long revision;
        SceneSemantics value;
        Observation observation;
        Mounted(SceneNode node,SceneRuntime runtime){this.node=node;clock=runtime.domain().signal(0L);}
    }
    private final SceneRuntime runtime;
    private final Map<SceneNode,Mounted> mounted=new IdentityHashMap<>();
    private final Map<SceneSemanticId,Mounted> byId=new HashMap<>();
    private final Set<Mounted> dirty=new LinkedHashSet<>();
    private SceneSemanticSnapshot snapshot=SceneSemanticSnapshot.EMPTY;
    private SceneGeometryView geometry;
    private SceneNode focus,modal;
    private long factRevision=Long.MIN_VALUE;
    private boolean changed;
    SceneSemanticCoordinator(SceneRuntime runtime){this.runtime=runtime;}
    void mount(SceneNode node){Mounted m=new Mounted(node,runtime);mounted.put(node,m);byId.put(m.id,m);changed=true;}
    BindingActivation observe(SceneNode node,Owner owner){
        Mounted m=mounted.get(node);
        m.observation=runtime.domain().observe(owner,()->{
            m.clock.get(); SceneSemantics next=runtime.domain().readOnly(()->describe(m));
            if(!Objects.equals(m.value,next)){m.value=next;changed=true;}
        },()->{dirty.add(m);changed=true;});
        dirty.add(m);
        return new BindingActivation(){public void refresh(){SceneSemanticCoordinator.this.refresh(m);}public void close(){m.observation.close();dirty.remove(m);}};
    }
    BindingActivation bind(SceneNode node,ReadableSignal<SceneSemantics> source,boolean defaults){
        Mounted m=mounted.get(node);Objects.requireNonNull(source,"语义来源");
        List<ReadableSignal<SceneSemantics>> declarations=defaults?m.defaults:m.overrides;declarations.add(source);invalidate(node);
        return new BindingActivation(){public void refresh(){}public void close(){declarations.remove(source);}};
    }
    private SceneSemantics describe(Mounted m){
        SceneNode node=m.node;
        SceneSemantics value=node.control()==null?null:node.control().semantics(node);
        if(value==null&&node.clickAction()!=null)value=SceneSemantics.builder(SceneSemantics.Role.BUTTON).name(node.text()).build();
        if(value==null&&node.modal())value=SceneSemantics.builder(SceneSemantics.Role.GROUP).build();
        for(ReadableSignal<SceneSemantics> source:m.defaults)value=SceneSemantics.merge(value,source.get());
        for(ReadableSignal<SceneSemantics> source:m.overrides)value=SceneSemantics.merge(value,source.get());
        return value;
    }
    void invalidate(SceneNode node){Mounted m=mounted.get(node);if(m!=null){m.clock.set(++m.revision);dirty.add(m);changed=true;}}
    void unmount(SceneNode node){Mounted m=mounted.remove(node);if(m==null)return;byId.remove(m.id);dirty.remove(m);changed=true;}
    private void refresh(Mounted m){
        if(!dirty.remove(m)||mounted.get(m.node)!=m)return;
        // 节点text等普通声明已结算；实际Signal依赖仍由观察器记录。
        m.observation.refresh();
    }
    private void refresh(){for(Mounted m:new ArrayList<>(dirty))refresh(m);}
    SceneSemanticSnapshot publish(SceneGeometryView next){
        refresh();SceneNode focused=runtime.focusRouter().focused().get(),active=runtime.activeModal();
        if(!changed&&geometry==next&&focus==focused&&modal==active&&factRevision==runtime.revision())return snapshot;
        Map<SceneNode,SceneSemantics> visible=new IdentityHashMap<>();List<SceneNode> order=new ArrayList<>();
        Map<SceneNode,List<SceneNode>> relationTargets=new IdentityHashMap<>();
        for(SceneNode root:runtime.roots())collect(root,next,visible,order,relationTargets);
        Map<SceneNode,String> names=new IdentityHashMap<>();
        for(SceneNode n:order)resolveName(n,visible,relationTargets,names,Collections.newSetFromMap(new IdentityHashMap<>()));
        Map<SceneNode,SceneNode> focusRepresentatives=new IdentityHashMap<>();
        for(SceneNode n:order){SceneSemantics s=visible.get(n);SceneNode target=focusTarget(n,s);
            if(s.focusTarget!=null&&target!=null)focusRepresentatives.putIfAbsent(target,n);}
        List<SceneSemanticSnapshot.Entry> entries=new ArrayList<>();SceneSemanticId focusId=null,modalId=null;
        for(SceneNode node:order){
            Mounted m=mounted.get(node);SceneSemantics s=visible.get(node);SceneNode actual=focusTarget(node,s);
            SceneNode parent=node.parent();while(parent!=null&&!visible.containsKey(parent))parent=parent.parent();
            List<SceneNode> labelNodes=relationNodes(s.labelledBy,relationTargets),descriptionNodes=relationNodes(s.describedBy,relationTargets);
            List<SceneSemanticId> labels=relations(labelNodes),descriptions=relations(descriptionNodes);
            SceneSemantics.Builder values=s.toBuilder();
            if(s.values.name==null&&s.labelledBy!=null)values.name(names.get(node));
            if(s.values.description==null&&s.describedBy!=null)values.description(relationText(descriptionNodes,names));
            boolean enabled=enabled(node),interactive=runtime.interactive(node),focusable=actual!=null&&actual.focusable();
            EnumSet<SceneSemantics.Action> actions=actions(node,s);
            if(!interactive)actions.clear();
            SceneGeometryView.Entry entry=next.get(node);LayoutBox box=entry==null?null:entry.box,clip=entry==null?null:entry.clip;
            boolean offscreen=box==null||next.visibleRegion(node).empty();
            boolean isFocused=(focused==node||actual!=null&&focused==actual)
                    &&(!focusRepresentatives.containsKey(focused)||focusRepresentatives.get(focused)==node);
            if(isFocused)focusId=m.id;
            if(node==active)modalId=m.id;
            entries.add(new SceneSemanticSnapshot.Entry(m.id,parent==null?null:mounted.get(parent).id,values.build().values,enabled,focusable,isFocused,
                    interactive,offscreen,box,clip,labels,descriptions,actions));
        }
        List<SceneSemanticId> added=new ArrayList<>(),updated=new ArrayList<>(),removed=new ArrayList<>();
        Set<SceneSemanticId> present=new LinkedHashSet<>();
        for(SceneSemanticSnapshot.Entry entry:entries){present.add(entry.id);SceneSemanticSnapshot.Entry old=snapshot.get(entry.id);
            if(old==null)added.add(entry.id);else if(!old.equals(entry))updated.add(entry.id);}
        for(SceneSemanticSnapshot.Entry old:snapshot.entries)if(!present.contains(old.id))removed.add(old.id);
        if(!added.isEmpty()||!updated.isEmpty()||!removed.isEmpty()||!Objects.equals(snapshot.focused,focusId)||!Objects.equals(snapshot.currentModal,modalId)
                ||!sameOrder(snapshot.entries,entries))snapshot=new SceneSemanticSnapshot(snapshot.revision+1,entries,focusId,modalId,added,updated,removed);
        geometry=next;focus=focused;modal=active;factRevision=runtime.revision();changed=false;
        return snapshot;
    }
    private boolean sameOrder(List<SceneSemanticSnapshot.Entry> a,List<SceneSemanticSnapshot.Entry> b){
        if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!a.get(i).id.equals(b.get(i).id))return false;return true;
    }
    private List<SceneNode> collect(SceneNode node,SceneGeometryView geometry,Map<SceneNode,SceneSemantics> output,List<SceneNode> order,
            Map<SceneNode,List<SceneNode>> relationTargets){
        if(!mounted.containsKey(node)||!node.visible()||node.destroyed()||node.retired())return Collections.emptyList();
        SceneLayer.Policy layer=node.control()==null?null:node.control().layerPolicy(node);
        if(layer!=null&&(layer.participation==SceneLayer.Participation.PASSIVE||!geometry.layerSourceAvailable(node)))return Collections.emptyList();
        SceneSemantics value=mounted.get(node).value;
        if(value!=null&&Boolean.TRUE.equals(value.excluded))return Collections.emptyList();
        boolean semantic=value!=null&&value.values.role!=null;
        if(semantic){output.put(node,value);order.add(node);}
        List<SceneNode> content=new ArrayList<>();
        for(SceneNode child:node.children())content.addAll(collect(child,geometry,output,order,relationTargets));
        for(SceneNode child:runtime.scrollDecorations(node))collect(child,geometry,output,order,relationTargets);
        // 工厂可返回普通布局容器；关联透过它指向真实语义内容，不要求旧用法增加GROUP。
        List<SceneNode> targets=semantic?Collections.singletonList(node):content;
        relationTargets.put(node,targets);return targets;
    }
    private List<SceneNode> relationNodes(List<SceneNode> nodes,Map<SceneNode,List<SceneNode>> targets){
        Set<SceneNode> result=new LinkedHashSet<>();if(nodes!=null)for(SceneNode node:nodes){List<SceneNode> actual=targets.get(node);if(actual!=null)result.addAll(actual);}
        return new ArrayList<>(result);
    }
    private List<SceneSemanticId> relations(List<SceneNode> nodes){
        List<SceneSemanticId> result=new ArrayList<>();for(SceneNode node:nodes)result.add(mounted.get(node).id);return result;
    }
    private String resolveName(SceneNode node,Map<SceneNode,SceneSemantics> visible,Map<SceneNode,List<SceneNode>> targets,
            Map<SceneNode,String> names,Set<SceneNode> visiting){
        if(names.containsKey(node))return names.get(node);
        SceneSemantics s=visible.get(node);if(s==null||!visiting.add(node))return "";
        String value=s.values.name;
        if(value==null&&s.labelledBy!=null){StringBuilder text=new StringBuilder();for(SceneNode label:relationNodes(s.labelledBy,targets)){
            String part=resolveName(label,visible,targets,names,visiting);if(part.isEmpty())continue;if(text.length()>0)text.append(' ');text.append(part);}
            value=text.toString();}
        if(value==null)value=node.text();if(value==null)value="";
        visiting.remove(node);names.put(node,value);return value;
    }
    private String relationText(List<SceneNode> nodes,Map<SceneNode,String> names){
        StringBuilder value=new StringBuilder();for(SceneNode node:nodes){String text=names.get(node);if(text==null||text.isEmpty())continue;
            if(value.length()>0)value.append(' ');value.append(text);}return value.toString();
    }
    private SceneNode focusTarget(SceneNode node,SceneSemantics s){
        SceneNode target=s.focusTarget==null?node:s.focusTarget;
        if(!mounted.containsKey(target))return null;
        for(SceneNode parent=target;parent!=null;parent=parent.parent())if(parent==node)return target;
        return null;
    }
    private EnumSet<SceneSemantics.Action> actions(SceneNode node,SceneSemantics s){
        EnumSet<SceneSemantics.Action> actions=EnumSet.noneOf(SceneSemantics.Action.class);if(s.actions!=null)actions.addAll(s.actions);
        SceneNode target=focusTarget(node,s);
        if(target!=null&&target.focusable()&&runtime.interactive(target))actions.add(SceneSemantics.Action.FOCUS);
        if(node.clickAction()!=null)actions.add(SceneSemantics.Action.ACTIVATE);
        if(!enabled(node))actions.clear();return actions;
    }
    private static boolean enabled(SceneNode node){for(SceneNode n=node;n!=null;n=n.parent())if(!n.enabled())return false;return true;}
    boolean dispatch(SceneSemanticRequest request,SceneGeometryView geometry){
        publish(geometry);Mounted m=byId.get(request.target);
        SceneSemanticSnapshot.Entry entry=snapshot.get(request.target);
        if(m==null||entry==null||!entry.actions.contains(request.action)||!runtime.interactive(m.node))return false;
        SceneSemantics s=m.value;
        if((request.action==SceneSemantics.Action.SET_TEXT||request.action==SceneSemantics.Action.SUBMIT)
                &&runtime.semanticCompositionActive(m.node))return false;
        if(request.action==SceneSemantics.Action.FOCUS){SceneNode target=focusTarget(m.node,s);
            return target!=null&&target.focusable()&&runtime.focusRouter().navigateTo(runtime.captureMount(target));}
        return runtime.invokeSemanticAction(m.node,request);
    }
    SceneSemanticSnapshot clear(){mounted.clear();byId.clear();dirty.clear();geometry=null;focus=modal=null;snapshot=snapshot.cleared();return snapshot;}
}

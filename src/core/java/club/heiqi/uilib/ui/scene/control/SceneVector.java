package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.geometry.*;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.*;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.*;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.semantics.SceneSemantics;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 正式路径控件：状态来自原Signal，图形和命中共用当前路径与精度，动作继续原按钮路由。 */
public final class SceneVector {
    private SceneVector(){}
    public static final class Model {
        public final ScenePath path;
        public final double viewX,viewY,viewWidth,viewHeight;
        public final Integer fill,strokeColor;
        public final SceneStroke stroke;
        public Model(ScenePath path,double width,double height,int fill){this(path,0,0,width,height,fill,null,null);}
        public Model(ScenePath path,double width,double height,Integer fill,Integer strokeColor,SceneStroke stroke){this(path,0,0,width,height,fill,strokeColor,stroke);}
        public Model(ScenePath path,double x,double y,double width,double height,Integer fill,Integer strokeColor,SceneStroke stroke){
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(width)||!Double.isFinite(height)||width<=0||height<=0)
                throw new IllegalArgumentException("路径视图原点须有限，尺寸须为正且有限");
            this.path=Objects.requireNonNull(path,"路径");viewX=x;viewY=y;viewWidth=width;viewHeight=height;this.fill=fill;this.strokeColor=strokeColor;
            this.stroke=strokeColor==null?null:stroke==null?new SceneStroke(1):stroke;
        }
        SceneShape shape(){return SceneShape.vector(path,viewX,viewY,viewWidth,viewHeight,fill!=null,stroke);}
    }
    public static SceneNode create(ScenePageContext page,ReadableSignal<Model> model){
        Objects.requireNonNull(page).domain().checkWrite();Control control=new Control(Objects.requireNonNull(model,"路径声明"));
        SceneNode node=new SceneNode().control(control).size(Size.natural(),Size.natural());
        node.bind((runtime,self,owner)->{
            control.width=page.domain().computed(owner,()->control.model().viewWidth);
            control.height=page.domain().computed(owner,()->control.model().viewHeight);
            control.shape=page.domain().computed(owner,()->control.model().shape());
            return new BindingActivation(){public void refresh(){}public void close(){control.reset();}};
        });
        return node;
    }
    public static SceneNode create(ScenePageContext page,ReadableSignal<Model> model,Runnable action){return create(page,model).focusable(true).clickFocus(true).onClick(Objects.requireNonNull(action));}
    private static final class Control implements SceneControl {
        final ReadableSignal<Model> source;
        ReadableSignal<Double> width,height;ReadableSignal<SceneShape> shape;
        ScenePath lastPath;SceneStroke lastStroke;double lastWidth,lastHeight,lastX,lastY,lastViewWidth,lastViewHeight,lastError;
        boolean lastFilled;SceneMesh fill=SceneMesh.EMPTY,stroke=SceneMesh.EMPTY;
        Control(ReadableSignal<Model> source){this.source=source;reset();}
        Model model(){return Objects.requireNonNull(source.get(),"路径声明值");}
        void reset(){width=()->model().viewWidth;height=()->model().viewHeight;shape=()->model().shape();lastPath=null;fill=stroke=SceneMesh.EMPTY;}
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node){return Arrays.asList(width,height);}
        @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode node){return Collections.singletonList(shape);}
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node){return Collections.singletonList(source);}
        @Override public MeasuredSize measure(SceneNode node,Constraints constraints,SceneTextMeasurer text){
            return new MeasuredSize((int)Math.min(Integer.MAX_VALUE,Math.ceil(width.get())),(int)Math.min(Integer.MAX_VALUE,Math.ceil(height.get())));
        }
        @Override public ChildPlacements arrange(SceneNode node,LayoutBox box,SceneChildMeasurer children){return new ChildPlacements(Collections.emptyList());}
        @Override public SceneShape hitShape(SceneNode node){return shape.get();}
        @Override public SceneSemantics semantics(SceneNode node){
            return SceneSemantics.builder(node.clickAction()==null?SceneSemantics.Role.IMAGE:SceneSemantics.Role.BUTTON).name(node.text()).build();
        }
        @Override public Object pointerTarget(SceneNode node,SceneGeometryView geometry,int x,int y){return node.clickAction()!=null&&geometry.contains(node,x,y)?node:null;}
        @Override public void paint(SceneNode node,SceneGeometryView geometry,PaintPlanBuilder target){
            SceneGeometryView.Entry entry=geometry.get(node);if(entry==null||entry.box.width<=0||entry.box.height<=0)return;
            Model value=model();double scale=geometry.coordinateTransform().maximumScale();if(scale==0)return;
            SceneTransform fit=SceneTransform.scale(entry.box.width/value.viewWidth,entry.box.height/value.viewHeight)
                    .multiply(SceneTransform.translation(-value.viewX,-value.viewY));
            double error=.25/scale/fit.maximumScale();boolean filled=value.fill!=null;
            if(lastPath!=value.path||!Objects.equals(lastStroke,value.stroke)||lastFilled!=filled||lastWidth!=entry.box.width||lastHeight!=entry.box.height
                    ||lastX!=value.viewX||lastY!=value.viewY||lastViewWidth!=value.viewWidth||lastViewHeight!=value.viewHeight||lastError!=error){
                fill=filled?value.path.fillMesh(error).transformed(fit):SceneMesh.EMPTY;
                stroke=value.stroke==null?SceneMesh.EMPTY:value.path.strokeMesh(value.stroke,error).transformed(fit);
                lastPath=value.path;lastStroke=value.stroke;lastFilled=filled;lastWidth=entry.box.width;lastHeight=entry.box.height;
                lastX=value.viewX;lastY=value.viewY;lastViewWidth=value.viewWidth;lastViewHeight=value.viewHeight;lastError=error;
            }
            SceneTransform position=SceneTransform.translation(entry.box.x,entry.box.y);
            if(!fill.empty())target.add(PaintCommand.path(fill.transformed(position),entry.clip,value.fill));
            if(!stroke.empty())target.add(PaintCommand.path(stroke.transformed(position),entry.clip,value.strokeColor));
        }
    }
}

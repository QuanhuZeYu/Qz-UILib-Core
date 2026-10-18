package club.heiqi.uilib.ui.scene.paint;

import club.heiqi.uilib.ui.scene.image.ImagePlacement;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.geometry.SceneClipRegion;
import club.heiqi.uilib.ui.scene.geometry.SceneMesh;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.text.TextLayout;

import java.util.Objects;

/** 封闭值载荷：不引用节点、不读回布局；嵌套计划均为同代已定位值。 */
public final class PaintCommand {
    public enum Kind {
        RECT,
        LINE,
        TEXT,
        IMAGE,
        VIDEO,
        IMAGE_BRANCH_OVERLAY,
        PATH
    }

    /** 新路径或正式几何的caller裁剪事实；矩阵归为单位值也保留，旧工厂的world调用条件保持。 */
    public final boolean sceneGeometry;
    public final SceneTransform transform;
    public final SceneClipRegion shapeClip;
    private final SceneClipRegion effectiveClip;
    public final SceneMesh mesh;
    public final Kind kind;
    public final LayoutBox box, clip;
    public final int color;
    public final TextLayout text;
    public final ImagePlacement image;
    public final Line line;
    public final club.heiqi.uilib.ui.scene.video.VideoPlacement video;

    /** 平端点线段，线宽沿法线居中；零长度不绘制，clip不改变端点或方向。 */
    public static final class Line {
        public final float x0, y0, x1, y1, thickness;

        private Line(float x0, float y0, float x1, float y1, float thickness) {
            if (!Float.isFinite(x0)
                    || !Float.isFinite(y0)
                    || !Float.isFinite(x1)
                    || !Float.isFinite(y1)
                    || !Float.isFinite(thickness)
                    || thickness <= 0) throw new IllegalArgumentException("无效线段");
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.thickness = thickness;
        }

        public boolean empty() {
            return x0 == x1 && y0 == y1;
        }

        private LayoutBox bounds() {
            double half = empty() ? 0 : (double) thickness / 2;
            double left = Math.floor(Math.min(x0, x1) - half);
            double top = Math.floor(Math.min(y0, y1) - half);
            double right = Math.ceil(Math.max(x0, x1) + half);
            double bottom = Math.ceil(Math.max(y0, y1) + half);
            if (left < Integer.MIN_VALUE
                    || top < Integer.MIN_VALUE
                    || right > Integer.MAX_VALUE
                    || bottom > Integer.MAX_VALUE
                    || right - left > Integer.MAX_VALUE
                    || bottom - top > Integer.MAX_VALUE)
                throw new IllegalArgumentException("线段超出布局坐标范围");
            // 仅作保守包络；消费者必须使用line端点生成四角，不能绘制此box。
            return new LayoutBox((int) left, (int) top, (int) (right - left), (int) (bottom - top));
        }
    }

    /** IMAGE的呈现分支由控件定义，backend仅依据资源事实选择；null表示该分支留空。 */
    public final PaintPlan pendingPaint, failedPaint, readyOverlay;

    /** 只引用本计划中先执行的IMAGE不可变命令；延后overlay复用它已作出的选择，不重画原图。 */
    public final PaintCommand branchSource;

    private PaintCommand(
            Kind kind,
            LayoutBox box,
            LayoutBox clip,
            int color,
            TextLayout text,
            ImagePlacement image,
            PaintPlan pendingPaint,
            PaintPlan failedPaint,
            PaintPlan readyOverlay) {
        this(kind, box, clip, color, text, image, pendingPaint, failedPaint, readyOverlay, null);
    }

    private PaintCommand(
            Kind kind,
            LayoutBox box,
            LayoutBox clip,
            int color,
            TextLayout text,
            ImagePlacement image,
            PaintPlan pendingPaint,
            PaintPlan failedPaint,
            PaintPlan readyOverlay,
            PaintCommand branchSource) {
        this(
                kind,
                box,
                clip,
                color,
                text,
                image,
                pendingPaint,
                failedPaint,
                readyOverlay,
                branchSource,
                null);
    }

    private PaintCommand(
            Kind kind,
            LayoutBox box,
            LayoutBox clip,
            int color,
            TextLayout text,
            ImagePlacement image,
            PaintPlan pendingPaint,
            PaintPlan failedPaint,
            PaintPlan readyOverlay,
            PaintCommand branchSource,
            Line line) {
        this(kind, box, clip, color, text, image, pendingPaint, failedPaint, readyOverlay, branchSource, line, null);
    }

    private PaintCommand(Kind kind, LayoutBox box, LayoutBox clip, int color, TextLayout text,
            ImagePlacement image, PaintPlan pendingPaint, PaintPlan failedPaint, PaintPlan readyOverlay,
            PaintCommand branchSource, Line line, club.heiqi.uilib.ui.scene.video.VideoPlacement video) {
        this(kind,box,clip,color,text,image,pendingPaint,failedPaint,readyOverlay,branchSource,line,video,null,SceneTransform.IDENTITY,SceneClipRegion.UNBOUNDED);
    }

    private PaintCommand(Kind kind, LayoutBox box, LayoutBox clip, int color, TextLayout text, ImagePlacement image,
            PaintPlan pendingPaint, PaintPlan failedPaint, PaintPlan readyOverlay, PaintCommand branchSource, Line line,
            club.heiqi.uilib.ui.scene.video.VideoPlacement video, SceneMesh mesh, SceneTransform transform, SceneClipRegion shapeClip) {
        this(kind,box,clip,color,text,image,pendingPaint,failedPaint,readyOverlay,branchSource,line,video,mesh,transform,shapeClip,kind==Kind.PATH);
    }
    private PaintCommand(Kind kind, LayoutBox box, LayoutBox clip, int color, TextLayout text, ImagePlacement image,
            PaintPlan pendingPaint, PaintPlan failedPaint, PaintPlan readyOverlay, PaintCommand branchSource, Line line,
            club.heiqi.uilib.ui.scene.video.VideoPlacement video, SceneMesh mesh, SceneTransform transform, SceneClipRegion shapeClip,boolean sceneGeometry) {
        this.sceneGeometry=sceneGeometry;this.mesh=mesh;this.transform=Objects.requireNonNull(transform);this.shapeClip=Objects.requireNonNull(shapeClip);
        effectiveClip=clip==null?shapeClip:shapeClip.intersect(SceneClipRegion.rectangle(clip).transformed(transform));
        this.video = video;
        this.line = line;
        this.kind = kind;
        this.box = Objects.requireNonNull(box, "box");
        this.clip = clip;
        this.color = color;
        this.text = text;
        this.image = image;
        this.pendingPaint = pendingPaint;
        this.failedPaint = failedPaint;
        this.readyOverlay = readyOverlay;
        this.branchSource = branchSource;
    }

    /** 当前命令的页面裁剪，包含原局部矩形clip经过实际矩阵后的精确区域。 */
    public SceneClipRegion effectiveClip(){return effectiveClip;}
    public PaintCommand withGeometry(SceneTransform outer,SceneClipRegion externalClip){
        return withGeometry(outer,externalClip,new java.util.IdentityHashMap<>());
    }
    PaintCommand withGeometry(SceneTransform outer,SceneClipRegion externalClip,java.util.Map<PaintCommand,PaintCommand> mapped){
        if(outer.identity()&&externalClip.unbounded()&&sceneGeometry)return this;
        PaintCommand existing=mapped.get(this);if(existing!=null)return existing;
        PaintPlan pending=pendingPaint==null?null:pendingPaint.transformed(outer,externalClip,mapped);
        PaintPlan failed=failedPaint==pendingPaint?pending:failedPaint==null?null:failedPaint.transformed(outer,externalClip,mapped);
        PaintPlan ready=readyOverlay==null?null:readyOverlay.transformed(outer,externalClip,mapped);
        PaintCommand source=branchSource==null?null:branchSource.withGeometry(outer,externalClip,mapped);
        SceneTransform combined=outer.multiply(transform);SceneClipRegion region=shapeClip.transformed(outer).intersect(externalClip);
        LayoutBox localClip=clip;
        // 原单位坐标+矩形裁剪继续原值型合同；普通节点不用新geometry载荷才能正确回放或平移。
        if(combined.identity()&&(region.unbounded()||region.rectangular())){
            if(!region.unbounded())localClip=clip==null?region.bounds():clip.intersect(region.bounds());
            region=SceneClipRegion.UNBOUNDED;
        }
        PaintCommand result=new PaintCommand(kind,box,localClip,color,text,image,pending,failed,ready,source,line,video,mesh,combined,region,true);
        mapped.put(this,result);return result;
    }
    /** 网格已按本次曲线精度准备，顶点与box均在当前命令的局部布局frame中。 */
    public static PaintCommand path(SceneMesh mesh,LayoutBox clip,int color){
        Objects.requireNonNull(mesh,"路径网格");LayoutBox bounds=SceneClipRegion.of(mesh).bounds();
        if(bounds==null)throw new IllegalArgumentException("路径超出布局坐标范围");
        return new PaintCommand(Kind.PATH,bounds,clip,color,null,null,null,null,null,null,null,null,mesh,SceneTransform.IDENTITY,SceneClipRegion.UNBOUNDED);
    }

    public static PaintCommand image(LayoutBox box, LayoutBox clip, ImagePlacement image) {
        return image(box, clip, image, (TextLayout) null);
    }

    /** 旧手工载荷不声明占位；alt仅作文字元数据，backend不据此另造UI。 */
    public static PaintCommand image(
            LayoutBox box, LayoutBox clip, ImagePlacement image, TextLayout alt) {
        return new PaintCommand(
                Kind.IMAGE,
                box,
                clip,
                0xFFFFFFFF,
                alt,
                Objects.requireNonNull(image, "image"),
                null,
                null,
                null);
    }

    public static PaintCommand image(
            LayoutBox box,
            LayoutBox clip,
            ImagePlacement image,
            PaintPlan pendingPaint,
            PaintPlan failedPaint,
            PaintPlan readyOverlay) {
        return new PaintCommand(
                Kind.IMAGE,
                box,
                clip,
                0xFFFFFFFF,
                null,
                Objects.requireNonNull(image, "image"),
                pendingPaint,
                failedPaint,
                readyOverlay);
    }

    /** 全页内容结束后再投影备用overlay；主图ready或未执行时不投影。资源仅遍历所带片段。 */
    public static PaintCommand imageBranchOverlay(
            PaintCommand source, PaintPlan pending, PaintPlan failed) {
        if (Objects.requireNonNull(source, "source").kind != Kind.IMAGE)
            throw new IllegalArgumentException("分支投影必须引用IMAGE");
        return new PaintCommand(
                Kind.IMAGE_BRANCH_OVERLAY,
                source.box,
                null,
                0,
                null,
                null,
                pending,
                failed,
                null,
                source);
    }

    /** 封闭端点/线宽事实；不持有live节点，不以clip裁短后重新计算方向。 */
    public static PaintCommand line(
            float x0, float y0, float x1, float y1, float thickness, LayoutBox clip, int color) {
        Line line = new Line(x0, y0, x1, y1, thickness);
        return new PaintCommand(
                Kind.LINE, line.bounds(), clip, color, null, null, null, null, null, null, line);
    }

    /** 视频是同一有序计划的封闭值，不读取播放会话或包含 GPU 句柄。 */
    public static PaintCommand video(LayoutBox box, LayoutBox clip, club.heiqi.uilib.ui.scene.video.VideoPlacement video) {
        return new PaintCommand(Kind.VIDEO, box, clip, 0xFFFFFFFF, null, null, null, null, null, null, null,
                Objects.requireNonNull(video, "video"));
    }

    PaintCommand presented(int dx, int dy, float opacity, LayoutBox limit,
            java.util.Map<PaintCommand, PaintCommand> mapped) {
        PaintCommand existing = mapped.get(this);
        if (existing != null) return existing;
        boolean geometric=!transform.identity()||!shapeClip.unbounded();
        LayoutBox moved = geometric?box:new LayoutBox(box.x + dx, box.y + dy, box.width, box.height);
        LayoutBox clipped = geometric?clip:clip == null ? null : new LayoutBox(clip.x + dx, clip.y + dy, clip.width, clip.height);
        if (!geometric && limit != null) clipped = clipped == null ? limit : clipped.intersect(limit);
        PaintPlan pending = pendingPaint == null ? null : pendingPaint.presented(dx, dy, opacity, limit, mapped);
        PaintPlan failed = failedPaint == pendingPaint ? pending
                : failedPaint == null ? null : failedPaint.presented(dx, dy, opacity, limit, mapped);
        PaintPlan ready = readyOverlay == null ? null : readyOverlay.presented(dx, dy, opacity, limit, mapped);
        PaintCommand branch = branchSource == null ? null : branchSource.presented(dx, dy, opacity, limit, mapped);
        Line movedLine = geometric?line:line == null ? null : new Line(line.x0 + dx, line.y0 + dy, line.x1 + dx, line.y1 + dy, line.thickness);
        PaintCommand result = new PaintCommand(kind, moved, clipped,
                club.heiqi.uilib.ui.scene.motion.SceneMotion.alpha(color, opacity),
                text == null ? null : text.withOpacity(opacity), image == null || geometric ? image : image.translated(dx, dy),
                pending, failed, ready, branch, movedLine, video == null || geometric ? video : video.translated(dx, dy),
                mesh==null||geometric?mesh:mesh.transformed(SceneTransform.translation(dx,dy)),
                geometric?SceneTransform.translation(dx,dy).multiply(transform):transform,
                geometric?shapeClip.transformed(SceneTransform.translation(dx,dy)).intersect(SceneClipRegion.rectangle(limit)):shapeClip,sceneGeometry);
        mapped.put(this, result);
        return result;
    }

    public static PaintCommand rect(LayoutBox box, LayoutBox clip, int color) {
        return new PaintCommand(Kind.RECT, box, clip, color, null, null, null, null, null);
    }

    public static PaintCommand text(LayoutBox box, LayoutBox clip, TextLayout text) {
        return new PaintCommand(
                Kind.TEXT, box, clip, text.style.color, text, null, null, null, null);
    }
}

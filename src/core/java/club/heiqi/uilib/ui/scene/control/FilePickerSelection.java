package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.control.FilePickerHistory.Location;
import club.heiqi.uilib.ui.scene.control.FilePickerDirectory.Scope;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 一次选择的唯一状态与目录工作 owner；界面只消费这些信号并发布选择意图。 */
final class FilePickerSelection {
    final SceneEditState path,search;
    final Signal<String> extension,searchTerm;
    final Signal<FilePickerOrder> order;
    final Signal<List<FilePickerDirectory.Entry>> entries;
    final Signal<String> message;
    final Signal<Path> directory, selected, revealTarget;
    final Signal<Boolean> busy, showing, roots, previous, next;
    final Signal<Long> page, revision;
    final Signal<Integer> scroll,backCount,forwardCount;
    java.util.function.IntSupplier scrollPosition=()->0;
    private final FilePickerHistory history=new FilePickerHistory();
    private FilePickerQuery readingQuery=FilePickerQuery.first();
    private Scope readingScope;
    private boolean listingPending;
    private final Consumer<Path> onSelected;
    private final Runnable onCancelled;
    private FilePickerDirectory reader;
    private SceneEditState.Submission pendingPath;
    private long request;
    private boolean attached, finished;

    FilePickerSelection(ReactiveDomain domain, Path initialDirectory, Consumer<Path> onSelected, Runnable onCancelled) {
        this.onSelected = onSelected; this.onCancelled = onCancelled;
        Path initial = initialDirectory.toAbsolutePath().normalize();readingScope=Scope.directory(initial);
        directory = domain.signal(initial); selected = domain.signal(null);revealTarget=domain.signal(null);
        path = new SceneEditState(domain, initial.toString());search=new SceneEditState(domain,"");
        extension=domain.signal("");searchTerm=domain.signal("");order=domain.signal(FilePickerOrder.NAME);
        scroll=domain.signal(0);backCount=domain.signal(0);forwardCount=domain.signal(0);
        entries = domain.signal(Collections.emptyList());
        message = domain.signal("正在读取目录…");
        busy = domain.signal(false); showing = domain.signal(true);
        roots = domain.signal(false); previous=domain.signal(false); next = domain.signal(false);
        page = domain.signal(0L); revision = domain.signal(0L);
    }
    void attach() {
        attached = true; finished = false; showing.set(true);
        reader = new FilePickerDirectory();
        browse(directory.get(), FilePickerQuery.first());
    }
    void detach() {
        attached = false; request++;listingPending=false;
        if (reader != null) { reader.close(); reader = null; }
    }
    private boolean active() { return attached && !finished; }
    boolean waiting() { return active() && busy.get(); }
    /** 只在宿主正式输入帧领取；工作线程不访问选择信号或节点。 */
    void receiveCompletion() {
        if (!active()) return;
        FilePickerDirectory.Result result = reader.take();
        if (result != null) receive(result);
    }
    private long begin(String status){return begin(status,true);}
    private long begin(String status,boolean listingChanges) {
        if (pendingPath != null) path.reject(pendingPath, "路径导航已更改");
        pendingPath = null;
        long id = ++request;
        busy.set(true);
        if(listingChanges){
            if(!listingPending)history.capture(currentSelection(),scrollPosition.getAsInt());
            revealTarget.set(null);revision.set(id);previous.set(false);next.set(false);selected.set(null);entries.set(Collections.emptyList());
        }
        listingPending=listingChanges;message.set(status);
        return id;
    }
    private void browse(Path target, FilePickerQuery query) {
        read(Scope.directory(target),query,"正在读取目录…",true,false,null);
    }
    private void read(Scope scope,FilePickerQuery query,String status,boolean navigation,boolean selectLocated,SceneEditState.Submission submission){
        if(!active())return;
        if(navigation)history.beginNavigation();
        long id=begin(status);readingScope=scope;
        readingQuery=query.filter(searchTerm.get(),extension.get()).order(order.get());
        pendingPath=submission;if(submission==null)path.reset(scope.pathText());
        reader.readScope(id,scope,readingQuery,selectLocated);
    }
    void activate(FilePickerDirectory.Entry entry) {
        if (entry.directory) browse(entry.path, FilePickerQuery.first());
        else {
            selected.set(entry.path); path.reset(entry.path.toString());
            message.set("已选择：" + entry.name());
        }
    }
    void go(SceneEditState.Submission submission) {
        if (!active()) return;
        try {
            Scope target=Scope.path(readingScope,parse(submission.text));
            search.reset("");searchTerm.set("");extension.set("");order.set(FilePickerOrder.NAME);
            read(target,FilePickerQuery.first(),"正在读取目录…",true,true,submission);
        } catch (IllegalArgumentException | SecurityException failure) {
            invalidPath(submission, failure);
        }
    }
    private void invalidPath(SceneEditState.Submission submission, RuntimeException failure) {
        begin("无法访问路径：" + failure.getMessage(),false);
        reader.cancelRead(); busy.set(false);
        path.reject(submission, message.get());
    }
    private Path parse(String raw){
        if(raw.isEmpty())throw new IllegalArgumentException("请输入目录或文件路径");
        return raw.regionMatches(true,0,"file:",0,5)?Paths.get(URI.create(raw)):readingScope.fileSystem().getPath(raw);
    }
    private Path resolve(String raw) {
        Path result=parse(raw);
        return (result.isAbsolute() ? result : directory.get().resolve(result)).toAbsolutePath().normalize();
    }
    void showRoots(long targetPage) {browseRoots(FilePickerQuery.first());}
    private void browseRoots(FilePickerQuery query) {
        read(Scope.rootsAt(directory.get()),query,"正在读取根目录…",true,false,null);
    }
    void navigate(Path directory){browse(directory,FilePickerQuery.first());}
    void history(int direction){
        if(!active()||busy.get())return;Location restoring=history.request(direction);if(restoring==null)return;
        search.reset(restoring.query.text);searchTerm.set(restoring.query.text);extension.set(restoring.query.extensions);order.set(restoring.query.order);
        Scope scope=restoring.roots?Scope.rootsAt(restoring.directory):Scope.directory(restoring.directory);
        read(scope,restoring.query,"正在返回浏览位置…",false,false,null);
    }
    void up() {
        if (!active()) return;
        read(Scope.parent(readingScope),FilePickerQuery.first(),"正在读取上级目录…",true,false,null);
    }
    void query(SceneEditState.Submission submission){if(search.snapshot().get().composing)return;if(search.accept(submission,submission.text)){searchTerm.set(submission.text.trim());refresh();}}
    void clearQuery(){search.reset("");searchTerm.set("");refresh();}
    void cycleOrder(){FilePickerOrder[] values=FilePickerOrder.VALUES;int i=java.util.Arrays.asList(values).indexOf(order.get());order.set(values[(i+1)%values.length]);refresh();}
    void refresh() {
        FilePickerQuery changed=FilePickerQuery.first().filter(searchTerm.get(),extension.get()).order(order.get());
        boolean same=readingQuery.text.equals(changed.text)&&readingQuery.extensions.equals(changed.extensions)&&readingQuery.order==changed.order;
        if(!listingPending||!same)history.refresh(!listingPending&&same?history.snapshot(currentSelection(),scrollPosition.getAsInt()):null);
        // 读取期间的新查询沿当前请求范围；不借最后完成的列表重建导航意图。
        read(readingScope,same?readingQuery:changed,"正在刷新当前范围…",false,false,null);
    }
    void turn(int direction) {
        if (!active() || busy.get()) return;
        if(entries.get().isEmpty()||direction>0&&!next.get()||direction<0&&!previous.get())return;
        List<FilePickerDirectory.Entry> shown=entries.get();
        FilePickerQuery query=direction>0?FilePickerQuery.after(page.get()+1,shown.get(shown.size()-1))
                :FilePickerQuery.before(page.get()-1,shown.get(0));
        read(readingScope,query,"正在读取目录…",true,false,null);
    }
    void confirm() {
        if (!active() || busy.get()) return;
        SceneEditState.Submission submission = path.request(true);
        try {
            Path target = resolve(submission.text);
            long id = begin("正在核验文件…",false);
            pendingPath = submission; reader.verify(id, target);
        } catch (IllegalArgumentException | SecurityException failure) { invalidPath(submission, failure); }
    }
    private void receive(FilePickerDirectory.Result result) {
        if (!active() || result.request != request) return;
        busy.set(false);listingPending=false;
        SceneEditState.Submission submission = pendingPath; pendingPath = null;
        if (result.error != null) {
            String reason="无法访问路径："+result.error;
            history.beginNavigation();message.set(submission==null||path.reject(submission,reason)?reason:"路径已更改，请重新确认。");
        } else if (result.verified != null) {
            // 编辑状态已拥有当前提交判断；旧核验不能关闭用户后来编辑的路径。
            if(submission==null||!path.accept(submission,result.verified.toString())){
                message.set("路径已更改，请重新确认。");return;
            }
            if(!active()||result.request!=request)return;
            finish(); onSelected.accept(result.verified);
        } else applyListing(result, submission);
    }
    private void applyListing(FilePickerDirectory.Result result, SceneEditState.Submission submission) {
        String normalized=result.directory==null?null:(result.selected==null?result.directory:result.selected).toString();
        if(submission!=null&&normalized!=null&&!path.accept(submission,normalized)){
            message.set("路径已更改，请重新前往或确认。");return;
        }
        Location restore=history.acceptListing(result);
        backCount.set(history.backCount());forwardCount.set(history.forwardCount());
        publishListing(result,submission,normalized);
        restoreSelection(result,restore);
        int position=restore==null?0:restore.scroll;
        // locate实际可能执行AROUND；历史保存worker已执行边界，不保存前往时的FIRST意图。
        readingQuery=result.query;readingScope=result.roots?Scope.rootsAt(directory.get()):Scope.directory(result.directory);scroll.set(position);
        history.current(directory.get(),result.roots,result.query,selected.get(),position);
        message.set(listingMessage(result));
        // 仅显式路径定位发布显露意图；历史与刷新沿原滚动位置，不追随选择。
        if(result.selected!=null)revealTarget.set(result.selected);
    }
    private void publishListing(FilePickerDirectory.Result result,SceneEditState.Submission submission,String normalized){
        roots.set(result.roots);page.set(result.page);previous.set(result.previous);next.set(result.next);
        entries.set(result.entries);selected.set(result.selected);
        if(result.roots){path.synchronizeExternal("");return;}
        if(result.directory==null)return;
        directory.set(result.directory);
        if(submission==null)path.synchronizeExternal(normalized);
    }
    private void restoreSelection(FilePickerDirectory.Result result,Location restore){
        if(restore==null||restore.selected==null)return;
        Path candidate=restore.selected;
        if(result.entries.stream().anyMatch(entry->entry.path.equals(candidate))){selected.set(candidate);path.reset(candidate.toString());}
    }
    private String listingMessage(FilePickerDirectory.Result result) {
        if (result.selected != null) return "已选择：" + result.selected.getFileName();
        if(result.entries.isEmpty()&&(!searchTerm.get().isEmpty()||!extension.get().isEmpty()))return "没有匹配项，可清除搜索或切换所有文件。";
        if(result.entries.isEmpty())return result.page>0?"当前页内容已变化，请刷新或返回上一页。":result.roots?"没有可用根目录。":"空目录";
        return (result.roots ? "根目录 / 盘符" : "单击目录进入，选择文件后确认。")
                + "（本页 " + result.entries.size() + " 项）";
    }
    Path currentSelection(){
        Path chosen=selected.get();
        if(chosen==null)return null;
        try{return chosen.equals(resolve(path.snapshot().get().text))?chosen:null;}
        catch(IllegalArgumentException|SecurityException invalid){return null;}
    }
    String targetLabel(){
        String raw=path.snapshot().get().text;
        if(raw.isEmpty())return "尚未选择文件";
        try{
            Path target=resolve(raw);
            if(target.equals(directory.get())&&currentSelection()==null)return "尚未选择文件";
            String name=target.getFileName()==null?target.toString():target.getFileName().toString();
            return (currentSelection()==null?"待核验：":"将打开：")+name;
        }catch(IllegalArgumentException|SecurityException invalid){return "请检查待打开的文件路径";}
    }
    String feedback(){
        // 当前请求及字段拒绝已有明确反馈，不能被旧选择的通用提示遮住。
        if(busy.get()||path.snapshot().get().error!=null)return message.get();
        if(selected.get()!=null&&currentSelection()==null)return "路径已更改，确认时将核验当前输入。";
        return message.get();
    }
    private void finish() { finished = true; showing.set(false); detach(); }
    void cancel() { if (active()) { finish(); onCancelled.run(); } }
}

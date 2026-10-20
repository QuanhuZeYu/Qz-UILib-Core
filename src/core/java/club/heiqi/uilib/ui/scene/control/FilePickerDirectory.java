package club.heiqi.uilib.ui.scene.control;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 文件事实只在本次挂载的单一工作线程读取；不持有节点，也不跨请求累计整个目录。 */
final class FilePickerDirectory implements AutoCloseable {
    static final int PAGE_SIZE = 64;
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();

    /** 当前请求范围；未决路径及相对基准只在同一worker解析，不把旧列表当新目标。 */
    static final class Scope {
        private enum Kind { DIRECTORY, PATH, ROOTS, PARENT }
        private final Kind kind;
        private final Path path;
        private final Scope base;
        private final long parentCount;
        private Scope(Kind kind,Path path,Scope base){this(kind,path,base,0);}
        private Scope(Kind kind,Path path,Scope base,long parentCount){this.kind=kind;this.path=path;this.base=base;this.parentCount=parentCount;}
        static Scope directory(Path path){return new Scope(Kind.DIRECTORY,path.toAbsolutePath().normalize(),null);}
        static Scope path(Scope base,Path path){
            if(path.isAbsolute()||base==null)return new Scope(Kind.PATH,path,null);
            long parents=0;
            // 未领取的相对go替换同一基准上的待解析草稿，不把已取消草稿接成导航历史。
            while(base.base!=null){
                parents=base.kind==Kind.PARENT?addParents(parents,base.parentCount):0;
                base=base.base;
            }
            if(parents>0)base=parents(base,parents);
            return new Scope(Kind.PATH,path,base);
        }
        static Scope rootsAt(Path path){return new Scope(Kind.ROOTS,path,null);}
        static Scope parent(Scope base){return parents(base,1);}
        private static long addParents(long left,long right){return left>Long.MAX_VALUE-right?Long.MAX_VALUE:left+right;}
        private static Scope parents(Scope base,long count){
            if(base.kind==Kind.PARENT){count=addParents(count,base.parentCount);base=base.base;}
            if(base.kind==Kind.ROOTS)return base;
            if(base.kind==Kind.DIRECTORY){
                Path current=base.path;
                for(long i=0;i<count;i++){Path parent=current.getParent();if(parent==null)return rootsAt(current);current=parent;}
                return directory(current);
            }
            return new Scope(Kind.PARENT,null,base,count);
        }
        FileSystem fileSystem(){return path==null?base.fileSystem():path.getFileSystem();}
        String pathText(){return path!=null&&path.isAbsolute()&&kind!=Kind.ROOTS?path.toString():"";}
        private Path directoryPath(){return kind==Kind.PATH?path.getParent():path;}
        private Scope resolve() throws IOException {
            if(kind==Kind.DIRECTORY||kind==Kind.ROOTS)return this;
            Scope context=base==null?null:base.resolve();
            if(kind==Kind.PARENT){
                if(context.kind==Kind.ROOTS)return context;
                Path current=context.directoryPath();
                for(long i=0;i<parentCount;i++){Path parent=current.getParent();if(parent==null)return rootsAt(current);current=parent;}
                return directory(current);
            }
            Path target=(context==null?path:context.directoryPath().resolve(path)).toAbsolutePath().normalize();
            BasicFileAttributes attributes=Files.readAttributes(target,BasicFileAttributes.class);
            if(attributes.isDirectory())return directory(target);
            if(!attributes.isRegularFile())throw new IOException("请选择目录或普通文件");
            return new Scope(Kind.PATH,target,null);
        }
    }

    static final class Entry {
        final Path path;
        final boolean directory;
        final long size,modified;
        Entry(Path path,boolean directory){this(path,directory,-1,-1);}
        Entry(Path path,boolean directory,long size,long modified){this.path=path;this.directory=directory;this.size=size;this.modified=modified;}
        String type(){if(directory)return "目录";String name=name();int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(java.util.Locale.ROOT);}
        String name() { return path.getFileName() == null ? path.toString() : path.getFileName().toString(); }
    }
    static final class Result {
        final long request, page;
        final Path directory, selected, verified;
        final boolean roots, previous, next;
        final List<Entry> entries;
        final String error;
        final FilePickerQuery query;
        Result(long request, long page, Path directory, boolean roots, boolean previous, boolean next,
                List<Entry> entries, Path selected, Path verified, String error,FilePickerQuery query) {
            this.request = request; this.page = page; this.directory = directory;
            this.roots = roots; this.previous=previous; this.next = next; this.entries = Collections.unmodifiableList(entries);
            this.selected = selected; this.verified = verified; this.error = error;this.query=query;
        }
    }
    private interface Read { Result run() throws IOException; }
    private final ThreadPoolExecutor worker;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Result> completion = new AtomicReference<>();
    private Future<?> latest;

    FilePickerDirectory() {
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, "uilib-file-picker-" + WORKER_IDS.incrementAndGet());
            thread.setDaemon(true); return thread;
        });
    }
    void browse(long request, Path directory, FilePickerQuery query) {
        submit(request, () -> list(request, directory, query, null));
    }
    void locate(long request, Path path) {
        readScope(request,Scope.path(null,path),FilePickerQuery.first(),true);
    }
    void readScope(long request,Scope scope,FilePickerQuery query,boolean selectLocated){
        submit(request,()->{
            checkCancelled();Scope resolved=scope.resolve();checkCancelled();
            if(resolved.kind==Scope.Kind.ROOTS)
                return collect(request,null,true,query,resolved.fileSystem().getRootDirectories().iterator(),null);
            if(resolved.kind==Scope.Kind.DIRECTORY)return list(request,resolved.path,query,null);
            Path parent=resolved.path.getParent();
            if(parent==null)throw new IOException("文件没有可浏览的上级目录");
            // 后续查询只采用定位得到的父范围，不重新选择先前请求的文件。
            FilePickerQuery actual=selectLocated
                    ?FilePickerQuery.around(entry(resolved.path,false)).filter(query.text,query.extensions).order(query.order):query;
            return list(request,parent,actual,selectLocated?resolved.path:null);
        });
    }
    void verify(long request, Path path) {
        submit(request, () -> {
            if (!Files.readAttributes(path, BasicFileAttributes.class).isRegularFile())
                throw new IOException("请选择存在的普通文件");
            return new Result(request, 0, null, false, false, false, Collections.emptyList(), null, path, null,null);
        });
    }
    void roots(long request, FileSystem fileSystem, FilePickerQuery query) {
        submit(request, () -> collect(request, null, true, query, fileSystem.getRootDirectories().iterator(), null));
    }
    private Result list(long request, Path directory, FilePickerQuery query, Path selected) throws IOException {
        checkCancelled();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            return collect(request, directory, false, query, stream.iterator(), selected);
        }
    }
    private Result collect(long request,Path directory,boolean roots,FilePickerQuery query,Iterator<Path> paths,Path selected){
        Candidates candidates=new Candidates(query);
        while(paths.hasNext()){
            checkCancelled();
            Entry entry=entry(paths.next().toAbsolutePath().normalize(),roots);
            if(query.accepts(entry))candidates.accept(entry);
        }
        checkCancelled();
        return candidates.result(request,directory,roots,selected);
    }

    private static Entry entry(Path path,boolean roots){
        try{
            BasicFileAttributes attributes=Files.readAttributes(path,BasicFileAttributes.class);
            return new Entry(path,roots||attributes.isDirectory(),attributes.size(),attributes.lastModifiedTime().toMillis());
        }catch(IOException|SecurityException inaccessible){
            boolean directory=roots;
            try{directory|=Files.isDirectory(path);}catch(SecurityException unknownKind){/* 未知属性保留，不伪造为0。 */}
            return new Entry(path,directory);
        }
    }

    /** 普通页保留PAGE_SIZE+1项；定位页另保留最多PAGE_SIZE-1个前驱，总候选最多两页。 */
    private static final class Candidates {
        private final FilePickerQuery query;
        private final Comparator<Entry> order;
        private final boolean backwards;
        private final PriorityQueue<Entry> entries, preceding;
        private long before;
        private boolean opposite;

        Candidates(FilePickerQuery query){
            this.query=query;order=query.order;backwards=query.direction==FilePickerQuery.Direction.PREVIOUS;
            entries=new PriorityQueue<>(PAGE_SIZE+1,backwards?order:order.reversed());
            preceding=query.direction==FilePickerQuery.Direction.AROUND?new PriorityQueue<>(PAGE_SIZE,order):null;
        }
        void accept(Entry entry){
            int compared=query.boundary==null?1:order.compare(entry,query.boundary);
            if(compared<0){
                before++;
                if(preceding!=null){
                    if(preceding.size()<PAGE_SIZE-1)preceding.add(entry);
                    else if(order.compare(entry,preceding.peek())>0){preceding.poll();preceding.add(entry);}
                }
            }
            if(!eligible(compared)){opposite=true;return;}
            if(entries.size()<PAGE_SIZE+1){entries.add(entry);return;}
            boolean nearer=backwards?order.compare(entry,entries.peek())>0:order.compare(entry,entries.peek())<0;
            if(nearer){entries.poll();entries.add(entry);}
        }
        private boolean eligible(int compared){
            if(query.boundary==null)return true;
            if(backwards)return compared<0;
            return compared>0||query.direction==FilePickerQuery.Direction.AROUND&&compared==0;
        }
        Result result(long request,Path directory,boolean roots,Path selected){
            List<Entry> shown=new ArrayList<>(entries);shown.sort(order);
            if(preceding!=null)return locatedResult(request,directory,roots,selected,shown);
            boolean more=shown.size()>PAGE_SIZE;
            if(more)shown.remove(backwards?0:shown.size()-1);
            return new Result(request,query.page,directory,roots,backwards?more:opposite,backwards?opposite:more,shown,selected,null,null,query);
        }
        private Result locatedResult(long request,Path directory,boolean roots,Path selected,List<Entry> following){
            // 目标在页内保留其全局位置，不能以目标开头再冒用自然页号。
            List<Entry> prefix=new ArrayList<>(preceding);prefix.sort(order);
            int count=(int)(before%PAGE_SIZE),remaining=PAGE_SIZE-count;
            List<Entry> shown=new ArrayList<>(PAGE_SIZE);
            shown.addAll(prefix.subList(prefix.size()-count,prefix.size()));
            shown.addAll(following.subList(0,Math.min(remaining,following.size())));
            return new Result(request,before/PAGE_SIZE,directory,roots,before>count,following.size()>remaining,shown,selected,null,null,query);
        }
    }
    void cancelRead() {
        if (latest != null) latest.cancel(true);
        latest = null;
        worker.getQueue().clear();
        completion.set(null);
    }
    private void submit(long request, Read read) {
        if (closed.get()) return;
        cancelRead();
        latest = worker.submit(() -> {
            try {
                Result result = read.run(); checkCancelled(); publish(result);
            } catch (CancellationException ignored) {
                // 取消不发布错误，也不让旧目录恢复当前界面。
            } catch (IOException | SecurityException | IllegalArgumentException | DirectoryIteratorException failure) {
                if (closed.get() || Thread.currentThread().isInterrupted()) return;
                String reason = failure.getMessage();
                if (reason == null || reason.isEmpty()) reason = failure.getClass().getSimpleName();
                publish(new Result(request, 0, null, false, false, false, Collections.emptyList(), null, null, reason,null));
            }
        });
    }
    private void checkCancelled() {
        if (closed.get() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
    private void publish(Result result) {
        if (!closed.get()) completion.set(result);
    }
    /** 只由宿主正式输入帧领取。direct 状态域不能把工作线程变成 UI 操作线程。 */
    Result take() {
        Result result = completion.getAndSet(null);
        return closed.get() ? null : result;
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        completion.set(null);
        if (latest != null) latest.cancel(true);
        latest = null;
        worker.shutdownNow();
        // DirectoryStream 由工作调用的 try-with-resources 关闭；渲染线程不等待文件系统 I/O。
    }
}

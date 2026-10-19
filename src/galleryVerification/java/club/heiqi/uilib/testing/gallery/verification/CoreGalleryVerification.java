package club.heiqi.uilib.testing.gallery.verification;

import club.heiqi.uilib.testing.gallery.GalleryChecks;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 与广场页面共用检查目录的唯一无窗口启动入口。 */
public final class CoreGalleryVerification {
    private CoreGalleryVerification() {}
    public static void main(String[] args) throws Exception {
        GalleryChecks service = new CoreGalleryChecks();
        List<String> selected = new ArrayList<>();
        for (GalleryChecks.Check check : service.checks()) {
            if (args.length == 1 && "--list".equals(args[0])) System.out.println(check.id + "	" + check.title);
            selected.add(check.id);
        }
        if (args.length == 1 && "--list".equals(args[0])) return;
        if (args.length > 0) selected = Arrays.asList(args);
        String output = System.getProperty("uilib.verification.report");
        Path directory = output == null ? null : Files.createDirectories(Paths.get(output));
        StringBuilder summary = new StringBuilder("check\tstatus\telapsedNanos\n");
        AssertionError failure = null;
        for (String id : selected) {
            GalleryChecks.Result result = service.run(id);
            String status = result.passed() ? "PASS" : "FAIL";
            System.out.println("GALLERY_CHECK " + id + " " + status + " nanos=" + result.elapsedNanos);
            System.out.print(result.details);
            summary.append(id).append('	').append(status).append('	').append(result.elapsedNanos).append('\n');
            if (directory != null) Files.write(directory.resolve(id + ".txt"), result.details.getBytes(StandardCharsets.UTF_8));
            if (!result.passed()) {
                if (failure == null) failure = new AssertionError("核心验证失败：" + id, result.failure);
                else failure.addSuppressed(result.failure);
            }
        }
        if (directory != null) Files.write(directory.resolve("results.tsv"), summary.toString().getBytes(StandardCharsets.UTF_8));
        if (failure != null) throw failure;
    }
}

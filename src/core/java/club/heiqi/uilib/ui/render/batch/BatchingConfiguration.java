package club.heiqi.uilib.ui.render.batch;

import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigKey;

/** 共享合批选项声明；生效值与所属渲染资源由宿主配置会话持有。 */
public final class BatchingConfiguration {
    public static final ConfigKey<Boolean> KEY =
            new ConfigKey<>("render.aggressiveBatching", ConfigCodecs.BOOLEAN, false)
                    .withComment("激进合批默认关闭；可能改变重叠文字、阴影和透明图元的覆盖顺序，设置页应用或文件重载后生效。");

    private BatchingConfiguration() {}
}

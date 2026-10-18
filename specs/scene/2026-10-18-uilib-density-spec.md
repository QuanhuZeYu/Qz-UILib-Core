# 控件尺寸角色与密度主题

[成熟度路线](<../proposals/2026-10-18-uilib-maturity-roadmap.md>)的尺寸与密度阶段面向数据表、工具面板及普通表单：页面选择紧凑或舒适密度，局部控件选择小、常规或大尺寸；继承的字号、内距、圆角、符号与图标间距一起更新。框架继续真实文字测量、原布局与唯一共同几何，明确的局部样式和业务草稿保持其含义。

采用[MUI Density](https://mui.com/material-ui/customization/density/)及[对应完整正文](https://raw.githubusercontent.com/mui/material-ui/master/docs/data/material/customization/density/density.md)的组件size、dense间距与theme默认值用法；采用[React Spectrum Provider](https://react-spectrum.adobe.com/v3/Provider.html)及[对应完整正文](https://raw.githubusercontent.com/adobe/react-spectrum/main/packages/@adobe/react-spectrum/docs/provider/Provider.mdx)的主题尺度、显式应用选择及局部覆盖行为。实现沿现有SceneTheme、ReadableSignal、Computed、Owner及正式控件，不增加主题树或样式注册中心。

## 1. 页面与局部声明

SceneTheme为不可变值，增加SceneDensity的COMPACT、COMFORTABLE和SceneControlSize的SMALL、REGULAR、LARGE。withDensity和withControlSize只替换所指默认角色。COMFORTABLE与REGULAR组合对当前默认主题、控件自然尺寸及字体预算保持恒等；旧工厂、参数和主题构造继续可用。

页面通过可追踪密度来源设置当前主题；当前ScenePageContext.theme同时派生主题模式与密度，自动滚动条也消费该来源。普通控件继续接现有theme参数，局部小按钮使用由父主题派生的SMALL来源。复合控件的内建按钮和弹层沿自己的当前主题来源；业务自定义cell/row工厂继续使用它明确捕获的主题，不由容器改写。

密度表达同类内容的排布紧凑程度；尺寸角色表达局部控件的强调和操作规模。两者共同决定继承的文字、内距和几何默认值，COMPACT减少相应占用，SMALL/REGULAR/LARGE有序表达默认规模。颜色模式与动效选择保持独立。字号与几何按当前基准解析，往返或重复选择不累计缩小。

## 2. 精确覆盖与默认值

框架区分控件主题默认值与作者声明。节点公共style、padding、gap及对应局部绑定表达作者值；其值等于默认值或为合法零值时仍保留这个意图。主题改变只更新继承的默认值，不在首挂或后续失效时反向覆盖作者值。普通裸SceneNode的现有默认样式和布局不变。

旧public SceneTheme构造传入的TextStyle、padding、gap是精确声明；withTextStyle同样表示完整、精确的文字样式覆盖。尺寸角色与密度只缩放仍继承的度量，明确字号不再被二次缩放。字体派生使用现TextStyle接口保留字族、候选、字形样式、效果和语言信息；作者后续更新局部Signal仍即时生效。withMotion只修改动效，所有主题复制保留密度、角色及来源事实，不通过当前值是否等于某个默认数字猜测来源。

角色不改写Size.fixed、fill、natural、bounds或已声明的列宽、缩进及滚动条绝对像素。自然尺寸继续使用真实字体行高与当前内距；明确的固定盒保持原布局合同，空间不足继续原裁剪、滚动和溢出策略。页面标题、业务内容边距及装饰图标的绝对声明也保持原意。

## 3. 一次解析，同一控件事实

有限的纯度量解析器按主题、控件族和尺寸角色给出有效TextStyle、padding、gap、radius、iconSize和iconGap。滑杆、滚动条与树的专用维度用所属名称表达。测量、文字预算、排列、绘制和命中消费同一有效结果；密度不通过子树仿射缩放实现。

Button/Label和带前缀图标的动作使用同一文字与图标预算；Field/Area使用原真实文字服务求自然高度；Icon、CheckBox、Switch、Radio保留自身形状比例。Slider的自然尺寸、轨道、滑块与命中共用同一Track。Scrollbar与ScrollArea外框消费同一有效条宽和最小滑块度量，显式Options.dimensions继续绝对像素。

SceneAppearance的现代按钮、编辑器主题和Surface继续保留各自当前基准，再应用同一角色解析和复制来源。普通按钮、现代动作和编辑器已有不同默认内距，不为统一解析改成同一个初值。Tooltip等框架默认面板也消费所属度量；纯颜色、动效或只影响画面的圆角变化不触发文字或行高度量。

Tabs改变标签度量时保留活动面板；Popup容量估算与实际行高分别沿原职责更新；Tree的默认缩进和标记槽随角色变化，显式像素继续原值。DataTable的列约束、真实单元格测量和业务行键保持原合同。

## 4. 有限集合的失效与状态

密度或角色改变影响文字、内距、图标预算时，Virtual、Popup、Tree和Table把当前有效布局度量作为原measurementCondition的一部分。显式estimatedRowHeight保持原估算含义；即使估算数字未变，实际布局条件变化仍使旧高度事实失效。

复用原按需子测量、有限行视图、reset及滚动锚点；当前准备行取得新实际高度，其余条目使用当前合法估算并沿原校正。纯颜色、动效和不影响占位的圆角不使屏外行高作废。不为切密度构建整份行节点，不让旧调用方增加必选度量参数。

焦点、字段草稿、选区、活动页签、选择与业务数据身份沿原Mount和逻辑键保持。共同几何更新画面、输入、方向导航和语义bounds；切换样式不另启输入或提交业务编辑。

## 5. 真实使用与验收

共享页面提供密度选择与局部尺寸对比，并可直接编辑、打开弹层、选择与提交。默认路径先可用，参数变化提供即时反馈；页面不显示内部像素计算说明。

| 场景 | 可观察结果 |
| --- | --- |
| 旧主题、构造、工厂与COMFORTABLE/REGULAR | 现有文字与几何默认值保持；旧显式声明继续精确可用 |
| 自然Button、图标、Field与局部SMALL/LARGE切密度 | 继承字号、自然高、图标预算、内距与圆角按同一角色更新，画面与命中一致 |
| 显式字号、padding0、gap、固定盒及滚动条dimensions | 首挂与往返主题更新均保持覆盖；后续局部Signal继续生效 |
| 项目表单保草稿与选区、Select打开时切密度 | Tabs、触发器、弹层及现代按钮同步更新，明确宽度、焦点与业务状态保留 |
| 表格预先访问不同区段，中段切密度再导航末尾返回 | 实际行高、内容范围及锚点使用新条件，固定列宽和备注保留，构造工作围绕有限内容 |
| 静止、纯颜色/动效与关闭 | 相同有效度量复用；不产生多余布局/屏外高度失效；所属观察沿原Owner释放 |

稳定逻辑沿正式ScenePageSession和既有真实页面验证。各仓继续按Core→Platform→Minecraft正式Maven依赖消费；设备和GPU画面在对应宿主实际运行入口验收。

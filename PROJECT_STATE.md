# 项目状态

## 2026-10-03: 帖子底部大广告、首页活动/播客横幅彻底屏蔽与全量广告SDK兜底防御 (v1.3)

- 宿主: 豆瓣 7.134.0, 模块 API 102.
- 核心修改与根因:
  1. 帖子底部大广告屏蔽:
     - 根因: 讨论帖与各类详情页（`RexxarAdActivity2` / `GroupTopicActivity2`）在正文 WebView 加载完成后, 动态构建并向 `RexxarStructHeader` 的 `llHeaderContainer` (`0x7f0a0c67`) 中添加 `FeedAdItemParent` (包含 `FeedAdItemView1` 及上下两条 38px 分隔线). 原先模块仅 Hook 了 `updateView`/`bind`/`populate`, 但 `FeedAdItemParent` 不走这些方法.
     - 修复方案:
       - 在 `DoubanAdPurifier` 中拦截 `RexxarAdActivity2.buildAdContainer` 与 `ViewGroup.addView`, 检测到广告 View 时直接丢弃且禁止挂载.
       - 为 `FeedAdItemParent` 以及所有 `FeedAdItemView1~7`、`FeedAdItemSdkView`、`FeedAdItemFakeView`、`RecentTopicAdView`、`FeedAdBannerView` 等广告类实现全生命周期拦截: `onMeasure` 强制 `(0, 0)`、`setVisibility` 强制 `GONE`、`onAttachedToWindow` 宽高置 0.
       - 在 `DoubanLayoutPurifier` 遍历时对 `llHeaderContainer` 的子 View 进行净化, 发现广告卡片时将其及相邻的上下空白分割线一同设为 `GONE` 且高度归零.
  2. 首页顶部活动/播客横幅屏蔽:
     - 根因: 首页精选顶部动态插入 `notification_container` (`0x7f0a0ead`) 及 `venue_...` 视图（如「播客马拉松10月收听开赛」横幅）, 原先未对该容器和对应 Binding 做拦截.
     - 修复方案:
       - Hook `ItemNotificationVenueViewBinding`、`ItemNotificationViewBinding` 等的数据绑定与充填方法, 根视图直接设为 `GONE` 且宽高置 0.
       - 拦截 `0x7f0a0ead` (`notification_container`)、`0x7f0a18ea` (`venue_bg`)、`0x7f0a08b0` (`header_container`)、`0x7f0a08bf` (`header_left_image`)、`0x7f0a08c6` (`header_right_banner`) 的可见性与尺寸.
       - 拦截 `HomeHeaderModel` 获取与刷新 `HomeHeaderAd` (`/api/v2/home_ads`).
  3. 全量广告 SDK 兜底与防崩溃加固:
     - 字节穿山甲 (CSJ): `TTAdNative` 全量广告加载方法（Feed/Splash/Draw/Banner/NativeExpress/Stream/Reward/FullScreen）全部拦截为空; 启动委托 Activity `TTDelegateActivity` 立即 finish; 允许 `TTAdSdk.init` 正常返回避免空指针, 拦截后续广告拉取.
     - 腾讯优量汇 (GDT): 拦截 `NativeUnifiedAD`、`NativeExpressAD`、`SplashAD`、`UnifiedInterstitialAD`、`RewardVideoAD` 全量广告请求.
     - 京东联盟 (JAD): 拦截 `JADBanner`、`JADFeeds`、`JADSplash`、`JADInterstitial` 的广告拉取.
     - 百度联盟 (MobAds): 拦截 `BaiduNativeManager` (Feed/Express/Native) 与 `SplashAd`.
     - 豆瓣原生广告分发: 拦截 `AbstractSdkFetcher.doFetch`、`AdIntersManager`、`PullAdContainer`、`SubjectAdHeader`.
  4. 版本升级:
     - `versionCode` 升至 4, `versionName` 升至 `1.3`, 同步更新 `module.prop` 与应用内设置弹窗版本标签.
- 验证结果:
  1. 本地单元测试 16 项全部通过 (`app:testDebugUnitTest`).
  2. 真机覆盖安装 (`DouBanPurify-v1.3.apk`) 并冷启动豆瓣实测:
     - 首页「精选」顶部原先的「播客马拉松10月收听开赛」红色横幅彻底消失, 首页直出内容流, 布局紧凑无多余空白.
     - 进入之前留存的帖子（`emoji乐子组` 帖子 `501254493`）, 滑动到正文最底部（「该小组已开启防搬运功能」下方）, 原先占满大半屏的「百度网盘」商业大广告及上下留白线完全消失, 正文底部与「回复/评论」区域无缝贴合.
     - 应用内侧边栏「DouBanPurify 净化设置」入口正常, 无崩溃报错.

## 2026-09-30: 影视详情与讨论页三个 Bug 修复完成并通过实机验证

- 宿主: 豆瓣 7.134.0, 模块 API 102.
- 根因分析:
  1. 影视详情标签缺失: `DoubanLayoutPurifier.java` 原先未限定 Activity 作用域, 凡是 2 个子项的 `PagerSlidingTabStrip` 均被当成首页顶部「动态/精选」, 导致电视剧详情页的第 0 个标签（如「剧评」）被误设为 GONE 且宽度置 0.
  2. 讨论帖滑动闪退/返回上一页: `DoubanAdPurifier.java` 对 `FeedAd.isValid()` 统一 Hook 返回了布尔值 `false`, 而豆瓣 7.134.0 中 `isValid()` 实际返回 `int`, 触发了 `ClassCastException: Boolean cannot be cast to Integer` 导致主线程崩溃重启; 另外原先 `HackViewPager` 的滑动拦截未限制首页, 误拦截了其他页面的手势.
  3. 更多讨论右下角发布按钮图标缺失: 首页悬浮笔隐藏逻辑通过 `0x7f0a02e1` (`btn_post`) 盲目隐藏, 未限定在首页 `SplashActivity`, 导致小组详情页 `GroupDetailActivity` 中发布按钮内部的图标 ImageView 也被隐藏, 仅剩外层绿圆背景 `btn_post_layout`.
- 修复方案:
  1. 将首页顶部标签、底栏导航、首页发布笔、首页 HackViewPager 滑动锁定的判断严格限制在 `SplashActivity` 且校验具体 View 结构与 ID.
  2. `DoubanAdPurifier` 增加方法签名返回类型校验, 仅当方法返回类型为 `boolean` / `Boolean` 时才拦截布尔结果, 避免类型强转异常.
  3. 讨论页发布按钮恢复内部图标并保持可交互.
- 验证结果:
  1. 本地单元与回归测试 14 项全部通过 (`app:testDebugUnitTest`).
  2. 真机覆盖安装 (`io.github.jessire.doubanpurify-v1.0`) 并重启豆瓣测试:
     - 搜索结果第一个电影（《绅士们 (2019)》）「综合/影评/讨论」3 个标签正常.
     - 搜索结果第二个电视剧（《绅士们 第二季 (2026)》）「剧评/小组讨论」2 个标签全部正常展示, 左右滑动及点击切换完全正常.
     - 进入小组讨论并点进任意帖子, 连续上下滑动不再出现崩溃或闪退返回上一页, 进程存活稳定.
     - 滑动到底部进入「更多讨论」, 右下角绿色发布圆钮内的发布/编辑图标已完整还原, 点击可正常调出发布交互.

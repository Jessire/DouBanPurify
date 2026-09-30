# 项目状态

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

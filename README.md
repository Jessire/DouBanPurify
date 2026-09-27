# DouBanPurify

[![LSPosed](https://img.shields.io/badge/LSPosed-API%20102-brightgreen.svg)](https://github.com/libxposed/api)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Target](https://img.shields.io/badge/Douban-7.134.0%2B-42BD56.svg)](https://www.douban.com)

DouBanPurify 是专为豆瓣（com.douban.frodo）Android 客户端打造的轻量级纯净与净化 Xposed / LSPosed 模块。
基于现代 LibXposed API 102 规范开发，不修改 APK 文件，纯运行时 Hook，实现开屏秒开、精简底栏与首页布局、净化侧边栏、屏蔽更新及评价弹窗，并提供原生内嵌的高质感设置界面。

---

## 核心特性

### 启动与广告拦截
- 开屏广告秒开：彻底去除冷启动与热启动开屏广告倒计时，启动直达首页。
- 信息流广告拦截：自动隐藏首页推荐信息流中的商业推广卡片及广告。
- 更新弹窗屏蔽：彻底拦截内测版、Beta 版本更新提示与静默安装弹窗。
- 好评弹窗屏蔽：阻断应用内弹出的评分求好评浮窗。
- 友盟统计切断：拦截 Umeng+ SDK 初始化、统计打点、崩溃采集及后台上传。

### 界面布局精简
- 底栏极简双 Tab：隐藏底栏「书影音」、「小组」、「市集」，仅保留「首页」和「我」，自动均分 50% 等宽布局并防止重绘死循环。
- 首页单 Tab 模式：移除顶部「动态」Tab，仅保留「精选/推荐」，禁止误触滑动至空白动态页。
- 顶栏杂项清理：移除首页右上角播客/耳机图标、搜索框下方小狗宠物挂件及推广横幅。
- 移除悬浮发布笔：隐藏首页右下角常驻的写日记/发布悬浮笔按钮。

### 搜索与侧边栏净化
- 搜索页精简：保留上方「实时热门书影音 Top20 / 实时热门讨论」轮播推荐卡片，彻底隐藏下方「热门话题」排行榜及广告流。
- 侧边栏抽屉净化：隐藏未成年人模式、草稿箱、我的背包、签到足迹、帮助与反馈、社区管理中心以及底部服务订单网格。
- 原生内嵌设置：在侧边栏「设置」正下方无缝植入 DouBanPurify 净化设置入口（使用豆瓣官方图标），点击弹出高质感分类设置弹窗，所有功能开关独立可控，即时生效无需重启。

---

## 构建与编译

### 环境要求
- JDK 17
- Android SDK 34+
- Gradle 8.x
- LSPosed 框架（支持现代 API 102 规范）

### 编译命令
`ash
./gradlew assembleDebug
`
产物位于 app/build/outputs/apk/debug/app-debug.apk。

### 安装与生效
`ash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
`
1. 在 LSPosed 管理器中启用 DouBanPurify，勾选作用域 豆瓣（com.douban.frodo）。
2. 强行停止豆瓣并重新打开即可体验。

---

## 作用域配置
- 包名：com.douban.frodo
- 入口：com.douban.pure.DoubanPureHook
- 框架 API：io.github.libxposed:api:102.0.0
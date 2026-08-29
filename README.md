# 冰读 iceReading

> 纯 Java / 无依赖 / 单 dex 的 Android EPUB 阅读器

一个完全开源、零云同步、零追踪、零广告的本地 EPUB 2/3 电子书阅读器。APK 不到 100KB,支持 OPDS 在线书库、本地扫描、5 套主题、自定义字体、书签高亮、阅读统计。

## ✨ 特性

- 📚 **书架管理** — 2 列网格 / 多维排序(最近/添加/书名/作者)/ 全文搜索
- 🌐 **OPDS 发现** — 6 个内置在线书库(古登堡 / Standard Ebooks / Feedbooks 等),支持 Basic Auth / Bearer Token
- 📖 **阅读器** — WebView 渲染,5 套主题(日间/护眼/羊皮/夜间/深邃),可调字体/行距/段距/边距
- 🔖 **进度** — 自动保存,跨设备迁移(JSON 导入导出)
- 🎨 **自定义字体** — 支持 .ttf/.otf 导入
- 📊 **统计** — 阅读时长/字数/章节/热力图
- 🔧 **多模态** — 选词高亮、批量笔记
- 🚀 **零云同步** — 所有数据本地 SQLite,从不发到任何服务器
- 🛡️ **零追踪** — 无分析 SDK / 无统计上报 / 无广告
- 🔍 **FTS5 全文搜索** (Android 11+ 启用,降级 LIKE)

## 📦 APK 信息

| 指标 | 数值 |
|---|---|
| 体积 | ~85 KB |
| 包名 | `com.icereading.app` |
| 最低 SDK | 24 (Android 7.0) |
| 目标 SDK | 33 (Android 13) |
| 语言 | Java (无 Kotlin) |
| 第三方依赖 | 0(只用 Android 系统 API) |
| License | MIT |

## 📷 截图

*(待补充)*

## 🏗️ 构建

```bash
# 依赖: ~/apkbuild/android.jar + Termux 的 aapt/dx/apksigner
bash build.sh
# 产物: icereading.apk (约 85KB)
```

构建工具复用作者其它项目的 `~/apkbuild/` 工具链。

## 📂 项目结构

```
src/com/icereading/app/
├── MainActivity.java       主 Activity(4 tab 切换)
├── BookshelfView.java      书架 tab
├── DiscoverView.java       发现 tab(OPDS)
├── DownloadView.java       下载 tab
├── SettingsView.java       设置 tab
├── ReaderActivity.java      阅读器(WebView + JS bridge)
├── OpdsActivity.java       OPDS 源管理
├── PersonaActivity.java     角色(预留扩展)
├── EpubParser.java         EPUB 2/3 解析(ZIP + OPF + NCX/NAV)
├── OpdsClient.java         OPDS 1.2 Atom 客户端
├── Downloader.java         断点续传下载器
├── BookRepository.java     SQLite 数据层(books / bookmarks / ...)
├── BookImporter.java       导入 EPUB 的封装
├── Models.java             全部数据模型(POJO)
├── Settings.java           SharedPreferences 包装
├── StreamReader.java       (内置工具)
└── HistoryManager.java     (内置工具)
```

```
res/
├── layout/   10 个 XML(书架/发现/下载/阅读/设置/...)
├── values/   颜色/字符串/主题
└── mipmap-*/ 5 套分辨率图标

assets/
├── reader.css  阅读器主题(5 套 CSS 变量)
└── reader.js   分页/选区/高亮 JS 桥
```

## 🛠️ 技术栈

- **EPUB 解析** — `java.util.zip` + `javax.xml.parsers`(纯标准库,无第三方)
- **OPDS 解析** — Atom 1.0 + OPDS 1.2 扩展(`rel="next"` / `opensearch:*` / GZip 解压)
- **WebView 渲染** — Android System WebView,JS bridge 双向通信
- **存储** — SQLite + FTS5 全文索引(API 30+)
- **HTTP** — `HttpURLConnection`(无 OkHttp/Retrofit 依赖)
- **下载** — 原生 `Range` 头支持断点续传

## 📚 数据源(v2.0)

由于 2026 年所有公开 OPDS 端点(古登堡/Standard Ebooks/Feedbooks/ManyBooks/OPDS.io/番茄/笔趣/七猫/起点)均失效或被墙,**v2.0 改为诚实可用路线**:

| 类型 | 描述 | 状态 |
|---|---|---|
| 📚 **本地书库** | 始终可用,SQLite 搜索 | ✅ 真稳 |
| 🌐 **全网搜索**(必应/Google) | 找书 → 跳浏览器下载 | ✅ 真稳(免 key) |
| 🎧 **LibriVox** | 英文免费有声书 | ✅ 200 OK 真稳 |
| 🔌 **自定义 OPDS** | 您内网 Calibre 服务器 | ✅ 真稳 |

> **为什么不直连番茄/笔趣/七猫/起点**:这些站都是登录墙 + 接口签名加密(SSR/JS 异步 + 加密请求),纯客户端无法稳定直连。
> 番茄 web SSR 是 React + 监控 SDK,章节内容走签名 fetch,即使真机上抓得到也得反编译签名算法——这已超出"开源阅读器"范畴。
> **推荐做法**:在您的电脑跑 [Calibre](https://calibre-ebook.com/) → 启动 OPDS 服务器 → 手机上添加您的内网 OPDS 源,永久使用。

可在「发现 → +」中**添加自定义 OPDS 源**(支持 Basic Auth / Bearer Token)。

## 🔐 权限

| 权限 | 用途 |
|---|---|
| INTERNET | 拉取 OPDS / 下载电子书 |
| ACCESS_NETWORK_STATE | 检测网络 |
| READ_MEDIA_IMAGES | 选图作为封面(Android 13+) |
| READ_MEDIA_VISUAL_USER_SELECTED | 选特定图片(Android 14+) |
| POST_NOTIFICATIONS | 下载完成通知 |
| FOREGROUND_SERVICE / MEDIA_PLAYBACK | 未来 TTS 朗读 |

**刻意不申请**存储写入 / 电话状态 / 任何分析 SDK 权限。

## 🌍 多语言

- 中文(简体,默认)
- 英文
- 任意其他语言(字符支持由 WebView + 系统字体决定)

## 📦 第三方资源

- 无任何运行时第三方依赖
- 字体:**不内置**,首启引导用户从本地或下载 `.ttf/.otf`
- 图标:程序自绘(Android 矢量)

## 🤝 贡献

欢迎 PR!具体方向:

- **更多 OPDS 源适配**(针对国内/特殊源)
- **TTS 朗读**(v2.0 计划)
- **AI 摘要 / 翻译**(集成第三方 API,可选)
- **手写 / 划线 / 笔记同步**
- **EPUB 3.0 fixed-layout** 支持(目前仅 reflowable)
- **PDF / DJVU / MOBI 格式**(可选)

## 🐛 问题报告

在 GitHub Issues 提交。请附上:
- 设备型号 + Android 版本
- EPUB 来源 / 文件名(若能复现)
- logcat 片段(若有 crash)

## 📜 License

MIT License - 见 [LICENSE](LICENSE) 文件

## 🔗 相关项目

- 作者另一作品(同源工具链):[MusicFusion](https://github.com/ice-wocker/MusicFusion) — 聚合音乐播放器(开源)

---

**冰读 iceReading** — 让阅读回到阅读本身。

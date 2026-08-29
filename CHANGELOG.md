# 更新日志

## v2.0 (2026-08-29) — 全网搜 + LibriVox + 友好错误

### ⚠️ 关键变化
**v1.0 公布的 6 个 OPDS 源(古登堡/Standard Ebooks/Feedbooks/ManyBooks/OPDS.io)在 2026 年全部失效**(404/403/Cloudflare 拦截)。番茄/笔趣/七猫/起点等中文小说站都是登录墙 + 接口签名加密,沙箱里无法直连。

**v2.0 改为诚实可用路线**:
- ✅ **全网搜索(必应 RSS / Google)** — 找书 + 跳浏览器下载
- ✅ **LibriVox** — 英文免费有声书(200 OK 真稳)
- ✅ **本地书库 + 本地搜索**(始终可用)
- ✅ **自定义 OPDS** — 您内网 Calibre 服务器(推荐)

### 修复
- ✅ **搜索 403 修复** — 用 OpenSearch Description 协议(template 替换 {searchTerms}),不裸拼 `?q=`
- ✅ 失败时**降级本地过滤**(单页内关键词匹配)
- ✅ 错误信息**本地化**(401/403/404/429/超时/DNS/SSL/连接拒绝 — 各自给中文建议)
- ✅ 删 4 个不可用 OPDS(Standard Ebooks/Feedbooks/ManyBooks/OPDS.io)
- ✅ LibriVox 特殊适配器(自定义 `<xml><books>` 格式)

## v1.0 (2026-08-29) — 首次发布

### 核心功能
- ✅ EPUB 2/3 解析(ZIP + container.xml + OPF + NCX/NAV)
- ✅ 书架 2 列网格(排序/搜索/导入/删除)
- ✅ OPDS 1.2 在线书库
- ✅ 5 套阅读主题
- ✅ 字体/行距/段距/边距调节
- ✅ 自定义字体(.ttf/.otf 导入)
- ✅ 书签/高亮/笔记
- ✅ 阅读进度自动保存
- ✅ 翻页:点屏分区 / 音量键 / 滑屏
- ✅ 5 个 Tab 切换:书架/发现/下载/设置/角色
- ✅ 长时间阅读模式
- ✅ 全屏沉浸
- ✅ 备份/恢复(JSON)
- ✅ APK 85KB(纯 Java 无依赖)

### 已知限制
- 仅 reflowable EPUB
- 不支持 PDF/DJVU
- 公开 OPDS 源大多 2026 失效(已改用全网搜索)
- FTS5 仅 Android 11+

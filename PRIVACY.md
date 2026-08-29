# 隐私政策

**冰读 iceReading** 尊重您的隐私。本应用遵守以下原则:

## 1. 数据存储

- **所有数据本地存储** — 书架、书签、笔记、阅读进度、设置均存储在应用私有 SQLite + SharedPreferences
- **不上传任何服务器** — 应用本身不与任何作者控制的服务器通信
- **可完全清除** — 设置中提供"清空书架"功能

## 2. 网络访问

应用联网**仅**用于以下场景(均由用户主动触发):

- 用户在「发现」tab 添加 OPDS 源并浏览
- 用户点击某本书后下载(显式下载操作)
- 加载 OPDS 源提供的封面图片(显示在列表中)

应用**不会**:
- 自动发送任何遥测/统计
- 与作者控制的服务器通信(本项目无服务端)
- 收集使用数据/崩溃数据

## 3. 第三方服务(OPDS 源)

- 用户添加的 OPDS 源由第三方运营
- 用户名/密码/Bearer Token 仅存储在本地 SharedPreferences
- 应用不会将凭据分享给其他源
- 各 OPDS 源的隐私政策由其各自规定

## 4. 权限使用

| 权限 | 使用场景 |
|---|---|
| INTERNET | OPDS / 下载 |
| ACCESS_NETWORK_STATE | 网络检测 |
| READ_MEDIA_IMAGES / VISUAL_USER_SELECTED | 选封面图 |
| POST_NOTIFICATIONS | 下载完成通知 |
| FOREGROUND_SERVICE | 未来 TTS 朗读 |

不申请存储全盘写入 / 通讯录 / 电话等无关权限。

## 5. 儿童隐私

应用不针对 13 岁以下儿童设计,也不主动收集任何儿童信息。

## 6. 政策变更

如有重大变更,会在 GitHub Releases 注明。

## 7. 联系

- GitHub Issues
- 提交时标记 `privacy` 标签

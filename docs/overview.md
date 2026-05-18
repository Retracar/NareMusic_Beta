NareMusic_Beta — 播放器模块概览

项目定位
- Kotlin + Media3 + 协程(StateFlow) 的 Android 音乐播放器，适配 Android 12-15。

分工边界（强制）
- UI、布局、Compose、XML 由前端同学负责，后端只提供逻辑 API 与服务接口。

代码结构（仓库内位置）
- `app/src/main/java/com/example/naremusic_beta/playercore/model` — 数据模型（Track、Lyrics、LyricLine、PlaybackState 等）
- `app/src/main/java/com/example/naremusic_beta/playercore` — 管理类（`PlayerManager`）
- `app/src/main/java/com/example/naremusic_beta/playercore/service` — 后台服务 `MusicService`
- `app/src/main/java/com/example/naremusic_beta/playercore/lyrics` — 歌词解析器
- `app/src/main/java/com/example/naremusic_beta/playercore/util` — 音频能力检测工具

重要设计原则
- 纯 Kotlin 实现；对外暴露极简 API；使用 `StateFlow` 发布状态；避免内存泄漏并显式释放播放器资源。

下一步建议
- 前端同学使用 `PlayerManager` 的 `StateFlow` 订阅播放状态并驱动 UI。
- 将 Gradle 依赖合并到 `app` 模块，执行一次 `./gradlew :app:assembleDebug` 以让 IDE/LS 更新索引。

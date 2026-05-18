项目：NareMusic_Beta — 播放器核心模块文档

目录
- 说明
- 依赖（Gradle）
- 导出 API 概览
- 持久化文件位置

说明
- 本模块仅包含播放器底层逻辑、Media3 封装、后台服务、歌词解析与音频能力探测。
- 严格不包含任何 UI/Compose/XML 布局代码。

Gradle 依赖（请在 `app/build.gradle.kts` 的 dependencies 区块中添加）

```kotlin
val media3Version = "1.1.0"
dependencies {
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version") // optional for UI owners

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

    // accompanist-lyrics-core: used only for parsing if available. 如果不可用，系统会回退到内置 LRC 解析器
    implementation("com.google.accompanist:accompanist-lyrics-core:0.30.0")
}
```

导出 API 概览（极简）
- `PlayerManager.init(context)` — 初始化播放器，必须调用一次。
- `PlayerManager.setQueue(tracks, startIndex)` — 设置播放队列。
- `PlayerManager.play()/pause()/stop()/seekTo(ms)` — 控制播放。
- `PlayerManager.queue` — `StateFlow<List<Track>>`（只读）
- `PlayerManager.playbackState` — `StateFlow<PlaybackState>`（只读）
- `LyricsParser.parse(rawLyricText)` — 返回 `Lyrics` 数据模型。

持久化位置
- 本文件：`docs/player-core.md`

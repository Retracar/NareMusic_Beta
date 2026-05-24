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

    // 歌词解析默认使用内置 LRC 解析器。
    // 不要在此处添加 accompanist-lyrics-core：当前构建中该依赖解析会失败。
}
```

导出 API 概览（极简）

初始化与释放
- `PlayerManager.init(context)` — 初始化播放器，幂等（只初始化一次）。
- `PlayerManager.release()` — 释放播放器资源，通常在 App 退出或 Activity 销毁时调用。

播放控制
- `PlayerManager.play()` — 开始/恢复播放。
- `PlayerManager.pause()` — 暂停播放。
- `PlayerManager.stop()` — 停止播放（保留队列）。

队列与切歌
- `PlayerManager.setQueue(tracks: List<Track>, startIndex: Int = 0)` — 设置/替换播放队列，定位到指定索引（不自动播放）。
- `PlayerManager.playUrl(uri: String)` — 便捷播放单条 URL 或本地文件（会替换队列）。
- `PlayerManager.playTrackAt(index: Int)` — 播放队列中指定索引的曲目。
- `PlayerManager.next()` / `previous()` — 下一首 / 上一首。
- `PlayerManager.addToQueue(track: Track)` — 追加曲目到队列；若队列为空则自动准备。
- `PlayerManager.removeFromQueue(trackId: String)` — 按 id 移除队列项。

进度控制
- `PlayerManager.seekTo(ms: Long)` — 跳转到指定毫秒位置（常用于进度条拖拽）。

其他
- `PlayerManager.clearLastError()` — 清除错误状态（UI 展示后应调用以避免重复）。
- `PlayerManager.getPlayer()` — 返回内部 ExoPlayer 实例（高级接口，不建议 UI 直接操作）。

状态订阅
- `PlayerManager.playbackState: StateFlow<PlaybackState>` — 播放状态流（包含进度、当前曲目、错误等）。
- `PlayerManager.queue: StateFlow<List<Track>>` — 播放队列流。

工具类
- `LyricsParser.parse(rawLyricText: String)` — 解析歌词文本，返回 `Lyrics` 数据模型。

持久化位置
- 本文件：`docs/player-core.md`

后台服务与 MediaSession
- `MusicService` 在 `app/src/main/java/com/example/naremusic_beta/playercore/service/` 目录下。
- 该服务暴露 Media3 MediaSession，供系统控制中心与车机等接入。
- **重要**：当需要启用该服务时，必须在 `app/src/main/AndroidManifest.xml` 中注册：
  ```xml
  <service
      android:name="com.example.naremusic_beta.playercore.service.MusicService"
      android:foregroundServiceType="mediaPlayback"
      android:exported="true">
      <intent-filter>
          <action android:name="androidx.media3.session.MediaSessionService" />
      </intent-filter>
  </service>
  ```
- UI 不需要直接启动该服务；播放器会在需要时自动通过 PlayerManager 的初始化流程关联。

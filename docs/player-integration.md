# PlayerManager — UI 对接规范

本文档定义 `PlayerManager`（播放器底层）与 UI 层的交互规范。仅包含纯逻辑接口、数据结构与调用规则，绝不包含任何 UI/Compose/XML 代码。

目标：UI 能通过极简的调用与订阅完成播放控制、进度交互、队列管理与错误展示，屏蔽 Media3 复杂性。

目录
- 一、公开方法（UI 可调用）
- 二、必须订阅的 StateFlow 状态
- 三、数据模型（摘要）
- 四、调用与订阅约定（要点）
- 五、对接检查清单
- 六、扩展字段与兼容性说明

---

## 一、公开方法（UI 可调用）

说明：UI 统一在主线程调用下列方法；所有方法为非挂起函数，内部异步处理播放器工作。

- `init(context: Context)`
  - 作用：初始化播放器与内部状态；幂等。建议在 `Application.onCreate()` 或主 Activity 启动时调用一次。

- `release()`
  - 作用：释放播放器与资源；在 App 退出或不再需要播放时调用。

- `setQueue(tracks: List<Track>, startIndex: Int = 0)`
  - 作用：设置/替换播放队列，定位到 `startIndex`（不隐式播放）。

- `play()` / `pause()` / `stop()`
  - 作用：播放、暂停、停止（停止保留队列）。

- `playUrl(uri: String)`
  - 作用：便捷播放单条 URL 或本地文件（会把队列替换为该条并开始播放）。

- `playTrackAt(index: Int)`
  - 作用：播放队列中指定索引的曲目。

- `next()` / `previous()`
  - 作用：切到下一首 / 上一首，内部处理边界；遵循当前播放模式（若实现）。

- `seekTo(ms: Long)`
  - 作用：跳转到指定毫秒位置。用于拖拽释放后调用。

- `addToQueue(track: Track, playNow: Boolean = false)`
  - 作用：追加到队列；若 `playNow=true` 则立即播放。

- `removeFromQueue(trackId: String): Boolean`
  - 作用：按 id 移除队列项，返回是否成功。

- `setPlayMode(mode: PlayMode)`
  - 作用：设置播放模式（预留：顺序/单曲循环/全部循环/随机）。

- `clearLastError()`
  - 作用：清除 `lastError` 字段，UI 在展示完错误后应调用以避免重复弹窗。

备注：存在 `getPlayer()` 的高级方法供扩展，但不建议 UI 层直接操作底层 Player。

---

## 二、必须订阅的 StateFlow 状态

说明：UI 通过订阅下面的 StateFlow 取得播放器实时状态，所有字段只读。

- `playbackState: StateFlow<PlaybackState>`（核心）
  - `isPlaying: Boolean` — 当前是否播放中（用于切换 Play/Pause 图标）。
  - `currentTrack: Track?` — 当前曲目信息（id/title/artist/uri/durationMs）。
  - `positionMs: Long` — 当前播放进度（PlayerManager 每 500ms 刷新）。
  - `durationMs: Long` — 当前曲目总时长（若未知则为 0 或 -1）。
  - `playbackState: Int` — Media3 内部状态（`Player.STATE_*`），仅作诊断。UI 可忽略。
  - `lastError: String?` — 若非空，表示发生可展示的错误；展示后调用 `clearLastError()`。
  - `isEnded: Boolean` — 表示播放已结束（用于播放结束触发下一曲或 UI 动作）。
  - `playMode: PlayMode` — 当前播放模式（预留）。

- `queue: StateFlow<List<Track>>`
  - 当前播放队列（UI 用于展示列表并高亮 `currentTrack`）。

- `lyrics: StateFlow<Lyrics?>`（预留）
  - 如果歌词可用，返回 `Lyrics` 数据模型（仅数据，UI 负责渲染）。

备注：若需要，`playMode` 也可以单独通过 `playModeFlow: StateFlow<PlayMode>` 暴露。

---

## 三、数据模型（摘要）

- `Track`:
  - `id: String` — 唯一标识（可使用 URI 或数据库 id）。
  - `title: String`
  - `artist: String?`
  - `album: String?`
  - `uri: String` — 支持 http/https/file:// 等。
  - `durationMs: Long`

- `PlaybackState`:
  - 包含上文列出的字段（`isPlaying`, `currentTrack`, `positionMs`, `durationMs`, `playbackState`, `lastError`, `isEnded`, `playMode`）。

- `PlayMode` (enum): `SEQUENTIAL`, `REPEAT_ONE`, `REPEAT_ALL`, `SHUFFLE`（预留）。

- `Lyrics` / `LyricLine`：由 PlayerManager 提供解析后的数据结构，仅供 UI 渲染。

---

## 四、调用与订阅约定（要点）

1. 线程约定：UI 在主线程调用 `PlayerManager` 的方法；订阅 StateFlow 建议在 `lifecycleScope`/`viewModelScope` 中收集。

2. 进度条交互（拖拽）最佳实践：
   - 开始拖拽：UI 在本地显示拖拽位置，不调用 `seekTo`，暂停读取自动刷新的 `positionMs`（仅 UI 层逻辑）。
   - 结束拖拽：调用 `PlayerManager.seekTo(ms)`，随后让 StateFlow 的 `positionMs` 与播放器回落同步。

3. 播放/暂停按钮：直接调用 `play()`/`pause()`，不要在 UI 中操作播放器底层实例。

4. 错误处理：当 `lastError` 非空时，UI 显示用户友好提示；提示后调用 `clearLastError()`。

5. 队列操作：UI 可自由调用 `setQueue`、`addToQueue`、`removeFromQueue`；变更会通过 `queue: StateFlow` 广播。

6. 播放结束处理：当 `isEnded == true`，UI 可选择自动触发 `next()` 或显示播放完成状态。

7. 幂等与防抖：UI 不需重复保护同一调用，但可在短时间内对高频调用（如连续点击 Next）做防抖以提升 UX。

---

## 五、对接检查清单（发给 UI 同事）

启动前检查：
- 已在 `Application` 或主 Activity 调用 `PlayerManager.init(context)`。  

控件绑定：
- 播放按钮 -> `PlayerManager.play()`  
- 暂停按钮 -> `PlayerManager.pause()`  
- 下一首 -> `PlayerManager.next()`  
- 列表点击 -> `PlayerManager.playTrackAt(index)`  
- 拖拽结束 -> `PlayerManager.seekTo(ms)`  

状态订阅：
- 必须订阅 `PlayerManager.playbackState` 更新播放图标/进度/错误/结束  
- 必须订阅 `PlayerManager.queue` 更新播放列表视图  

错误处理：
- 当 `playbackState.lastError` 非空，展示后调用 `PlayerManager.clearLastError()`。  

本地测试：
- 用一个 http 音频 URL 调用 `playUrl()`，确认能出声、能拖进度、能切歌。

---

## 六、扩展字段与兼容性说明

- 所有对外接口尽量保持稳定，新增功能应通过新增 `PlaybackState` 字段或额外 `StateFlow` 暴露，避免更改已有方法签名。  
- 预留支持：播放模式 (`playMode`)、歌词 (`lyrics`)、队列元信息（来源、专辑封面 URL 等）。

---

如果你同意这份规范，我会将其保存为 `docs/player-integration.md`（已写入）并本地提交。UI 同事可以直接按此文档对接。若需，我可把示例的 StateFlow 收集代码片段添加为附录（非 UI 代码，只示意如何订阅）。

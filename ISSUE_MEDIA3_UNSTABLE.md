<!-- ISSUE DRAFT: Media3 Unstable API encapsulation and follow-up testing -->
# Title
封装 Media3 `UnstableApi`（通知样式）并补充测试

# Background
我们在 `feat/lycore` 分支中发现代码库中存在对 Media3 不稳定 API（`MediaStyleNotificationHelper.MediaStyle`）的直接调用。为了降低升级与审计风险，已将该调用封装到 `playercore/service/Media3NotificationHelper.kt`，并添加了单元测试与 Robolectric+Mockito 测试覆盖回退与非空分支。

# Changes (local)
- `playercore/service/Media3NotificationHelper.kt` — 新增 wrapper 并在内部使用 `@OptIn(UnstableApi::class)`。
- `playercore/service/MusicService.kt` — 替换直接调用为 wrapper，增加日志和更稳健的 notification 构建。
- `playercore/audio/AudioFocusManager.kt`, `playercore/PlayerManager.kt` — 修复焦点及 listener/release 问题（请参考变更细节）。
- Tests: 两个 JVM 测试文件新增，覆盖 null 回退和 mocked `MediaSession` 的非空场景。

# Why this matters
- 直接散布使用 `UnstableApi` 会导致升级成本高并增加运行时兼容风险。集中封装可以在未来 Media3 API 发生变化时更容易替换实现。

# Acceptance criteria
1. Wrapper 文件存在并包含 `@OptIn(UnstableApi::class)` 的局部使用。
2. 单元测试（包括 Robolectric 模拟）通过：`./gradlew test`。
3. PR 中包含变更说明（Changelog）和风险/迁移说明。

# How to reproduce locally
1. Checkout branch `feat/lycore`。
2. Run tests: `./gradlew test`。

# Suggested reviewers
- `@team-media` (播放器/前台服务 owner)
- `@team-android` (架构/兼容性)

# Notes
- 在 CI 中需要确保 Robolectric 能运行（JVM），并考虑是否把某些更深层通知行为验证放到 instrumentation 测试。

# Remaining work
- `Gradle` 侧保持原样，不新增全局 `opt-in` 配置；如果后续确实需要，只在局部文件或特定作用域处理。
- 继续在 QA 设备上回归验证：来电、蓝牙切换、通知控制、服务被系统回收后的恢复路径。
- 如后续升级 Media3，先检查 wrapper 处是否仍然需要兼容处理，再决定是否替换实现。

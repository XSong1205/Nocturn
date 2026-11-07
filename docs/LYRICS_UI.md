# 歌词界面与性能验证 · 2026-11-02

歌词入口放在底部 Dock，选中状态表示歌词模式；译文与音译采用进度条上方右侧的“译 / 音”按钮，只在当前歌词提供相应数据时出现。顶部音质标隐藏，歌名与歌手按中轴对齐。

Accompanist Lyrics UI 2.0.0-rc.3 使用已有的文字准备与栅格缓存。Nocturn 保持单个 Compose 时钟状态接收 500 ms 播放采样，逐帧只推进高亮时钟；歌曲内容转换、字体样式、跟随参数与时间回调缓存。稳定内容键避免每次采样哈希整首歌词；无障碍正文通过 derivedStateOf 仅在当前行变化时更新。屏幕仍使用采样位置，不接收逐帧进度重组。

时间轴修正：Media3 采样附带 `elapsedRealtimeNanos` 取样时刻、播放速度和位置跳变序号，完整传递到歌词时钟。绘制帧也读取同一单调时钟，补偿采样到 UI 的延迟；普通采样取已显示位置与修正位置的较大值，滞后的校正最多让高亮短暂停留，不会回退到上一个词。明确的拖动预览和 Media3 seek / repeat / item transition 才允许重置位置；不通过大幅倒退阈值猜测真实跳转。播放器无时间戳的离线预览使用接收时刻作为备用锚点。

换行使用 540 ms 缓动配合阻尼弹簧链：stiffness 180、damping 26、coupling 0.25；保留 Accompanist 的当前行缩放和渐变。Android 12+ 对非当前行启用按距离递增的缓存图层模糊，blurDelta 2；手动浏览时恢复清晰，视口外保活距离为 24 dp。Android 10 / 11 不启用 RenderEffect 模糊。

## 验证

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Compose 回归包含 Dock 位置与选中态、译文按钮位于进度条上方右侧、字幕开关在封面 / 歌词切换后保留、顶部无音质标、双语深浅色截图、跨播放采样保持时钟身份、暂停与向后跳转即时校正、换行在过程中逐步移动并最终对齐阅读锚点。原有 YRC / LRC 映射、点击跳转、手动浏览和恢复跟随仍在回归范围内。

布局与模糊调优阶段三个 Gradle 任务全部通过：170 项测试、0 失败；Lint 0 错误、18 项仓库现有警告。该阶段 Debug APK `0.1.8`（versionCode 9）已在连接手机上安装并启动。后续针对逐字闪回的时间轴修正按用户要求不再运行测试、Lint 或设备验证；上述通过记录不涵盖该修正。

截图为 Robolectric 宿主渲染，输出到 `app/build/screenshots/player-lyrics-bilingual-dark.png`、`player-lyrics-bilingual-light.png`。宿主截图用于布局与交互验证，不用于 Android GPU 性能结论。

## 可复现的设备采样入口

Debug APK 提供 `LyricsPerformanceActivity`，独立显示固定双语 YRC 场景，以 500 ms 样本循环播放 100–144 秒区间，不请求网络、不访问账号、不启动音频。该 Activity 仅声明在 `src/debug/AndroidManifest.xml`，不进入 Release APK。

```powershell
adb shell am start -n dev.nocturn.app/.LyricsPerformanceActivity
# 静态截图：添加 --ez paused true；浅色截图：添加 --ez light true。
adb shell dumpsys gfxinfo dev.nocturn.app reset
# 在歌词场景保持前台，采集同一段 20 秒播放。
adb shell dumpsys gfxinfo dev.nocturn.app framestats
```

本次连接的 Redmi / API 37 设备可安装并启动 Debug 场景，但采样期间前台多次切换到首页或通知栏；这些采样已排除，不据此报告性能提升百分比或帧率。原始尝试保存在忽略的 `build/reference/lyrics-tuning-2026-11-02/`。后续对比应独占设备、固定刷新率与温度，并分别确认采样开始 / 结束的前台 Activity；发布性能结论需要重新采集稳定前台的基线与最终版本。

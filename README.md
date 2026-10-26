# 倒数日 · Daymark

Kotlin + Jetpack Compose + Material 3 的日期记录应用。参考相邻 ShizukuFTP 的大圆角、柔和色调与壁纸配色方向。

- 新建、编辑、删除事项，选择起始日期与卡片色调。
- App 和桌面卡片居中显示「距 xxx 已经开始 / XXX 天」。未来事项显示「距 xxx 还有」。
- 2 × 1、2 × 2 与 3 × 2（宽 × 高）桌面组件，每个组件可独立选择事项。
- Android 12 起使用 Material You 壁纸动态色，支持系统深浅主题。
- 卡片可选预设色，也可通过色相环与加白滑杆自定义颜色，App 和组件保持一致并自动选择清晰的文字颜色。
- 首页从[今日诗词 API](https://www.jinrishici.com/doc/)获取诗句，显示作者与出处；支持手动刷新，离线时保留最近一次成功获取的内容。
- 记日事项保存在本机，不会发送给诗词服务；联网仅用于获取诗词。API 客户端 Token 保存在本机，不参与设备备份和迁移。

## 天数规则

开始当天为 0 天，次日为 1 天。使用本地日历日期计算，跨月、闰年和夏令时不会影响计数。App 在前台跨日时刷新；组件通过每日非精确闹钟、系统周期更新及日期/时区/开机广播刷新。系统休眠或厂商后台限制可能延后组件刷新，点开 App 会立即同步。

## 桌面组件

在 App 中点击桌面组件入口，选择尺寸后按桌面提示添加，再点按组件选择事项。也可长按桌面，从「小组件 → 记日」中添加并选择事项。修改事项会同步组件；删除事项后，相关组件显示重新选择提示。桌面网格由启动器决定，实际尺寸可随启动器调整。

~~gemini办事纯属在敷衍我....~~

## 构建

使用 JDK 17 或更新版本、Android SDK 36。通过 Android Studio 打开工程并同步，或执行：

```text
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows 使用 `gradlew.bat`。Gradle Wrapper 已包含，`local.properties` 由 Android Studio 生成。

最低支持 Android 8.0（API 26），目标 Android 16（API 36）。新代码全部使用 Kotlin。

# ColorOS 桌面组件入口

已在 PLK110 的 Oplus Launcher 16.3.16 / Android 16 上验证。

入口只在默认桌面为 `com.android.launcher/com.android.launcher.Launcher` 且声明
`com.android.launcher.support.card_widget_entry_integration` 能力、且桌面版本号在已验证集合中时提供。
目前验证版本为 16.3.16（versionCode 160030016）。其他版本自动使用 Android 标准申请。

App 向导出的 Launcher Activity 发送显式 `VIEW` Intent，携带
`state=ToggleBar_widget`。系统桌面的 `ToggleBarUtils.resolveStateByNewIntent`
会将其导航至原生 `WidgetsFullSheet`。该路径展示普通 AppWidget，不调用速览卡片服务。

不能使用 `MAIN` / `HOME` 动作：桌面在处理 state 后，还会在父类 Home-key
处理逻辑中关闭浮层。仅加 `is_add_item_activity` 标记也不能阻止关闭浮层。

`ColorOsWidgetEntryTest` 从 App 的普通 UID 发起跳转，不获取 root 权限：
先确认 App 窗口在前台，再确认系统「插件」面板出现并保持显示，以避免旧桌面窗口
或一闪而过的面板导致误判。

专用入口无法启动或被系统拒绝时，自动切换为标准组件申请流程；也可手动选择
「系统标准添加」。系统没有向普通 App 提供可靠的外部面板显示确认回调，所以不会
通过定时猜测成功，也不会要求无障碍或 root 来读取其他应用的界面。

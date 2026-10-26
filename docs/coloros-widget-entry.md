# ColorOS 桌面组件入口

已在 PLK110 的 Oplus Launcher 16.3.16 / Android 16 上验证。

入口只在默认桌面为 `com.android.launcher/com.android.launcher.Launcher` 且声明
`com.android.launcher.support.card_widget_entry_integration` 能力时提供。

App 向导出的 Launcher Activity 发送显式 `VIEW` Intent，携带
`state=ToggleBar_widget`。系统桌面的 `ToggleBarUtils.resolveStateByNewIntent`
会将其导航至原生 `WidgetsFullSheet`。该路径展示普通 AppWidget，不调用速览卡片服务。

不能使用 `MAIN` / `HOME` 动作：桌面在处理 state 后，还会在父类 Home-key
处理逻辑中关闭浮层。仅加 `is_add_item_activity` 标记也不能阻止关闭浮层。

`ColorOsWidgetEntryTest` 从 App 的普通 UID 发起跳转，不获取 root 权限：
先确认 App 窗口在前台，再确认系统「插件」面板出现并保持显示，以避免旧桌面窗口
或一闪而过的面板导致误判。

此入口依据实际桌面版本确认。桌面更换为其他实现时，App 继续使用标准组件申请流程。

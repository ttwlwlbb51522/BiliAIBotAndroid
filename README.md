# BiliAIBot Android 控制台

当前版本：**1.0.0**

这是电脑端机器人的手机控制端，不是独立运行的机器人。

## 功能

- 查看机器人状态、版本、运行时长、dry-run 模式
- 查看今日统计
- 查看待审草稿
- 批准并发送草稿
- 丢弃草稿
- 查看最近运行日志
- 手动触发刷视频（可填写关键词）
- 按分类列出配置项，显示键名、中文含义和当前值，点击单项修改

## 前置条件

电脑端机器人需要开启管理接口：

```properties
admin.enabled=true
admin.bind=0.0.0.0
admin.port=24444
admin.token=你的随机长令牌
```

然后重启电脑端机器人。日志应出现：

```text
[管理] Android 控制接口已启动: http://0.0.0.0:24444/api/status
```

手机需要能够连接到电脑的公网IP。

假设电脑 IP 是 `192.168.1.10`，App 里填写：

```text
http://192.168.1.10:24444
```

Token 填 `admin.token` 的值。

## 版本管理

- `VERSION` 保存当前版本
- `gradle.properties` 的 `APP_VERSION` 和 `APP_VERSION_CODE` 写入 APK
- 发布说明见 [VERSIONING.md](VERSIONING.md)
- 更新记录见 [CHANGELOG.md](CHANGELOG.md)

递增版本：

```powershell
.\scripts\bump-version.ps1 patch
```

Linux：

```bash
./scripts/bump-version.sh patch
```

## 构建

Android Studio 打开本目录，或命令行：

```powershell
$env:JAVA_HOME='D:\jdk17'
.\gradlew.bat assembleDebug
```

生成的 APK：

```text
app\build\outputs\apk\debug\app-debug.apk
```

安装到手机：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 使用

1. 启动电脑端机器人，确认管理接口已启动。
2. 打开 App，填写电脑地址和 Token。
3. 点“保存并连接”。
4. 点“状态”“草稿”“日志”刷新。
5. 草稿卡片上可“批准发送”或“丢弃”。
6. “手动刷视频”会调用电脑端 `browse`。
7. “编辑配置文件”会列出所有配置项，显示键名和含义；点击单项修改，保存前校验，保存后需要重启电脑端机器人生效。

## 安全说明

- 当前使用 HTTP，Token 在局域网中明文传输；不要暴露到公网。
- 建议只在可信局域网使用，并在 Windows 防火墙中只允许局域网访问 24444。
- Token 存在 App 的 SharedPreferences 中，不要共享手机或 APK。
- 如果 Token 泄露，修改电脑端 `admin.token` 并重启，App 同步更新。

## 模拟器

Android 模拟器访问宿主机用：

```text
http://192.168.1.10:24444
```

真机不能使用 `192.168.1.10`，必须用电脑的局域网 IP。

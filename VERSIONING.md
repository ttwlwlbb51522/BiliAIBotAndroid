# 版本管理

## 版本号格式

使用语义化版本 `MAJOR.MINOR.PATCH`：

- `MAJOR`：不兼容变更
- `MINOR`：向后兼容的新功能
- `PATCH`：修复缺陷、文档和小调整

## Android 版本字段

- `VERSION`：当前语义化版本，例如 `1.0.0`
- `gradle.properties` 中的 `APP_VERSION`：写入 APK `versionName`
- `gradle.properties` 中的 `APP_VERSION_CODE`：写入 APK `versionCode`，每次发布必须递增

`versionCode` 只能增加，不能减小或复用，否则 Android 无法覆盖安装。

## 发布流程

1. 修改代码并补充 `CHANGELOG.md`
2. 递增版本：

```powershell
.\scripts\bump-version.ps1 patch
```

或 Linux：

```bash
./scripts/bump-version.sh patch
```

3. 构建：

```powershell
$env:JAVA_HOME='D:\jdk17'
.\gradlew.bat clean assembleDebug testDebugUnitTest
```

4. 提交并打标签：

```bash
git add .
git commit -m "release: v1.0.1"
git tag -a v1.0.1 -m "v1.0.1"
git push origin main --tags
```

## 标签规则

- 已发布标签不移动、不覆盖、不复用
- 标签格式固定为 `vX.Y.Z`
- 每次发布必须同时更新 `VERSION`、`APP_VERSION`、`APP_VERSION_CODE`
- `CHANGELOG.md` 记录用户可见变化

# setellan for shop

`setellan for shop` 是一个 Android 端 Unity Bundle / ShopConfig 分析与定制工具。项目使用 Kotlin 与 Jetpack Compose 开发，支持获取官方资源、导入本地 `.unity3d` Bundle、解析 ShopConfig 数据，并按选中的 token 导出处理后的 Bundle。

<p align="center">© 2023-2026 setellan</p>

## 功能特性

- 官方资源列表获取与 Bundle 下载
- 本地 `.unity3d` 文件导入与分析
- UnityFS Bundle 解包与重打包
- ShopConfig TextAsset / FlatBuffer 记录扫描
- 按 token、名称、ID、分类和关键词搜索
- 支持“排除选中”和“保留选中”两种导出模式
- 基于 MD5 的分析缓存，重复文件可直接复用缓存结果
- 支持普通直写、所有文件访问、Root、Shizuku 等写入方式

## 技术栈

- Kotlin
- Jetpack Compose / Material 3
- Android Gradle Plugin 8.7.3
- Gradle Wrapper 8.10.2
- JDK 17
- OkHttp
- lz4-java
- XZ for Java
- Shizuku API

## 环境要求

- JDK 17
- Android SDK
- Android SDK Platform 34
- Android Build Tools
- Android Studio，或可用的命令行 Gradle 环境

如使用命令行构建，请在项目根目录创建 `local.properties`，并配置 Android SDK 路径：

```properties
sdk.dir=/path/to/android-sdk
```

> `local.properties` 为本机环境配置文件，不建议提交到 Git 仓库。

## 项目结构

```text
.
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/qiutool/app/
│       │   ├── core/          # Bundle 解析、ShopConfig 扫描、导出与权限逻辑
│       │   └── ui/            # Compose 界面与 ViewModel
│       └── res/               # 图标、字符串、主题等资源
├── gradle/wrapper/            # Gradle Wrapper
├── build.gradle               # 根 Gradle 配置
├── settings.gradle            # 项目模块配置
├── LICENSE
└── README.md
```

## 构建

在项目根目录执行：

```bash
./gradlew assembleDebug
```

Windows：

```powershell
.\gradlew.bat assembleDebug
```

构建完成后，Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装

连接设备或模拟器后执行：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

1. 启动应用并阅读提示说明。
2. 选择写入方式：普通直写、所有文件访问、Root 或 Shizuku。
3. 在“官方”页获取资源列表并选择资源分析，或在“本地”页导入 `.unity3d` 文件。
4. 在分析结果中搜索并勾选需要处理的 token。
5. 选择导出模式：
   - `排除选中`：移除或隐藏选中的 token。
   - `保留选中`：仅保留选中的 token。
6. 导出后按界面提示查看生成文件。

## 分析缓存

应用会根据文件 MD5 缓存分析结果，用于减少重复解析耗时：

- 官方资源优先使用资源列表中的 MD5。
- 本地文件会在导入后计算 MD5。
- MD5 相同：直接读取缓存结果。
- MD5 不同：重新分析并刷新缓存。
- 缓存会自动裁剪，避免长期占用过多空间。

## 权限说明

Android 11 及以上版本对其他应用的 `/Android/data/` 目录访问限制较多。部分写入场景可能需要 Root 或 Shizuku 权限。

如果当前设备不具备高权限环境，可以先导出到普通目录，再手动处理生成文件。

## 注意事项

- 本项目不包含任何游戏资源文件。
- 导出前建议关闭目标应用，避免文件占用或缓存自动刷新。
- 不同版本资源结构可能变化，如无法识别 ShopConfig，可能需要适配新的解析规则。
- 导出文件如明显大于原文件，应用会提示潜在风险。

## License

Apache License 2.0

Copyright © 2023-2026 setellan

请查看仓库中的 `LICENSE` 文件了解许可信息。

## Disclaimer

本项目仅用于学习和研究 Android、Unity Bundle、FlatBuffer 解析与本地文件处理技术。使用者应自行确认使用场景的合法性与合规性，并自行承担使用、修改或分发本项目产生的风险。



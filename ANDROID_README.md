# 植物病害识别 Android 应用

这是一个使用 Kotlin 和 Jetpack Compose 开发的 Android 应用，可以离线识别植物病害。

## 项目结构

```
android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── assets/              # 资源文件
│           │   ├── class_names_134.txt
│           │   ├── disease_data.json
│           │   └── model.ptl       # 需要添加转换后的模型
│           ├── java/com/plantdiseasedetector/
│           │   ├── MainActivity.kt
│           │   ├── ModelHelper.kt
│           │   └── DiseaseInfo.kt
│           └── res/
│               ├── values/
│               └── xml/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17 或更高版本
- Android SDK API 24 (Android 7.0) - minSdk
- Android SDK API 34 (Android 14) - targetSdk
- Gradle 8.2

## 构建步骤

### 1. 准备模型文件

首先需要将 PyTorch 模型转换为 TorchScript 格式：

```bash
# 确保 best_model_134.pth 在工作目录中
python convert_model.py
```

这将：
- 转换模型为 TorchScript 格式
- 复制资源文件到 assets 目录

### 2. 使用 Android Studio 构建

1. 用 Android Studio 打开 `android/` 目录
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器
4. 点击 "Run" 按钮或使用命令：

```bash
cd android
./gradlew assembleDebug
```

### 3. 生成 APK

Debug APK 路径：
```
android/app/build/outputs/apk/debug/app-debug.apk
```

Release APK：
```bash
./gradlew assembleRelease
```

## 功能特性

- 📸 拍照或从相册选择图片
- 🤖 离线推理，无需网络
- 🌿 识别 134 种植物病害
- 📊 显示识别置信度
- 💊 提供病害详细信息和防治建议
- 🎨 现代化 Material Design 3 界面

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose
- **深度学习**: PyTorch Mobile
- **架构**: MVVM

## 注意事项

1. **模型文件**: 必须确保 `android/app/src/main/assets/model.ptl` 存在
2. **权限**: 应用需要相机和存储权限
3. **性能**: 建议在 Android 8.0+ 设备上运行以获得最佳性能

## 故障排除

### 模型加载失败
- 确认 `model.ptl` 文件存在于 assets 目录
- 确认模型是使用正确的脚本转换的

### 权限问题
- 在设置中手动授予应用权限
- 确认 AndroidManifest.xml 中声明了所需权限

## 许可证

本项目仅供学习和研究使用。

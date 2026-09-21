# 背诵星球 — 安卓工程（云端自动构建）

这是背诵星球安卓源代码。APK 由 GitHub Actions 自动编译生成。
打开本仓库的 Actions → Build Android APK，选择最新成功运行，在 Artifacts 下载 Beisong-Planet-APK，解压得到 app-debug.apk。只有实际构建成功后才会出现下载。ZIP 本身不能安装，不能改名为 APK。

## 已实现的封装代码
- 原有四级背诵练习、逐字提示、提示句复练、本机保存、记忆星。
- 页面内置，无需访问原网页，也不需要 ChatGPT 登录。
- ML Kit 内置中文文字识别模型；拍照或选择相册，由用户校对识别结果。
- 安卓系统 SpeechRecognizer 语音接口及运行时麦克风权限。
- 安卓系统中文 TTS 朗读、系统文件选择器导出 TXT。
- 无广告、不内置账号或云端课文同步。

## 环境与本地生成 APK
1. 电脑安装 Android Studio，使用 JDK 17，安装 Android SDK Platform 35 与对应构建工具。
2. 安装 Gradle 8.9（本包不含 Gradle 二进制或 wrapper JAR）。
3. 将本目录作为项目打开，配置 SDK 路径；或设置 ANDROID_HOME。
4. 在本目录运行：
   `gradle :app:assembleDebug`
5. 安装包生成位置：`app/build/outputs/apk/debug/app-debug.apk`。
6. 此为自动调试签名的试用版；正式分发与后续覆盖升级需保留同一签名密钥并配置正式签名。不同电脑/云构建的调试签名可能不同。

## 可选：GitHub Actions 构建
本包提供 `.github/workflows/build-apk.yml`。如有 GitHub 仓库，将本包文件放在仓库根目录，在 Actions 中手动运行 Build Android APK；成功后从 Artifacts 下载 APK 压缩包并解压。推送到 main 会自动触发构建，也可手动运行。

## 手机安装与兼容性
目标为 Android 8.0 及以上，实际兼容性尚待编译和真机验证。取得 APK 后，在手机文件管理器打开，按系统提示仅允许该来源安装本次应用。
核心界面与背诵练习不依赖网络；识字模型随安装包提供。语音识别、中文朗读依赖手机已安装的系统语音服务，可能需要联网；无服务时使用默写或家长听背。
系统 WebView 需保持更新。照片识别准确性受清晰度影响，不适合承诺准确识别手写或复杂多栏课文。
应用只申请麦克风权限。相机使用外部系统相机，照片选择和文字导出使用系统选择器。照片保存在应用临时缓存，不上传到本应用服务器；语音可能经系统服务商处理。

## 必须完成的真机验收
- 安装、冷启动、退出再进、课文保存与删除应用后的数据清除。
- 拍照、选相册、取消选择、大图、旋转照片、OCR 错误提示。
- 允许/拒绝麦克风、无系统语音服务、联网与断网、后台暂停。
- 背对点亮、背错不通关、提示句复练、全文完成、自评标识。
- 中文朗读、停止朗读、TXT 导出、安卓返回键、横竖屏与字体放大。

## 依赖与参考
- Google ML Kit 中文识别：https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Android WebView 本地资源：https://developer.android.com/develop/ui/views/layout/webapps/load-local-content
- SpeechRecognizer：https://developer.android.com/reference/android/speech/SpeechRecognizer

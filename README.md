# 重生之我是象棋高手（安卓纯离线象棋悬浮分析）

这是一个**本地屏幕识别 + 本地 Pikafish + 悬浮箭头**的 Android MVP。它不需要账号、服务器、API 或会员。

> 当前版本不做自动点击/自动走棋，也不包含隐藏进程、规避检测等功能。建议只用于人机局、分析和学习。

## 第一版的识棋思路

不依赖通用视觉模型。因为手机棋盘 UI 固定，所以首次在**标准初始局面**做一次校准：

1. MediaProjection 获取当前手机屏幕；
2. 你点棋盘左上与右下两个交叉点；
3. APP 按 9×10 网格切出 90 个 patch；
4. 根据初始局面已知棋子位置，直接建立 15 类本地模板（14 种红黑棋子 + 空位）；
5. 后续每帧用颜色/边缘特征做模板匹配，转成 FEN；
6. FEN 送给手机本地 Pikafish；
7. 悬浮窗显示 MultiPV，透明全屏层画最佳着法箭头。

这种方案对固定棋盘皮肤很实用，也避免了 ONNX 模型与棋盘皮肤/domain mismatch。换皮肤或缩放后重新校准即可。

## 你电脑上怎么直接构建

推荐：Windows + Android Studio。

### A. 最省事

1. 安装 Android Studio，并安装 Android SDK 35。
2. PowerShell 进入项目目录。
3. 执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\prepare_assets.ps1
.\scripts\bootstrap_wrapper.ps1
```

4. 用 Android Studio 打开本项目，等 Gradle Sync 完成。
5. `Build > Build APK(s)`。

### B. 一条命令尝试构建

Android Studio SDK 已装好后：

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\build_debug.ps1
```

APK 默认输出：

`app\build\outputs\apk\debug\app-debug.apk`

## 手机使用顺序

1. 安装 APK。
2. 打开 APP，点“授权悬浮窗”。
3. 点“授权录屏并启动”。
4. 切到你的象棋 APP，并进入**标准初始局面**。
5. 点右上悬浮窗“校准”。
6. 在刚才截到的棋盘图上：
   - 第一下点最左上棋盘交叉点；
   - 第二下点最右下棋盘交叉点；
   - 如果你的视角是黑方在下，切换“视角：黑方在下”；
   - 保存。
7. 返回棋局。识别稳定后会出现最佳着法箭头，并显示 3 条 PV。
8. 如果行棋方判断错，点“换边”。

## 重要限制（MVP）

- 当前模板识别假设棋盘尺寸/皮肤不变；换皮肤需重校准。
- 选中棋子的高亮、走棋动画可能短暂影响识别；代码用连续两帧稳定后才接受新局面。
- 第一次校准必须尽量是标准初始局面。
- 目前只打包 ARM64 (`arm64-v8a`)；绝大多数现代安卓手机都是这个架构。
- MediaProjection 在 Android 14+ 必须用 `mediaProjection` 前台服务；工程已经按这个要求声明。官方要求见 Android Developers。

## Pikafish 资源

`scripts/prepare_assets.ps1` 会尝试：

- 从 Pikafish Networks 下载 `pikafish.nnue`；
- 从 Pikafish 最新 GitHub release 中自动寻找 Android ARM64 引擎；
- 把它重命名为 `libpikafish.so` 放入 `jniLibs/arm64-v8a`，利用 Android 原生库目录的可执行权限运行 UCI 引擎。

如果最新 release 没有直接暴露 Android 附件，脚本会列出附件并停止。此时从 Pikafish GitHub Actions / Release 获取 `Pikafish-Android-arm64-universal`，手动放到：

`app/src/main/jniLibs/arm64-v8a/libpikafish.so`

然后再构建。

## 源码结构

- `CaptureService.java`：MediaProjection、屏幕帧、分析循环、悬浮窗。
- `CalibrationActivity.java` / `CalibrationView.java`：点选棋盘范围。
- `TemplateModel.java`：纯本地棋子模板特征。
- `BoardRecognizer.java`：90 点识别、视角旋转、FEN。
- `PikafishEngine.java`：UCI、MultiPV、局面评分。
- `ArrowOverlayView.java`：把 `b2e2` 这类最佳着法画到原棋盘上。

## 下一步最值得做

如果你实际装上后给我一张“校准后的识别状态/错误截图”，下一版优先做：

1. 自适应 patch 半径；
2. 选中棋子高亮的二次模板；
3. 自动判断红/黑视角和行棋方；
4. 将 UCI 坐标转成“炮二平五”这类中文棋谱；
5. 可选 ONNX 棋子分类器作为模板识别失败时的 fallback。

## 不想配本机环境：GitHub Actions 直接出 APK

项目已经附带 `.github/workflows/build-apk.yml`。它会在 Ubuntu CI 中：

1. 安装 Android SDK/NDK；
2. 按 Pikafish 官方 CI 的方式编译 `Android-arm64-universal`；
3. 下载最新版 `pikafish.nnue`；
4. 构建 `app-debug.apk`；
5. 把 APK 作为 Actions Artifact 上传。

做法：

1. 新建一个 GitHub 仓库；
2. 把本项目全部文件推上去；
3. 打开 `Actions > Build Android APK > Run workflow`；
4. 完成后在该次 workflow 页面最下方下载 `XiangqiOverlayMVP-debug-apk`。

这条路线不依赖你本机安装 NDK，也避免“GitHub release 恰好没放 Android 二进制”的问题，因为 CI 直接从 Pikafish 源码编译 Android ARM64 版。

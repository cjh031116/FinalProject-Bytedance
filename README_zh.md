# Android MVP 视频流应用

这是一个示例 Android 应用，用于演示类似 TikTok 风格的垂直视频流。它使用 MVP (Model-View-Presenter) 架构构建，并展示了视频缓存、预加载和使用 ExoPlayer (Media3) 进行性能监控等高级功能。

## ✨ 功能特性

-   **垂直视频流**: 使用 `RecyclerView` 和 `PagerSnapHelper` 实现，提供流畅的逐页滚动体验。
-   **MVP 架构**: 在 UI (`View`)、业务逻辑 (`Presenter`) 和数据 (`Model`) 之间实现清晰的关注点分离。
-   **ExoPlayer (Media3)**: 利用 Google 强大的媒体播放器实现高效的视频播放。
-   **高级缓存**: 实现 `SimpleCache` 来缓存视频数据，减少网络使用并改善后续观看的加载时间。
-   **视频预加载**: 主动获取并缓存即将播放的视频数据，以确保即时播放。
-   **混合内容类型**: 信息流支持在同一个 `RecyclerView` 中展示视频项和可滑动的图片轮播。
-   **全屏模式**: 允许用户切换到横向、沉浸式的全屏模式进行视频播放。
-   **性能监控**: `Presenter` 收集并记录性能指标，如视频加载时间和缓存命中率，以供分析。

## 🏛️ 架构 (MVP)

该项目遵循 Model-View-Presenter 模式，以确保代码库的可扩展性和可维护性。

-   **Model (`VideoModel`, `VideoRepository`)**:
    -   负责提供数据。
    -   `VideoRepository` 模拟从远程或本地源获取视频元数据。
    -   `VideoModel` 处理数据相关的业务逻辑，包括触发视频预加载和管理缓存状态。

-   **View (`MainActivity`)**:
    -   一个被动的接口，用于显示数据并将用户交互路由到 Presenter。
    -   它实现了 `VideoFeedContract.View` 接口。
    -   包含 `RecyclerView` 及其 `Adapter`。它不了解任何业务逻辑。

-   **Presenter (`VideoFeedPresenter`)**:
    -   充当 Model 和 View 之间的中间人。
    -   它从 Model 检索数据，并将其格式化以在 View 中显示。
    -   处理所有业务逻辑，例如何时加载更多视频、何时播放视频以及收集性能统计信息。
    -   它实现了 `VideoFeedContract.Presenter` 接口。

-   **Contract (`VideoFeedContract`)**:
    -   一个单独的文件，定义了 View 和 Presenter 的接口，为它们之间的通信建立了清晰的契约。

## 🛠️ 关键组件

-   `MainActivity.kt`: 托管 `RecyclerView` 的主屏幕 (View)。
-   `VideoFeedMvpAdapter.kt`: `RecyclerView.Adapter`，负责将 `VideoItem` 数据绑定到视图，管理 `ExoPlayer` 实例，并处理不同的视图类型（视频 vs. 图片轮播）。
-   `VideoFeedPresenter.kt`: 包含应用业务逻辑的核心组件。
-   `VideoModel.kt`: 管理数据获取和视频预加载机制。
-   `CacheUtil.kt`: 一个单例对象，提供 ExoPlayer `SimpleCache` 的共享实例。
-   `VideoRepository.kt`: 通过提供视频和图片项的静态列表来模拟数据源。

## 🚀 如何运行

1.  克隆仓库。
2.  在 Android Studio 中打开项目。
3.  在 Android 模拟器或物理设备上构建并运行应用。

## 🧪 配置与测试

您可以直接在代码中轻松切换功能以进行测试：

-   **切换预加载**: 在 `MainActivity.kt` 中，将 `ENABLE_PRELOAD` 常量更改为 `true` 或 `false`，以在有无视频预加载的情况下测试应用的性能。
    ```kotlin
    // in MainActivity.kt
    private const val ENABLE_PRELOAD = true // or false
    ```
-   **清除缓存**: 要从干净的状态测试缓存机制，您可以在 `MainActivity.kt` 的 `onCreate` 方法中取消对 `clearCacheForTest()` 的调用注释。
    ```kotlin
    // in MainActivity.kt -> onCreate()
    // clearCacheForTest()
    ```

性能报告（包括加载时间和缓存命中率）会在应用销毁时打印到 Logcat 控制台。您可以通过标签 `VideoPresenter` 进行过滤查看。


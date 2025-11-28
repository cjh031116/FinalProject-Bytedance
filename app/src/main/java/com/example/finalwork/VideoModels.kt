package com.example.finalwork

data class VideoItem(
    val id: String,
    val title: String,
    val authorName: String,
    val avatarUrl: String?,
    val videoUrl: String,
    val likeCount: Int,
    val commentCount: Int,
    val collectCount: Int,
    val shareCount: Int,
)

class VideoRepository {
    private val masterVideoList = listOf(
        VideoItem("0", "标题 1", "作者 1", null, "http://vjs.zencdn.net/v/oceans.mp4", 101, 10, 20, 5),
        VideoItem("1", "标题 2", "作者 2", null, "https://media.w3.org/2010/05/sintel/trailer.mp4", 152, 12, 22, 8),
        VideoItem("2", "标题 3", "作者 3", null, "http://mirror.aarnet.edu.au/pub/TED-talks/911Mothers_2010W-480p.mp4", 203, 15, 25, 3),
        VideoItem("3", "标题 4", "作者 4", null, "https://cesium.com/public/SandcastleSampleData/big-buck-bunny_trailer.mp4", 98, 8, 15, 1),
        VideoItem("4", "标题 5", "作者 5", null, "video_5", 305, 30, 40, 15),
        VideoItem("5", "标题 6", "作者 6", null, "video_6", 410, 45, 50, 20),
        VideoItem("6", "标题 7", "作者 7", null, "video_7", 250, 22, 33, 11),
        VideoItem("7", "标题 8", "作者 8", null, "video_8", 180, 18, 28, 9),
        VideoItem("8", "标题 9", "作者 9", null, "video_9", 500, 50, 60, 25),
        VideoItem("9", "标题 10", "作者 10", null, "video_10", 333, 33, 44, 12)
    )
    private var page = 0

    fun loadInitial(): List<VideoItem> {
        page = 0
        return generatePage(page)
    }

    fun loadMore(): List<VideoItem> {
        page++
        return generatePage(page)
    }

    private fun generatePage(page: Int): List<VideoItem> {

        val itemsPerPage = 5 // 每次加载5个
        val items = mutableListOf<VideoItem>()
        val startIndex = page * itemsPerPage

        for (i in 0 until itemsPerPage) {
            // 使用取模运算(%)来循环从 masterVideoList 中获取视频
            val indexInMasterList = (startIndex + i) % masterVideoList.size
            val originalItem = masterVideoList[indexInMasterList]

            // 创建一个带有唯一ID的新对象，以帮助RecyclerView正确处理列表更新
            items.add(originalItem.copy(id = "instance_${startIndex + i}"))
        }
        return items
    }
}


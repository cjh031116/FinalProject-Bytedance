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
        VideoItem("0", "Ocean scene", "作者 1", null, "http://vjs.zencdn.net/v/oceans.mp4", 101, 10, 20, 5),
        VideoItem("1", "Trailer", "作者 2", null, "https://media.w3.org/2010/05/sintel/trailer.mp4", 152, 12, 22, 8),
        VideoItem("2", "TED Talk", "作者 3", null, "http://mirror.aarnet.edu.au/pub/TED-talks/911Mothers_2010W-480p.mp4", 203, 15, 25, 3),
        VideoItem("3", "Bunny", "作者 4", null, "https://cesium.com/public/SandcastleSampleData/big-buck-bunny_trailer.mp4", 98, 8, 15, 1),
        VideoItem("4", "News 1", "作者 5", null, "https://stream7.iqilu.com/10339/upload_transcode/202002/09/20200209105011F0zPoYzHry.mp4", 305, 30, 40, 15),
        VideoItem("5", "News 2", "作者 6", null, "https://stream7.iqilu.com/10339/upload_transcode/202002/09/20200209104902N3v5Vpxuvb.mp4", 410, 45, 50, 20),
        VideoItem("6", "Bear", "作者 7", null, "https://www.w3schools.com/html/movie.mp4", 250, 22, 33, 11),
        VideoItem("7", "Popular", "作者 8", null, "https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4", 180, 18, 28, 9),
        VideoItem("8", "Big Rabit", "作者 9", null, "https://www.w3school.com.cn/example/html5/mov_bbb.mp4", 500, 50, 60, 25),
        VideoItem("9", "Time", "作者 10", null, "http://devimages.apple.com/iphone/samples/bipbop/bipbopall.m3u8", 333, 33, 44, 12)
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


package com.example.finalwork

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalwork.databinding.ItemVideoBinding

class VideoFeedAdapter(private val context: Context) :
    ListAdapter<VideoItem, VideoFeedAdapter.VideoViewHolder>(VideoDiffCallback()) {

    private var player: ExoPlayer = ExoPlayer.Builder(context).build()
    private var currentPlayingPosition = RecyclerView.NO_POSITION
    private var currentPlayingHolder: VideoViewHolder? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        // Detach player from the recycled view
        if (holder == currentPlayingHolder) {
            holder.binding.playerView.player = null
        }
    }

    fun playVideoAt(position: Int, recyclerView: RecyclerView) {
        if (position == RecyclerView.NO_POSITION || position == currentPlayingPosition) {
            return
        }

        // Stop previous video if any
        currentPlayingHolder?.binding?.playerView?.player = null

        val newHolder = recyclerView.findViewHolderForAdapterPosition(position) as? VideoViewHolder
        if (newHolder != null) {
            currentPlayingPosition = position
            currentPlayingHolder = newHolder

            // Attach player to the new view
            newHolder.binding.playerView.player = player

            // Set media item and play
            val videoUrl = getItem(position).videoUrl
            val mediaItem = if (videoUrl.startsWith("http")) {
                MediaItem.fromUri(videoUrl)
            } else {
                // For local video from res/raw
                val videoId = context.resources.getIdentifier(videoUrl, "raw", context.packageName)
                val uri = Uri.parse("android.resource://${context.packageName}/$videoId")
                MediaItem.fromUri(uri)
            }

            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun pauseCurrent() {
        player.pause()
    }

    fun release() {
        player.release()
    }

    fun appendList(newList: List<VideoItem>) {
        submitList(currentList + newList)
    }

    class VideoViewHolder(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VideoItem) {
            binding.ivAvatar.setImageResource(R.mipmap.ic_launcher_round)
            binding.tvAuthor.text = item.authorName
            binding.tvTitle.text = item.title
            binding.tvLikeCount.text = item.likeCount.toString()
            binding.tvCommentCount.text = item.commentCount.toString()
            binding.tvCollectCount.text = item.collectCount.toString()
            binding.tvShareCount.text = item.shareCount.toString()
        }
    }
}

class VideoDiffCallback : DiffUtil.ItemCallback<VideoItem>() {
    override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean {
        return oldItem == newItem
    }
}

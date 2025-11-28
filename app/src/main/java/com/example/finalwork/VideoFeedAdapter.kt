package com.example.finalwork

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VideoFeedAdapter(private val context: Context) : RecyclerView.Adapter<VideoFeedAdapter.VideoViewHolder>() {

    private val items = mutableListOf<VideoItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(list: List<VideoItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun appendList(list: List<VideoItem>) {
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun playVideoAt(position: Int) {
        // TODO: 这里后续接入真正的播放逻辑
    }

    fun pauseCurrent() {
        // TODO: 暂停当前播放
    }

    fun release() {
        // TODO: 释放播放器等资源
    }

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // 去掉 PlayerView，保留信息展示控件
        private val ivAvatar: ImageView = itemView.findViewById(R.id.iv_avatar)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tv_author)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        private val tvLike: TextView = itemView.findViewById(R.id.tv_like_count)
        private val tvComment: TextView = itemView.findViewById(R.id.tv_comment_count)
        private val tvCollect: TextView = itemView.findViewById(R.id.tv_collect_count)
        private val tvShare: TextView = itemView.findViewById(R.id.tv_share_count)

        fun bind(item: VideoItem) {
            // 头像暂时用应用图标占位
            ivAvatar.setImageResource(R.mipmap.ic_launcher_round)
            tvAuthor.text = item.authorName
            tvTitle.text = item.title
            tvLike.text = item.likeCount.toString()
            tvComment.text = item.commentCount.toString()
            tvCollect.text = item.collectCount.toString()
            tvShare.text = item.shareCount.toString()
        }
    }
}

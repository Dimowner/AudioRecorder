package com.dimowner.audiorecorder.app.moverecords

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dimowner.audiorecorder.databinding.ListItemFooterBinding
import com.dimowner.audiorecorder.databinding.MoveRecordsItemBinding

private const val TYPE_ITEM = 1
private const val TYPE_FOOTER = 2

class MoveRecordsAdapter : ListAdapter<MoveRecordsItem, RecyclerView.ViewHolder>(MoveRecordsDiffUtil) {

	private var isFooterShown: Boolean = false

	var itemClickListener: ((MoveRecordsItem) -> Unit)? = null

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			TYPE_ITEM -> {
				return MoveRecordsItemViewHolder(
					MoveRecordsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
				) { position ->
					itemClickListener?.invoke(getItem(position))
				}
			}
			TYPE_FOOTER -> {
				FooterViewHolder(
					ListItemFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
				)
			}
			else -> {
				throw IllegalArgumentException("Invalid view type - $viewType")
			}
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		if (holder is MoveRecordsItemViewHolder) {
			holder.bind(getItem(position))
		}
	}

	override fun getItemCount(): Int {
		val itemCount = super.getItemCount()
		return if (isFooterShown) itemCount + 1 else itemCount
	}

	override fun getItemViewType(position: Int): Int {
		return when {
			isFooterShown && position >= itemCount - 1 -> {
				TYPE_FOOTER
			}
			else -> {
				TYPE_ITEM
			}
		}
	}

	fun showFooter(show: Boolean) {
		if (show) {
			if (!isFooterShown) {
				isFooterShown = true
				notifyItemInserted(itemCount)
			}
		} else {
			if (isFooterShown) {
				isFooterShown = false
				notifyItemRemoved(itemCount)
			}
		}
	}

	private object MoveRecordsDiffUtil : DiffUtil.ItemCallback<MoveRecordsItem>() {
		override fun areItemsTheSame(oldItem: MoveRecordsItem, newItem: MoveRecordsItem): Boolean {
			return oldItem.id == newItem.id
		}

		override fun areContentsTheSame(oldItem: MoveRecordsItem, newItem: MoveRecordsItem): Boolean {
			return oldItem == newItem
		}
	}

	internal class FooterViewHolder(binding: ListItemFooterBinding) : RecyclerView.ViewHolder(binding.root)

	internal class MoveRecordsItemViewHolder(
		private val binding: MoveRecordsItemBinding,
		itemClickListener: ((Int) -> Unit)? = null
	): RecyclerView.ViewHolder(binding.root) {

		init {
			binding.listItemDelete.setOnClickListener { itemClickListener?.invoke(bindingAdapterPosition) }
		}

		fun bind(item: MoveRecordsItem) {
			binding.listItemName.text = item.name
			binding.listItemInfo.text = item.info
		}
	}
}

data class MoveRecordsItem(
	val id: Int,
	val name: String,
	val info: String
)

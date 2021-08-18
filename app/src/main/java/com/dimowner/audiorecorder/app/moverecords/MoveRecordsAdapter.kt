package com.dimowner.audiorecorder.app.moverecords

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dimowner.audiorecorder.R
import com.dimowner.audiorecorder.databinding.ListItemFooterBinding
import com.dimowner.audiorecorder.databinding.ListItemFooterPanelBinding
import com.dimowner.audiorecorder.databinding.MoveRecordsItemBinding
import com.dimowner.audiorecorder.util.RippleUtils

private const val TYPE_ITEM = 1
private const val TYPE_FOOTER_PROGRESS = 2
private const val TYPE_FOOTER_PANEL = 3

class MoveRecordsAdapter : ListAdapter<MoveRecordsItem, RecyclerView.ViewHolder>(MoveRecordsDiffUtil) {

	private var isFooterProgressShown: Boolean = false
	private var isFooterPanelShown: Boolean = false

	var activeItem = -1
		set(value) {
			val prev = field
			field = value
			notifyItemChanged(value)
			notifyItemChanged(prev)
		}

	var itemClickListener: ((MoveRecordsItem) -> Unit)? = null
	var moveRecordClickListener: ((MoveRecordsItem) -> Unit)? = null

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			TYPE_ITEM -> {
				return MoveRecordsItemViewHolder(
					MoveRecordsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false),
					{ position ->
						itemClickListener?.invoke(getItem(position))
					}, { position ->
						moveRecordClickListener?.invoke(getItem(position))
					})
			}
			TYPE_FOOTER_PROGRESS -> {
				FooterViewHolder(
					ListItemFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
				)
			}
			TYPE_FOOTER_PANEL -> {
				FooterPanelViewHolder(
					ListItemFooterPanelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
			if (holder.absoluteAdapterPosition == activeItem) {
				holder.binding.container.setBackgroundResource(R.color.selected_item_color)
			} else {
				holder.binding.container.setBackgroundResource(android.R.color.transparent)
			}
		}
	}

	override fun getItemCount(): Int {
		val itemCount = super.getItemCount()
		val showFooter = if (isFooterProgressShown) 1 else 0
		val showFooterPanel = if (isFooterPanelShown) 1 else 0
		return itemCount + showFooter + showFooterPanel
	}

	override fun getItemViewType(position: Int): Int {
		return when {
			isFooterProgressShown && isFooterPanelShown && position == itemCount-2 -> {
				TYPE_FOOTER_PROGRESS
			}
			isFooterProgressShown && isFooterPanelShown && position >= itemCount-1 -> {
				TYPE_FOOTER_PANEL
			}
			isFooterPanelShown && position >= itemCount-1 -> {
				TYPE_FOOTER_PANEL
			}
			isFooterProgressShown && position >= itemCount-1 -> {
				TYPE_FOOTER_PROGRESS
			}
			else -> {
				TYPE_ITEM
			}
		}
	}

	fun showFooterProgress(show: Boolean) {
		if (show) {
			if (!isFooterProgressShown) {
				isFooterProgressShown = true
				notifyItemInserted(itemCount)
			}
		} else {
			if (isFooterProgressShown) {
				isFooterProgressShown = false
				notifyItemRemoved(itemCount)
			}
		}
	}

	fun showFooterPanel(show: Boolean) {
		if (show) {
			if (!isFooterPanelShown) {
				isFooterPanelShown = true
				notifyItemInserted(itemCount)
			}
		} else {
			if (isFooterPanelShown) {
				isFooterPanelShown = false
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
	internal class FooterPanelViewHolder(binding: ListItemFooterPanelBinding) : RecyclerView.ViewHolder(binding.root)

	internal class MoveRecordsItemViewHolder(
		val binding: MoveRecordsItemBinding,
		itemClickListener: ((Int) -> Unit)? = null,
		moveRecordsClickListener: ((Int) -> Unit)? = null
	): RecyclerView.ViewHolder(binding.root) {

		init {
			binding.container.setOnClickListener { itemClickListener?.invoke(bindingAdapterPosition) }
			binding.btnMove.setOnClickListener { moveRecordsClickListener?.invoke(bindingAdapterPosition) }
			binding.btnMove.background = RippleUtils.createRippleShape(
				ContextCompat.getColor(binding.btnMove.context, R.color.white_transparent_80),
				ContextCompat.getColor(binding.btnMove.context, R.color.white_transparent_50),
				binding.btnMove.context.resources.getDimension(R.dimen.spacing_normal)
			)
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

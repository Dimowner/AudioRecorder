package com.dimowner.audiorecorder.app.trash;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.lostrecords.RecordItem;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 14.12.2019.
 * @author Dimowner
 */
public class TrashAdapter extends RecyclerView.Adapter<TrashAdapter.ItemViewHolder> {

	private List<RecordItem> data;
	private OnItemClickListener onItemClickListener;

	TrashAdapter() {
		this.data = new ArrayList<>();
	}

	public void setData(List<RecordItem> list) {
		if (!data.isEmpty()) {
			data.clear();
		}
		data.addAll(list);
		notifyDataSetChanged();
	}

	void removeItem(int id) {
		int pos = -1;
		for (int i = 0; i < data.size(); i++) {
			if (id == data.get(i).getId()) {
				pos = i;
				break;
			}
		}
		if (pos >= 0 && pos < data.size()) {
			data.remove(pos);
			notifyDataSetChanged();
		}
	}

	void clearData() {
		data.clear();
		notifyDataSetChanged();
	}

	public List<RecordItem> getData() {
		return data;
	}

	@NonNull
	@Override
	public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_trash, viewGroup, false);
		return new ItemViewHolder(v);
	}

	@Override
	public void onBindViewHolder(@NonNull ItemViewHolder holder, final int position) {
		final int pos = holder.getAdapterPosition();
		if (pos != RecyclerView.NO_POSITION) {
			holder.name.setText(data.get(position).getName());
			holder.duration.setText(TimeUtils.formatTimeIntervalMinSec(data.get(position).getDuration()/1000));
			holder.view.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onItemClickListener != null) {
						onItemClickListener.onItemClick(data.get(position));
					}
				}
			});
			holder.btnDelete.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onItemClickListener != null) {
						onItemClickListener.onDeleteItemClick(data.get(position));
					}
				}
			});
			holder.btnRestore.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onItemClickListener != null) {
						onItemClickListener.onRestoreItemClick(data.get(position));
					}
				}
			});
		}
	}

	@Override
	public int getItemCount() {
		return data.size();
	}

	void setOnItemClickListener(OnItemClickListener onItemClickListener) {
		this.onItemClickListener = onItemClickListener;
	}

	class ItemViewHolder extends RecyclerView.ViewHolder {
		TextView name;
		TextView duration;
		ImageButton btnDelete;
		ImageButton btnRestore;
		View view;

		ItemViewHolder(View itemView) {
			super(itemView);
			view = itemView;
			name = itemView.findViewById(R.id.list_item_name);
			duration = itemView.findViewById(R.id.list_item_location);
			btnDelete = itemView.findViewById(R.id.list_item_delete);
			btnRestore = itemView.findViewById(R.id.list_item_restore);
		}
	}

	public interface OnItemClickListener {
		void onItemClick(RecordItem record);
		void onDeleteItemClick(RecordItem record);
		void onRestoreItemClick(RecordItem record);
	}
}

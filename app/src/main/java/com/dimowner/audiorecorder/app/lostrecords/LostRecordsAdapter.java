package com.dimowner.audiorecorder.app.lostrecords;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.dimowner.audiorecorder.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 14.12.2019.
 *
 * @author Dimowner
 */
public class LostRecordsAdapter extends RecyclerView.Adapter<LostRecordsAdapter.ItemViewHolder> {

	private List<RecordItem> data;
	private OnItemClickListener onItemClickListener;

	LostRecordsAdapter() {
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
			}
		}
		if (pos >= 0 && pos < data.size()) {
			data.remove(pos);
			notifyItemRemoved(pos);
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
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.list_item_lost, viewGroup, false);
		return new ItemViewHolder(v);
	}

	@Override
	public void onBindViewHolder(@NonNull ItemViewHolder holder, final int position) {
		final int pos = holder.getAdapterPosition();
		if (pos != RecyclerView.NO_POSITION) {
			holder.name.setText(data.get(pos).getName());
			holder.location.setText(data.get(pos).getPath());
			holder.btnDelete.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onItemClickListener != null) {
						onItemClickListener.onItemClick(data.get(pos));
					}
				}
			});
		}
	}

	@Override
	public int getItemCount() {
		return data.size();
	}

	public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
		this.onItemClickListener = onItemClickListener;
	}

	class ItemViewHolder extends RecyclerView.ViewHolder {
		TextView name;
		TextView location;
		ImageButton btnDelete;
		View view;

		ItemViewHolder(View itemView) {
			super(itemView);
			view = itemView;
			name = itemView.findViewById(R.id.list_item_name);
			location = itemView.findViewById(R.id.list_item_location);
			btnDelete = itemView.findViewById(R.id.list_item_delete);
		}
	}

	public interface OnItemClickListener {
		void onItemClick(RecordItem record);
	}
}

/*
 * Copyright 2020 Dmytro Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.app.lostrecords;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 14.12.2019.
 *
 * @author Dimowner
 */
public class LostRecordsAdapter extends RecyclerView.Adapter<LostRecordsAdapter.ItemViewHolder> {

	private final List<RecordItem> data;
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
			//this line below gives you the animation and also updates the
			//list items after the deleted item
			notifyItemRangeChanged(pos, getItemCount());
		}
	}

	void clearData() {
		data.clear();
		notifyDataSetChanged();
	}

	public List<RecordItem> getData() {
		return new ArrayList<>(data);
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
			holder.duration.setText(TimeUtils.formatTimeIntervalHourMinSec2(data.get(pos).getDuration()/1000));
			holder.view.setOnClickListener(v -> {
				if (onItemClickListener != null) {
					onItemClickListener.onItemClick(data.get(pos));
				}
			});
			holder.btnDelete.setOnClickListener(v -> {
				if (onItemClickListener != null) {
					onItemClickListener.onRemoveItemClick(data.get(pos));
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

	static class ItemViewHolder extends RecyclerView.ViewHolder {
		TextView name;
		TextView location;
		TextView duration;
		ImageButton btnDelete;
		View view;

		ItemViewHolder(View itemView) {
			super(itemView);
			view = itemView;
			name = itemView.findViewById(R.id.list_item_name);
			duration = itemView.findViewById(R.id.list_item_duration);
			location = itemView.findViewById(R.id.list_item_location);
			btnDelete = itemView.findViewById(R.id.list_item_delete);
		}
	}

	interface OnItemClickListener {
		void onItemClick(RecordItem record);
		void onRemoveItemClick(RecordItem record);
	}
}

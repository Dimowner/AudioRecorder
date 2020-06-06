/*
 * Copyright 2020 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder.app.browser;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.info.RecordInfo;
import com.dimowner.audiorecorder.app.settings.SettingsMapper;
import com.dimowner.audiorecorder.util.RippleUtils;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 30.05.2020.
 * @author Dimowner
 */
public class FileBrowserAdapter extends RecyclerView.Adapter<FileBrowserAdapter.ItemViewHolder> {

	private List<RecordInfo> data;
	private SettingsMapper settingsMapper;
	private OnItemClickListener onItemClickListener;
	private int colorInTrash;
	private int colorFound;
	private int colorNotFound;
	private int radius;

	FileBrowserAdapter(Context context, SettingsMapper settingsMapper) {
		this.settingsMapper = settingsMapper;
		this.data = new ArrayList<>();
		try {
			radius = (int) context.getResources().getDimension(R.dimen.spacing_tiny);
			colorInTrash = ContextCompat.getColor(context, R.color.md_yellow_800F);
			colorFound = ContextCompat.getColor(context, R.color.md_green_600);
			colorNotFound = ContextCompat.getColor(context, R.color.md_red_700);
		} catch (Resources.NotFoundException e) {
			radius = 8;
			colorInTrash = Color.YELLOW;
			colorFound = Color.GREEN;
			colorNotFound = Color.RED;
		}
	}

	public void setData(List<RecordInfo> list) {
		if (!data.isEmpty()) {
			data.clear();
		}
		data.addAll(list);
		notifyDataSetChanged();
	}

	void removeItem(String path) {
		int pos = -1;
		for (int i = 0; i < data.size(); i++) {
			if (path.equals(data.get(i).getLocation())) {
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

	void setRecordInDatabase(String path) {
		int pos = -1;
		for (int i = 0; i < data.size(); i++) {
			if (path.equals(data.get(i).getLocation())) {
				pos = i;
			}
		}
		if (pos >= 0 && pos < data.size()) {
			data.get(pos).setInDatabase(true);
			notifyItemChanged(pos);
		}
	}

	void clearData() {
		data.clear();
		notifyDataSetChanged();
	}

	public List<RecordInfo> getData() {
		return data;
	}

	@NonNull
	@Override
	public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.list_item_file_browser, viewGroup, false);
		return new ItemViewHolder(v);
	}

	@Override
	public void onBindViewHolder(@NonNull ItemViewHolder holder, final int position) {
		final int pos = holder.getAdapterPosition();
		if (pos != RecyclerView.NO_POSITION) {
			RecordInfo rec = data.get(pos);
			holder.name.setText(rec.getName());
			if (rec.isInTrash()) {
				holder.status.setText(R.string.in_trash);
				holder.status.setBackground(RippleUtils.createShape(colorInTrash, radius));
			} else if (!rec.isInDatabase()) {
				holder.status.setText(R.string.not_found_in_the_app);
				holder.status.setBackground(RippleUtils.createShape(colorNotFound, radius));
			} else {
				holder.status.setText(R.string.found_in_the_app);
				holder.status.setBackground(RippleUtils.createShape(colorFound, radius));
			}
			updateInformation(holder.info, rec.getFormat(), rec.getSampleRate(), rec.getSize(), rec.getDuration()/1000);
			if (rec.isInDatabase() || rec.isInTrash()) {
				holder.actionPanel.setVisibility(View.GONE);
			} else {
				holder.actionPanel.setVisibility(View.VISIBLE);
			}
			holder.view.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onItemClickListener != null) {
						onItemClickListener.onItemClick(data.get(pos));
					}
				}
			});
			holder.btnImport.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onItemClickListener != null) {
						onItemClickListener.onImportItemClick(data.get(pos));
					}
				}
			});
			holder.btnDownload.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onItemClickListener != null) {
						onItemClickListener.onDownloadItemClick(data.get(pos));
					}
				}
			});
			holder.btnDelete.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (onItemClickListener != null) {
						onItemClickListener.onRemoveItemClick(data.get(pos));
					}
				}
			});
		}
	}

	@Override
	public int getItemCount() {
		return data.size();
	}

	private void updateInformation(TextView view, String format, int sampleRate, long size, long duration) {
		if (format.equals(AppConstants.FORMAT_3GP)) {
			view.setText(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
					+ settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
					+ settingsMapper.convertSampleRateToString(sampleRate) + AppConstants.SEPARATOR
					+ TimeUtils.formatTimeIntervalHourMinSec2(duration)
			);
		} else {
			switch (format) {
				case AppConstants.FORMAT_M4A:
				case AppConstants.FORMAT_WAV:
					view.setText(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
							+ settingsMapper.convertFormatsToString(format) + AppConstants.SEPARATOR
							+ settingsMapper.convertSampleRateToString(sampleRate) + AppConstants.SEPARATOR
							+ TimeUtils.formatTimeIntervalHourMinSec2(duration)
					);
					break;
				default:
					view.setText(settingsMapper.formatSize(size) + AppConstants.SEPARATOR
							+ format + AppConstants.SEPARATOR
							+ settingsMapper.convertSampleRateToString(sampleRate) + AppConstants.SEPARATOR
							+ TimeUtils.formatTimeIntervalHourMinSec2(duration)
					);
			}
		}
	}

	public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
		this.onItemClickListener = onItemClickListener;
	}

	class ItemViewHolder extends RecyclerView.ViewHolder {
		TextView name;
		TextView info;
		TextView status;
		Button btnImport;
		Button btnDownload;
		Button btnDelete;
		LinearLayout actionPanel;
		View view;

		ItemViewHolder(View itemView) {
			super(itemView);
			view = itemView;
			name = itemView.findViewById(R.id.list_item_name);
			info = itemView.findViewById(R.id.list_item_info);
			status = itemView.findViewById(R.id.list_item_status);
			actionPanel = itemView.findViewById(R.id.list_item_action_panel);
			btnImport = itemView.findViewById(R.id.list_item_btn_import);
			btnDownload = itemView.findViewById(R.id.list_item_btn_download);
			btnDelete = itemView.findViewById(R.id.list_item_btn_delete);
		}
	}

	public interface OnItemClickListener {
		void onItemClick(RecordInfo record);
		void onImportItemClick(RecordInfo record);
		void onDownloadItemClick(RecordInfo record);
		void onRemoveItemClick(RecordInfo record);
	}
}

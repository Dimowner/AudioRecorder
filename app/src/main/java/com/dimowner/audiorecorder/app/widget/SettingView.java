package com.dimowner.audiorecorder.app.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dimowner.audiorecorder.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Created on 07.04.2020.
 * @author Dimowner
 */
public class SettingView extends LinearLayout {

	private ChipsView chipsView;
	private TextView txtTitle;
	private ImageButton btnInfo;
	private ImageView imgInfo;

	public SettingView(@NonNull Context context) {
		super(context);
		init();
	}

	public SettingView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public SettingView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	public SettingView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	private void init() {
		setOrientation(VERTICAL);
		inflate(getContext(), R.layout.view_setting_view, this);

		chipsView = findViewById(R.id.chips_view);
		txtTitle = findViewById(R.id.setting_title);
		btnInfo = findViewById(R.id.setting_btn_info);
		imgInfo = findViewById(R.id.setting_image);
	}

	@Override
	public void setVisibility(int visibility) {
		super.setVisibility(visibility);
		chipsView.setVisibility(visibility);
	}

	public void setData(String[] names, String[] keys, int[] colors) {
		chipsView.setData(names, keys, colors);
	}

	public void setData(String[] names, String[] keys) {
		chipsView.setData(names, keys, null);
	}

	public void removeChip(String[] keys) {
		chipsView.removeChip(keys);
	}

	public void addChip(String[] keys, String[] names) {
		chipsView.addChip(keys, names);
	}

	public void setSelected(String key) {
		chipsView.setSelected(key);
	}

	public String getSelected() {
		return chipsView.getSelected();
	}

	public void setOnChipCheckListener(ChipsView.OnCheckListener l) {
		chipsView.setOnChipCheckListener(l);
	}

	public void setTitle(String title) {
		txtTitle.setText(title);
	}

	public void setTitle(int resId) {
		txtTitle.setText(resId);
	}

	public void setImageInfo(int imgRes) {
		imgInfo.setImageResource(imgRes);
	}

	public void setEnabled(boolean enabled) {
		chipsView.setEnabled(enabled);
	}

	public void setOnInfoClickListener(View.OnClickListener listener) {
		btnInfo.setOnClickListener(listener);
	}
}

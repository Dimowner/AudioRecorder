/*
 * Copyright 2019 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder.app.info;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.TimeUtils;

public class ActivityInformation extends Activity {

	private static final String KEY_INFO = "key_info";

	public static Intent getStartIntent(Context context, RecordInfo info) {
		Intent intent = new Intent(context, ActivityInformation.class);
		intent.putExtra(KEY_INFO, info);
		return intent;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		ColorMap colorMap = ARApplication.getInjector().provideColorMap();
		setTheme(colorMap.getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_info);

		getWindow().setFlags(
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
		LinearLayout toolbar = findViewById(R.id.toolbar);
		toolbar.setPadding(0, AndroidUtils.getStatusBarHeight(getApplicationContext()), 0, 0);

		Bundle extras = getIntent().getExtras();
		TextView txtName = findViewById(R.id.txt_name);
		TextView txtFormat = findViewById(R.id.txt_format);
		TextView txtDuration = findViewById(R.id.txt_duration);
		TextView txtSize = findViewById(R.id.txt_size);
		TextView txtLocation = findViewById(R.id.txt_location);
		TextView txtCreated = findViewById(R.id.txt_created);
		TextView txtSampleRate = findViewById(R.id.txt_sample_rate);
		TextView txtChannelsCount = findViewById(R.id.txt_channels_count);
		TextView txtBitrate = findViewById(R.id.txt_bitrate);
		TextView lblBitrate = findViewById(R.id.lbl_bitrate);

		if (extras != null) {
			if (extras.containsKey(KEY_INFO)) {
				RecordInfo info = extras.getParcelable(KEY_INFO);
				if (info != null) {
					txtName.setText(info.getName());
					txtFormat.setText(info.getFormat());
					txtDuration.setText(TimeUtils.formatTimeIntervalHourMinSec2(info.getDuration()/1000));
					txtSize.setText(ARApplication.getInjector().provideSettingsMapper().formatSize(info.getSize()));
					txtLocation.setText(info.getLocation());
					txtCreated.setText(TimeUtils.formatDateTime(info.getCreated()));
					txtSampleRate.setText(getString(R.string.value_hz, info.getSampleRate()));
					switch (info.getChannelCount()) {
						case 1:
							txtChannelsCount.setText(R.string.mono);
							break;
						case 2:
							txtChannelsCount.setText(R.string.stereo);
							break;
					}
					if (info.getFormat().equals(AppConstants.FORMAT_WAV) || info.getFormat().equals(AppConstants.FORMAT_FLAC)) {
						txtBitrate.setVisibility(View.GONE);
						lblBitrate.setVisibility(View.GONE);
					} else {
						txtBitrate.setVisibility(View.VISIBLE);
						lblBitrate.setVisibility(View.VISIBLE);
						txtBitrate.setText(getString(R.string.value_kbps, info.getBitrate()/1000));
					}
				}
			}
		}

		findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
	}
}

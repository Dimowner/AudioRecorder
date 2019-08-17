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
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.TimeUtils;

public class ActivityInformation extends Activity {

	private static final String KEY_NAME = "pref_name";
	private static final String KEY_FORMAT = "pref_format";
	private static final String KEY_DURATION = "pref_duration";
	private static final String KEY_SIZE = "pref_size";
	private static final String KEY_LOCATION = "pref_location";

	private ColorMap colorMap;


	public static Intent getStartIntent(Context context, String name, String format, long duration, long size, String location) {
		Intent intent = new Intent(context, ActivityInformation.class);
		intent.putExtra(KEY_NAME, name);
		intent.putExtra(KEY_FORMAT, format);
		intent.putExtra(KEY_DURATION, duration);
		intent.putExtra(KEY_SIZE, size);
		intent.putExtra(KEY_LOCATION, location);
		return intent;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		colorMap = ARApplication.getInjector().provideColorMap();
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

		if (extras != null) {
			if (extras.containsKey(KEY_NAME)) {
				txtName.setText(extras.getString(KEY_NAME));
			}
			if (extras.containsKey(KEY_FORMAT)) {
				txtFormat.setText(extras.getString(KEY_FORMAT));
			}
			if (extras.containsKey(KEY_DURATION)) {
				txtDuration.setText(TimeUtils.formatTimeIntervalHourMinSec2(extras.getLong(KEY_DURATION)));
			}
			if (extras.containsKey(KEY_SIZE)) {
				txtSize.setText(AndroidUtils.formatSize(extras.getLong(KEY_SIZE)));
			}
			if (extras.containsKey(KEY_LOCATION)) {
				txtLocation.setText(extras.getString(KEY_LOCATION));
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

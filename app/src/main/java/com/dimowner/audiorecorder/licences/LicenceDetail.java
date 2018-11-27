/*
 * Copyright 2018 Dmitriy Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.licences;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;

/**
 * Activity shows licence details.
 * @author Dimowner
 */
public class LicenceDetail extends Activity implements View.OnClickListener {

	public static final String EXTRAS_KEY_LICENCE_ITEM_POS = "licence_item_pos";

	private ColorMap colorMap;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		colorMap = ARApplication.getInjector().provideColorMap();
		setTheme(colorMap.getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_licence_detail);

		String licenceTitle;
		String licenceLocation;
		if (getIntent().hasExtra(EXTRAS_KEY_LICENCE_ITEM_POS)) {
			int pos = getIntent().getIntExtra(EXTRAS_KEY_LICENCE_ITEM_POS, -1);
			if (pos > -1) {
				String[] licences = getResources().getStringArray(R.array.licences_assets_locations);
				licenceLocation = licences[pos];
				String[] licenceNames = getResources().getStringArray(R.array.licences_names);
				licenceTitle = licenceNames[pos];
			} else {
				licenceLocation = null;
				licenceTitle = "";
			}
		} else {
			licenceLocation = null;
			licenceTitle = "";
		}

		if (licenceLocation != null) {
			WebView mAboutWebText = findViewById(R.id.licence_html_text);
			mAboutWebText.loadUrl(licenceLocation);
		}

		LinearLayout toolbar = findViewById(R.id.toolbar);
		toolbar.setBackgroundResource(colorMap.getPrimaryColorRes());

		ImageButton btnBack = findViewById(R.id.btn_back);
		btnBack.setOnClickListener(this);
		TextView txtTitle = findViewById(R.id.title);
		txtTitle.setText(licenceTitle);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.btn_back:
				finish();
		}
	}
}

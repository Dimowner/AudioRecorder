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

package com.dimowner.audiorecorder.app.licences;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;

/**
 * Activity shows list which contains all licences used in app.
 * @author Dimowner
 */
public class LicenceActivity extends Activity implements View.OnClickListener {

	private ColorMap colorMap;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		colorMap = ARApplication.getInjector().provideColorMap();
		setTheme(colorMap.getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.ativity_licences);

		ImageButton btnBack = findViewById(R.id.btn_back);
		btnBack.setOnClickListener(this);

		String[] licenceNames = getResources().getStringArray(R.array.licences_names);
		ListView list = findViewById(R.id.licence_list);
		ArrayAdapter<String> adapter = new ArrayAdapter<>(
				this, android.R.layout.simple_list_item_1, android.R.id.text1, licenceNames);
		list.setAdapter(adapter);

		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Intent intent = new Intent(getApplicationContext(), LicenceDetail.class);
				intent.putExtra(LicenceDetail.EXTRAS_KEY_LICENCE_ITEM_POS, position);
				startActivity(intent);
			}
		});

		LinearLayout toolbar = findViewById(R.id.toolbar);
		TextView txtDescr = findViewById(R.id.txt_description);

		toolbar.setBackgroundResource(colorMap.getPrimaryColorRes());
		txtDescr.setBackgroundResource(colorMap.getPrimaryColorRes());
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.btn_back:
				finish();
		}
	}
}

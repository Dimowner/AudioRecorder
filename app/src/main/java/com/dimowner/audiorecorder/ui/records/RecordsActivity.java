package com.dimowner.audiorecorder.ui.records;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;

public class RecordsActivity extends Activity {

	public static Intent getStartIntent(Context context) {
		return new Intent(context, RecordsActivity.class);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setTheme(ARApplication.getAppThemeResource(getApplicationContext()));
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_records);
	}
}

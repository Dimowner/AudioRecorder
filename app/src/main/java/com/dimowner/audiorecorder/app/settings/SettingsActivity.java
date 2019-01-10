/*
 * Copyright 2018 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder.app.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.app.licences.LicenceActivity;

public class SettingsActivity extends Activity implements SettingsContract.View, View.OnClickListener {

	private static final String VERSION_UNAVAILABLE = "N/A";

	private TextView txtTotalDuration;
	private TextView txtRecordsCount;
	private TextView txtAvailableSpace;

	private SettingsContract.UserActionsListener presenter;


	public static Intent getStartIntent(Context context) {
		Intent intent = new Intent(context, SettingsActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		return intent;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setTheme(ARApplication.getInjector().provideColorMap().getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		ImageButton btnBack = findViewById(R.id.btn_back);
		TextView btnDeleteAll = findViewById(R.id.btnDeleteAll);
		TextView btnLicences = findViewById(R.id.btnLicences);
		TextView btnRate = findViewById(R.id.btnRate);
		TextView txtAbout = findViewById(R.id.txtAbout);
		txtAbout.setText(getAboutContent());
		btnBack.setOnClickListener(this);
		btnDeleteAll.setOnClickListener(this);
		btnLicences.setOnClickListener(this);
		btnRate.setOnClickListener(this);
		Switch swPublicDir = findViewById(R.id.swPublicDir);

		txtRecordsCount = findViewById(R.id.txt_records_count);
		txtTotalDuration= findViewById(R.id.txt_total_duration);
		txtAvailableSpace = findViewById(R.id.txt_available_space);

		final Prefs prefs = ARApplication.getInjector().providePrefs();
		swPublicDir.setChecked(prefs.isStoreDirPublic());
		swPublicDir.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton btn, boolean isChecked) {
				prefs.setStoreDirPublic(isChecked);
			}
		});
		presenter = ARApplication.getInjector().provideSettingsPresenter();
	}

	@Override
	protected void onStart() {
		super.onStart();
		presenter.bindView(this);
		presenter.loadSettings();
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (presenter != null) {
			presenter.unbindView();
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.btn_back:
				ARApplication.getInjector().releaseSettingsPresenter();
				finish();
				break;
			case R.id.btnLicences:
				startActivity(new Intent(getApplicationContext(), LicenceActivity.class));
				break;
			case R.id.btnRate:
				rateApp();
				break;
			case R.id.btnDeleteAll:
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.warning)
						.setIcon(R.drawable.ic_delete_forever)
						.setMessage(R.string.delete_all_records)
						.setCancelable(false)
						.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								presenter.deleteAllRecords();
								dialog.dismiss();
							}
						})
						.setNegativeButton(R.string.btn_no,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										dialog.dismiss();
									}
								});
				AlertDialog alert = builder.create();
				alert.show();
				break;
		}
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		ARApplication.getInjector().releaseSettingsPresenter();
	}

	public void rateApp() {
		try {
			Intent rateIntent = rateIntentForUrl("market://details");
			startActivity(rateIntent);
		} catch (ActivityNotFoundException e) {
			Intent rateIntent = rateIntentForUrl("https://play.google.com/store/apps/details");
			startActivity(rateIntent);
		}
	}

	private Intent rateIntentForUrl(String url) {
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format("%s?id=%s", url, getApplicationContext().getPackageName())));
		int flags = Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
		if (Build.VERSION.SDK_INT >= 21) {
			flags |= Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
		} else {
			flags |= Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET;
		}
		intent.addFlags(flags);
		return intent;
	}

	public SpannableStringBuilder getAboutContent() {
		// Get app version;
		String packageName = getPackageName();
		String versionName;
		try {
			PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
			versionName = info.versionName;
		} catch (PackageManager.NameNotFoundException e) {
			versionName = VERSION_UNAVAILABLE;
		}

		// Build the about body view and append the link to see OSS licenses
		SpannableStringBuilder aboutBody = new SpannableStringBuilder();
		aboutBody.append(Html.fromHtml(getString(R.string.about_body, versionName)));
		return aboutBody;
	}

	@Override
	public void showSelectedThemeColor(int colorRes) {

	}

	@Override
	public void showRecordingQuality(int quality) {

	}

	@Override
	public void showRecordingChannelsCount(int count) {

	}

	@Override
	public void showAllRecordsDeleted() {
		Toast.makeText(getApplicationContext(), R.string.all_records_deleted, Toast.LENGTH_LONG).show();
	}

	@Override
	public void showFailDeleteAllRecords() {
		Toast.makeText(getApplicationContext(), R.string.failed_to_delete_all_records, Toast.LENGTH_LONG).show();
	}

	@Override
	public void showTotalRecordsDuration(String duration) {
		txtTotalDuration.setText(getResources().getString(R.string.total_duration, duration));
	}

	@Override
	public void showRecordsCount(int count) {
		txtRecordsCount.setText(getResources().getString(R.string.total_record_count, count));
	}

	@Override
	public void showAvailableSpace(String space) {
		txtAvailableSpace.setText(getResources().getString(R.string.available_space, space));
	}

	@Override
	public void showProgress() {
//		TODO: showProgress
	}

	@Override
	public void hideProgress() {
//		TODO: hideProgress
	}

	@Override
	public void showError(String message) {
		Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
	}

	@Override
	public void showError(int resId) {
		Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
	}
}

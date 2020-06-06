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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.browser.FileBrowserActivity;
import com.dimowner.audiorecorder.app.trash.TrashActivity;
import com.dimowner.audiorecorder.app.widget.ChipsView;
import com.dimowner.audiorecorder.app.widget.SettingView;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.RippleUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import timber.log.Timber;

public class SettingsActivity extends Activity implements SettingsContract.View, View.OnClickListener {

	private TextView txtTotalDuration;
	private TextView txtRecordsCount;
	private TextView txtAvailableSpace;
	private TextView txtSizePerMin;
	private TextView txtInformation;
	private TextView txtLocation;

	private Switch swPublicDir;
	private Switch swKeepScreenOn;
	private Switch swAskToRename;

	private Spinner themeColor;
	private Spinner nameFormatSelector;

	private SettingView formatSetting;
	private SettingView sampleRateSetting;
	private SettingView bitrateSetting;
	private SettingView channelsSetting;
	private Button btnReset;

	private SettingsContract.UserActionsListener presenter;
	private ColorMap colorMap;
	private ColorMap.OnThemeColorChangeListener onThemeColorChangeListener;
	private CompoundButton.OnCheckedChangeListener publicDirListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton btn, boolean isChecked) {
			presenter.storeInPublicDir(getApplicationContext(), isChecked);
			if (isChecked) {
				showDialogPublicDirInfo();
			} else {
				showDialogPrivateDirInfo();
			}
		}
	};

	private String[] formats;
	private String[] formatsKeys;
	private String[] sampleRates;
	private String[] sampleRatesKeys;
	private String[] rates;
	private String[] rateKeys;
	private String[] recChannels;
	private String[] recChannelsKeys;

	public static Intent getStartIntent(Context context) {
		Intent intent = new Intent(context, SettingsActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		return intent;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		colorMap = ARApplication.getInjector().provideColorMap();
		setTheme(colorMap.getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		getWindow().setFlags(
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
		LinearLayout toolbar = findViewById(R.id.toolbar);
		toolbar.setPadding(0, AndroidUtils.getStatusBarHeight(getApplicationContext()), 0, 0);

		View space = findViewById(R.id.space);
		ViewGroup.LayoutParams params = space.getLayoutParams();
		params.height = AndroidUtils.getNavigationBarHeight(getApplicationContext());
		space.setLayoutParams(params);

		btnReset = findViewById(R.id.btnReset);
		btnReset.setOnClickListener(this);
		txtSizePerMin = findViewById(R.id.txt_size_per_min);
		txtInformation = findViewById(R.id.txt_information);
		txtLocation = findViewById(R.id.txt_records_location);
		txtLocation.setOnClickListener(this);
		findViewById(R.id.btnBack).setOnClickListener(this);
		TextView txtAbout = findViewById(R.id.txtAbout);
		txtAbout.setText(getAboutContent());
		findViewById(R.id.btnTrash).setOnClickListener(this);
		findViewById(R.id.btn_file_browser).setOnClickListener(this);
		findViewById(R.id.btnRate).setOnClickListener(this);
		findViewById(R.id.btnRequest).setOnClickListener(this);
		findViewById(R.id.btnPatreon).setOnClickListener(this);
		swPublicDir = findViewById(R.id.swPublicDir);
		swKeepScreenOn = findViewById(R.id.swKeepScreenOn);
		swAskToRename = findViewById(R.id.swAskToRename);

		txtRecordsCount = findViewById(R.id.txt_records_count);
		txtTotalDuration= findViewById(R.id.txt_total_duration);
		txtAvailableSpace = findViewById(R.id.txt_available_space);

		swPublicDir.setOnCheckedChangeListener(publicDirListener);

		swKeepScreenOn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton btn, boolean isChecked) {
				presenter.keepScreenOn(isChecked);
			}
		});
		swAskToRename.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton btn, boolean isChecked) {
				presenter.askToRenameAfterRecordingStop(isChecked);
			}
		});

		formatSetting = findViewById(R.id.setting_recording_format);
		formats = getResources().getStringArray(R.array.formats2);
		formatsKeys = new String[] {
				AppConstants.FORMAT_M4A,
				AppConstants.FORMAT_WAV,
				AppConstants.FORMAT_3GP
		};
		formatSetting.setData(formats, formatsKeys);
		formatSetting.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				presenter.setSettingRecordingFormat(key);
			}
		});
		formatSetting.setTitle(R.string.recording_format);
		formatSetting.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showInfoDialog(SettingsActivity.this, R.string.info_format);
			}
		});

		sampleRateSetting = findViewById(R.id.setting_frequency);
		sampleRates = getResources().getStringArray(R.array.sample_rates2);
		sampleRatesKeys = new String[] {
				SettingsMapper.SAMPLE_RATE_8000,
				SettingsMapper.SAMPLE_RATE_16000,
				SettingsMapper.SAMPLE_RATE_22050,
				SettingsMapper.SAMPLE_RATE_32000,
				SettingsMapper.SAMPLE_RATE_44100,
				SettingsMapper.SAMPLE_RATE_48000,
		};
		sampleRateSetting.setData(sampleRates, sampleRatesKeys);
		sampleRateSetting.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				presenter.setSettingSampleRate(SettingsMapper.keyToSampleRate(key));
			}
		});
		sampleRateSetting.setTitle(R.string.sample_rate);
		sampleRateSetting.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showInfoDialog(SettingsActivity.this, R.string.info_frequency);
			}
		});

		bitrateSetting = findViewById(R.id.setting_bitrate);
		rates = getResources().getStringArray(R.array.bit_rates2);
		rateKeys = new String[] {
//				SettingsMapper.BITRATE_24000,
				SettingsMapper.BITRATE_48000,
				SettingsMapper.BITRATE_96000,
				SettingsMapper.BITRATE_128000,
				SettingsMapper.BITRATE_192000,
				SettingsMapper.BITRATE_256000,
		};
		bitrateSetting.setData(rates, rateKeys);
		bitrateSetting.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				presenter.setSettingRecordingBitrate(SettingsMapper.keyToBitrate(key));
			}
		});
		bitrateSetting.setTitle(R.string.bitrate);
		bitrateSetting.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showInfoDialog(SettingsActivity.this, R.string.info_bitrate);
			}
		});

		channelsSetting = findViewById(R.id.setting_channels);
		recChannels = getResources().getStringArray(R.array.channels);
		recChannelsKeys = new String[] {
				SettingsMapper.CHANNEL_COUNT_STEREO,
				SettingsMapper.CHANNEL_COUNT_MONO
		};
		channelsSetting.setData(recChannels, recChannelsKeys);
		channelsSetting.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				presenter.setSettingChannelCount(SettingsMapper.keyToChannelCount(key));
			}
		});
		channelsSetting.setTitle(R.string.channels);
		channelsSetting.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showInfoDialog(SettingsActivity.this, R.string.info_channels);
			}
		});

		presenter = ARApplication.getInjector().provideSettingsPresenter();

		LinearLayout pnlInfo = findViewById(R.id.info_panel);
		pnlInfo.setBackground(
				RippleUtils.createShape(
						ContextCompat.getColor(getApplicationContext(),R.color.white_transparent_88),
						getResources().getDimension(R.dimen.spacing_normal)
				)
		);

		btnReset.setBackground(
				RippleUtils.createShape(
						ContextCompat.getColor(getApplicationContext(),colorMap.getPrimaryColorRes()),
						getResources().getDimension(R.dimen.spacing_normal)
				)
		);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			btnReset.setForeground(
					RippleUtils.createRippleMaskShape(
							ContextCompat.getColor(getApplicationContext(), R.color.white_transparent_80),
							getResources().getDimension(R.dimen.spacing_normal)
					)
			);
		}

		initThemeColorSelector();
		initNameFormatSelector();
	}

	private void initThemeColorSelector() {
		themeColor = findViewById(R.id.themeColor);
		List<AppSpinnerAdapter.ThemeItem> items = new ArrayList<>();
		String[] values = getResources().getStringArray(R.array.theme_colors2);
		int[] colorRes = colorMap.getColorResources();
		for (int i = 0; i < values.length; i++) {
			items.add(new AppSpinnerAdapter.ThemeItem(values[i], getApplicationContext().getResources().getColor(colorRes[i])));
		}
		AppSpinnerAdapter adapter = new AppSpinnerAdapter(SettingsActivity.this,
				R.layout.list_item_spinner, R.id.txtItem, items, R.drawable.ic_color_lens);
		themeColor.setAdapter(adapter);

		onThemeColorChangeListener = new ColorMap.OnThemeColorChangeListener() {
			@Override
			public void onThemeColorChange(String colorKey) {
				setTheme(colorMap.getAppThemeResource());
				recreate();
			}
		};
		colorMap.addOnThemeColorChangeListener(onThemeColorChangeListener);

		int selected = SettingsMapper.colorKeyToPosition(colorMap.getSelected());
		if (selected != themeColor.getSelectedItemPosition()) {
			themeColor.setSelection(selected);
		}
		themeColor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				String colorKey = SettingsMapper.positionToColorKey(position);
				colorMap.updateColorMap(colorKey);
				presenter.setSettingThemeColor(colorKey);
			}
			@Override public void onNothingSelected(AdapterView<?> parent) { }
		});
	}

	private void initNameFormatSelector() {
		nameFormatSelector = findViewById(R.id.name_format);
		List<AppSpinnerAdapter.ThemeItem> items = new ArrayList<>();
		String[] values = new String[3];
		values[0] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameCounted(1) + ".m4a";
//		values[1] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameDate() + ".m4a";
		values[1] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameDateVariant() + ".m4a";
		values[2] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameMills() + ".m4a";
		for (int i = 0; i < values.length; i++) {
			items.add(new AppSpinnerAdapter.ThemeItem(values[i],
					getApplicationContext().getResources().getColor(colorMap.getPrimaryColorRes())));
		}
		AppSpinnerAdapter adapter = new AppSpinnerAdapter(SettingsActivity.this,
				R.layout.list_item_spinner, R.id.txtItem, items, R.drawable.ic_title);
		nameFormatSelector.setAdapter(adapter);

		nameFormatSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				presenter.setSettingNamingFormat(SettingsMapper.positionToNamingFormat(position));
			}
			@Override public void onNothingSelected(AdapterView<?> parent) { }
		});
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
	protected void onDestroy() {
		super.onDestroy();
		colorMap.removeOnThemeColorChangeListener(onThemeColorChangeListener);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.btnBack:
				ARApplication.getInjector().releaseSettingsPresenter();
				finish();
				break;
			case R.id.btnTrash:
				startActivity(TrashActivity.getStartIntent(getApplicationContext()));
				break;
			case R.id.txt_records_location:
				presenter.onRecordsLocationClick();
				break;
			case R.id.btn_file_browser:
				startActivity(FileBrowserActivity.getStartIntent(getApplicationContext()));
				break;
			case R.id.btnRate:
				rateApp();
				break;
			case R.id.btnReset:
				presenter.resetSettings();
				presenter.loadSettings();
				break;
//			case R.id.btnDeleteAll:
//				AlertDialog.Builder builder = new AlertDialog.Builder(this);
//				builder.setTitle(R.string.warning)
//						.setIcon(R.drawable.ic_delete_forever)
//						.setMessage(R.string.delete_all_records)
//						.setCancelable(false)
//						.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
//							public void onClick(DialogInterface dialog, int id) {
//								presenter.deleteAllRecords();
//								dialog.dismiss();
//							}
//						})
//						.setNegativeButton(R.string.btn_no,
//								new DialogInterface.OnClickListener() {
//									public void onClick(DialogInterface dialog, int id) {
//										dialog.dismiss();
//									}
//								});
//				AlertDialog alert = builder.create();
//				alert.show();
//				break;
			case R.id.btnRequest:
				requestFeature();
				break;
			case R.id.btnPatreon:
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.patreon.com/Dimowner"));
				int flags = Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
				if (Build.VERSION.SDK_INT >= 21) {
					flags |= Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
				} else {
					flags |= Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET;
				}
				intent.addFlags(flags);
				startActivity(intent);
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

	private void requestFeature() {
		Intent i = new Intent(Intent.ACTION_SEND);
		i.setType("message/rfc822");
		i.putExtra(Intent.EXTRA_EMAIL, new String[]{AppConstants.REQUESTS_RECEIVER});
		i.putExtra(Intent.EXTRA_SUBJECT,
				"[" + getResources().getString(R.string.app_name)
						+ "] " + AndroidUtils.getAppVersion(getApplicationContext())
						+ " - " + getResources().getString(R.string.request)
		);
		try {
			Intent chooser = Intent.createChooser(i, getResources().getString(R.string.send_email));
			chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(chooser);
		} catch (android.content.ActivityNotFoundException ex) {
			showError(R.string.email_clients_not_found);
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
		// Build the about body view and append the link to see OSS licenses
		SpannableStringBuilder aboutBody = new SpannableStringBuilder();
		aboutBody.append(Html.fromHtml(getString(R.string.about_body, AndroidUtils.getAppVersion(getApplicationContext()))));
		return aboutBody;
	}

	@Override
	public void showStoreInPublicDir(boolean b) {
		swPublicDir.setOnCheckedChangeListener(null);
		swPublicDir.setChecked(b);
		swPublicDir.setOnCheckedChangeListener(publicDirListener);
	}

	@Override
	public void showKeepScreenOn(boolean b) {
		swKeepScreenOn.setChecked(b);
	}

	@Override
	public void showChannelCount(int count) {
		channelsSetting.setSelected(SettingsMapper.channelCountToKey(count));
	}

	@Override
	public void showAskToRenameAfterRecordingStop(boolean b) {
		swAskToRename.setChecked(b);
	}

	@Override
	public void showRecordingBitrate(int bitrate) {
		bitrateSetting.setSelected(SettingsMapper.bitrateToKey(bitrate));
	}

	@Override
	public void showRecordingSampleRate(int rate) {
		sampleRateSetting.setSelected(SettingsMapper.sampleRateToKey(rate));
	}

	@Override
	public void showRecordingFormat(String formatKey) {
		formatSetting.setSelected(formatKey);
	}

	@Override
	public void showNamingFormat(String namingKey) {
		nameFormatSelector.setSelection(SettingsMapper.namingFormatToPosition(namingKey));
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
	public void showBitrateSelector() {
		bitrateSetting.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideBitrateSelector() {
		bitrateSetting.setVisibility(View.GONE);
	}

	@Override
	public void showDialogPublicDirInfo() {
		AndroidUtils.showInfoDialog(this, R.string.public_dir_warning);
	}

	@Override
	public void showDialogPrivateDirInfo() {
		AndroidUtils.showInfoDialog(this, R.string.private_dir_warning);
	}

	@Override
	public void updateRecordingInfo(String format) {
		String[] sampleRates = new String[] {
				sampleRatesKeys[2],
				sampleRatesKeys[3],
				sampleRatesKeys[4],
				sampleRatesKeys[5]
		};
		if (format.equals(AppConstants.FORMAT_3GP)) {
			sampleRateSetting.removeChip(sampleRates);
			if (sampleRateSetting.getSelected() == null) {
				sampleRateSetting.setSelected(sampleRatesKeys[1]);
				presenter.setSettingSampleRate(SettingsMapper.keyToSampleRate(sampleRatesKeys[1]));
			}
		} else {
			String[] values = new String[] {
					this.sampleRates[2],
					this.sampleRates[3],
					this.sampleRates[4],
					this.sampleRates[5]
			};
			sampleRateSetting.addChip(sampleRates, values);
		}

		if (format.equals(AppConstants.FORMAT_3GP)) {
			channelsSetting.removeChip(new String[] {SettingsMapper.CHANNEL_COUNT_STEREO});
			channelsSetting.setSelected(SettingsMapper.CHANNEL_COUNT_MONO);
		} else {
			channelsSetting.addChip(new String[] {SettingsMapper.CHANNEL_COUNT_STEREO}, new String[] {getString(R.string.stereo)});
		}
	}

	@Override
	public void showSizePerMin(String size) {
		txtSizePerMin.setText(getString(R.string.size_per_min, size));
	}

	@Override
	public void showInformation(String info) {
		txtInformation.setText(info);
	}

	@Override
	public void showRecordsLocation(String location) {
		txtLocation.setVisibility(View.VISIBLE);
		txtLocation.setText(getString(R.string.records_location, location));
	}

	@Override
	public void hideRecordsLocation() {
		txtLocation.setText("");
		txtLocation.setVisibility(View.GONE);
	}

	@Override
	public void openRecordsLocation(File file) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		Uri fileUri = FileProvider.getUriForFile(
				getApplicationContext(),
				getApplicationContext().getPackageName() + ".app_file_provider",
				file
		);
		intent.setDataAndType(fileUri, DocumentsContract.Document.MIME_TYPE_DIR);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		try {
			startActivity(intent);
		} catch (ActivityNotFoundException e) {
			Timber.e(e);
		}
	}

	@Override
	public void enableAudioSettings() {
		btnReset.setEnabled(true);
		formatSetting.setEnabled(true);
		sampleRateSetting.setEnabled(true);
		bitrateSetting.setEnabled(true);
		channelsSetting.setEnabled(true);
	}

	@Override
	public void disableAudioSettings() {
		btnReset.setEnabled(false);
		formatSetting.setEnabled(false);
		sampleRateSetting.setEnabled(false);
		bitrateSetting.setEnabled(false);
		channelsSetting.setEnabled(false);
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

	@Override
	public void showMessage(int resId) {
		Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
	}
}

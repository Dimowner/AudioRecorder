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

package com.dimowner.audiorecorder.app.setup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.main.MainActivity;
import com.dimowner.audiorecorder.app.settings.AppSpinnerAdapter;
import com.dimowner.audiorecorder.app.settings.SettingsMapper;
import com.dimowner.audiorecorder.app.widget.ChipsView;
import com.dimowner.audiorecorder.app.widget.SettingView;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.FileUtil;

import java.util.ArrayList;
import java.util.List;

public class SetupActivity extends Activity implements SetupContract.View, View.OnClickListener {

	private Spinner nameFormatSelector;
	private Spinner themeColor;

	private SettingView formatSetting;
	private SettingView sampleRateSetting;
	private SettingView bitrateSetting;
	private SettingView channelsSetting;
	private TextView txtInformation;
	private TextView txtSizePerMin;

	private SetupContract.UserActionsListener presenter;
	private ColorMap colorMap;
	private ColorMap.OnThemeColorChangeListener onThemeColorChangeListener;

	public static Intent getStartIntent(Context context) {
		Intent intent = new Intent(context, SetupActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		return intent;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		colorMap = ARApplication.getInjector().provideColorMap();
		setTheme(colorMap.getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_setup);

//		getWindow().setFlags(
//				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
//				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
//
//		LinearLayout toolbar = findViewById(R.id.toolbar);
//		toolbar.setPadding(0, AndroidUtils.getStatusBarHeight(getApplicationContext()), 0, 0);

		txtInformation = findViewById(R.id.txt_information);
		txtSizePerMin = findViewById(R.id.txt_size_per_min);

		Button btnApply = findViewById(R.id.btn_apply);
		Button btnReset = findViewById(R.id.btn_reset);
		btnApply.setOnClickListener(this);
		btnReset.setOnClickListener(this);

//		Space space = findViewById(R.id.space);
//		ViewGroup.LayoutParams params = space.getLayoutParams();
//		params.height = AndroidUtils.getNavigationBarHeight(getApplicationContext());
//		space.setLayoutParams(params);

		formatSetting = findViewById(R.id.setting_recording_format);
		final String[] formats = getResources().getStringArray(R.array.formats2);
		final String[] formatsKeys = new String[] {
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
				AndroidUtils.showInfoDialog(SetupActivity.this, R.string.info_format);
			}
		});

		sampleRateSetting = findViewById(R.id.setting_frequency);
		final String[] sampleRates = getResources().getStringArray(R.array.sample_rates2);
		final String[] sampleRatesKeys = new String[] {
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
				AndroidUtils.showInfoDialog(SetupActivity.this, R.string.info_frequency);
			}
		});

		bitrateSetting = findViewById(R.id.setting_bitrate);
		final String[] rates = getResources().getStringArray(R.array.bit_rates2);
		final String[] rateKeys = new String[] {
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
				AndroidUtils.showInfoDialog(SetupActivity.this, R.string.info_bitrate);
			}
		});

		channelsSetting = findViewById(R.id.setting_channels);
		final String[] recChannels = getResources().getStringArray(R.array.channels);
		final String[] recChannelsKeys = new String[] {
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
				AndroidUtils.showInfoDialog(SetupActivity.this, R.string.info_channels);
			}
		});

		presenter = ARApplication.getInjector().provideSetupPresenter();

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
		AppSpinnerAdapter adapter = new AppSpinnerAdapter(SetupActivity.this,
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
		AppSpinnerAdapter adapter = new AppSpinnerAdapter(SetupActivity.this,
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
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.btn_apply:
				presenter.executeFirstRun();
				startActivity(MainActivity.getStartIntent(getApplicationContext()));
				finish();
				break;
			case R.id.btn_reset:
				presenter.resetSettings();
				presenter.loadSettings();
				break;
		}
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
	public void onBackPressed() {
		super.onBackPressed();
		ARApplication.getInjector().releaseSetupPresenter();
	}

	@Override
	public void showRecordingBitrate(int bitrate) {
		bitrateSetting.setSelected(SettingsMapper.bitrateToKey(bitrate));
	}

	@Override
	public void showSampleRate(int rate) {
		sampleRateSetting.setSelected(SettingsMapper.sampleRateToKey(rate));
	}

	@Override
	public void showChannelCount(int count) {
		channelsSetting.setSelected(SettingsMapper.channelCountToKey(count));
	}

	@Override
	public void showNamingFormat(String namingKey) {
		nameFormatSelector.setSelection(SettingsMapper.namingFormatToPosition(namingKey));
	}

	@Override
	public void showRecordingFormat(String formatKey) {
		formatSetting.setSelected(formatKey);
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
	public void showInformation(int infoResId) {
		txtInformation.setText(infoResId);
	}

	@Override
	public void showSizePerMin(String size) {
		txtSizePerMin.setText(getString(R.string.size_per_min, size));
	}

	@Override
	public void updateRecordingInfo(String format) {
		String[] sampleRateKeys = new String[] {
				SettingsMapper.SAMPLE_RATE_22050,
				SettingsMapper.SAMPLE_RATE_32000,
				SettingsMapper.SAMPLE_RATE_44100,
				SettingsMapper.SAMPLE_RATE_48000
		};
		if (format.equals(AppConstants.FORMAT_3GP)) {
			sampleRateSetting.removeChip(sampleRateKeys);
			if (sampleRateSetting.getSelected() == null) {
				sampleRateSetting.setSelected(SettingsMapper.SAMPLE_RATE_16000);
			}
		} else {
			String[] sampleRates = getResources().getStringArray(R.array.sample_rates2);
			String[] values = new String[] {
					sampleRates[2],
					sampleRates[3],
					sampleRates[4],
					sampleRates[5]
			};
			sampleRateSetting.addChip(sampleRateKeys, values);
		}

		if (format.equals(AppConstants.FORMAT_3GP)) {
			channelsSetting.removeChip(new String[] {SettingsMapper.CHANNEL_COUNT_STEREO});
			channelsSetting.setSelected(SettingsMapper.CHANNEL_COUNT_MONO);
		} else {
			channelsSetting.addChip(new String[] {SettingsMapper.CHANNEL_COUNT_STEREO}, new String[] {getString(R.string.stereo)});
		}
	}

	@Override
	public void showProgress() {
	}

	@Override
	public void hideProgress() {
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

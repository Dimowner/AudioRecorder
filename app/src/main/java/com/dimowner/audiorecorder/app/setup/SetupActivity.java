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
import com.dimowner.audiorecorder.app.widget.ChipsView;
import com.dimowner.audiorecorder.app.widget.SettingView;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.FileUtil;

import java.util.ArrayList;
import java.util.List;

public class SetupActivity extends Activity implements SetupContract.View, View.OnClickListener {

	private final static String SAMPLE_RATE_8000 = "8000";
	private final static String SAMPLE_RATE_16000 = "16000";
	private final static String SAMPLE_RATE_32000 = "32000";
	private final static String SAMPLE_RATE_44100 = "44100";
	private final static String SAMPLE_RATE_48000 = "48000";

	private final static String BITRATE_24000 = "24000";
	private final static String BITRATE_48000 = "48000";
	private final static String BITRATE_96000 = "96000";
	private final static String BITRATE_128000 = "128000";
	private final static String BITRATE_192000 = "192000";

	private final static String CHANNEL_COUNT_STEREO = "stereo";
	private final static String CHANNEL_COUNT_MONO = "mono";

	private Spinner nameFormatSelector;
	private Spinner themeColor;

	private SettingView formatSetting;
	private SettingView sampleRateSetting;
	private SettingView bitrateSetting;
	private SettingView channelsSetting;
	private TextView txtInformation;

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

		getWindow().setFlags(
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
				WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

		LinearLayout toolbar = findViewById(R.id.toolbar);
		toolbar.setPadding(0, AndroidUtils.getStatusBarHeight(getApplicationContext()), 0, 0);

		txtInformation = findViewById(R.id.txt_information);

		Button btnApply = findViewById(R.id.btn_apply);
		Button btnReset = findViewById(R.id.btn_reset);
		btnApply.setOnClickListener(this);
		btnReset.setOnClickListener(this);

		Space space = findViewById(R.id.space);
		ViewGroup.LayoutParams params = space.getLayoutParams();
		params.height = AndroidUtils.getNavigationBarHeight(getApplicationContext());
		space.setLayoutParams(params);

		Space space2 = findViewById(R.id.space2);
		ViewGroup.LayoutParams params2 = space.getLayoutParams();
		params2.height = AndroidUtils.getNavigationBarHeight(getApplicationContext());
		space2.setLayoutParams(params2);

		findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});

		findViewById(R.id.btn_apply).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				presenter.executeFirstRun();
				startActivity(MainActivity.getStartIntent(getApplicationContext()));
				finish();
			}
		});

		formatSetting = findViewById(R.id.setting_recording_format);
		final String[] formats = getResources().getStringArray(R.array.formats2);
		final String[] formatsKeys = new String[] {
			AppConstants.FORMAT_M4A,
			AppConstants.FORMAT_WAV
		};
		formatSetting.setData(formats, formatsKeys);
		formatSetting.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				presenter.setSettingRecordingFormat(key);
			}
		});
		formatSetting.setTitle(R.string.recording_format);
//		formatSetting.setImageInfo(R.drawable.ic_audiotrack);
		formatSetting.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showInfoDialog(SetupActivity.this, R.string.info_format);
			}
		});

		sampleRateSetting = findViewById(R.id.setting_frequency);
		final String[] sampleRates = getResources().getStringArray(R.array.sample_rates2);
		final String[] sampleRatesKeys = new String[] {
				SAMPLE_RATE_8000,
				SAMPLE_RATE_16000,
				SAMPLE_RATE_32000,
				SAMPLE_RATE_44100,
				SAMPLE_RATE_48000,
		};
		sampleRateSetting.setData(sampleRates, sampleRatesKeys);
		sampleRateSetting.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				presenter.setSettingSampleRate(keyToSampleRate(key));
			}
		});
		sampleRateSetting.setTitle(R.string.frequency);
//		sampleRateSetting.setImageInfo(R.drawable.ic_audiotrack);
		sampleRateSetting.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showInfoDialog(SetupActivity.this, R.string.info_frequency);
			}
		});

		bitrateSetting = findViewById(R.id.setting_bitrate);
		final String[] rates = getResources().getStringArray(R.array.bit_rates2);
		final String[] rateKeys = new String[] {
				BITRATE_24000,
				BITRATE_48000,
				BITRATE_96000,
				BITRATE_128000,
				BITRATE_192000,
		};
		bitrateSetting.setData(rates, rateKeys);
		bitrateSetting.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				presenter.setSettingRecordingBitrate(keyToBitrate(key));
			}
		});
		bitrateSetting.setTitle(R.string.bitrate);
//		bitrateSetting.setImageInfo(R.drawable.ic_audiotrack);
		bitrateSetting.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showInfoDialog(SetupActivity.this, R.string.info_bitrate);
			}
		});

		channelsSetting = findViewById(R.id.setting_channels);
		final String[] recChannels = getResources().getStringArray(R.array.channels);
		final String[] recChannelsKeys = new String[] {
				CHANNEL_COUNT_STEREO,
				CHANNEL_COUNT_MONO
		};
		channelsSetting.setData(recChannels, recChannelsKeys);
		channelsSetting.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				presenter.setSettingChannelCount(keyToChannelCount(key));
			}
		});
		channelsSetting.setTitle(R.string.channels);
//		channelsSetting.setImageInfo(R.drawable.ic_surround_sound_2_0);
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

		int selected = colorKeyToPosition(colorMap.getSelected());
		if (selected != themeColor.getSelectedItemPosition()) {
			themeColor.setSelection(selected);
		}
		themeColor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				String colorKey = positionToColorKey(position);
				colorMap.updateColorMap(colorKey);
				presenter.setSettingThemeColor(colorKey);
			}
			@Override public void onNothingSelected(AdapterView<?> parent) { }
		});
	}

	private void initNameFormatSelector() {
		nameFormatSelector = findViewById(R.id.name_format);
		List<AppSpinnerAdapter.ThemeItem> items = new ArrayList<>();
		String[] values = new String[2];
		values[0] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameCounted(1) + ".m4a";
		values[1] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameDate() + ".m4a";
//		values[2] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameDateVariant() + ".m4a";
//		values[3] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameMills() + ".m4a";
		for (int i = 0; i < values.length; i++) {
			items.add(new AppSpinnerAdapter.ThemeItem(values[i],
					getApplicationContext().getResources().getColor(colorMap.getPrimaryColorRes())));
		}
		AppSpinnerAdapter adapter = new AppSpinnerAdapter(SetupActivity.this,
				R.layout.list_item_spinner, R.id.txtItem, items, R.drawable.ic_title);
		nameFormatSelector.setAdapter(adapter);

		nameFormatSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				presenter.setSettingNamingFormat(positionToNamingFormat(position));
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
			case R.id.btn_back:
				finish();
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
		bitrateSetting.setSelected(bitrateToKey(bitrate));
	}

	@Override
	public void showSampleRate(int rate) {
		sampleRateSetting.setSelected(sampleRateToKey(rate));
	}

	@Override
	public void showChannelCount(int count) {
		channelsSetting.setSelected(channelCountToKey(count));
	}

	@Override
	public void showNamingFormat(String namingKey) {
		nameFormatSelector.setSelection(namingFormatToPosition(namingKey));
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

	private String positionToColorKey(int position) {
		switch (position) {
			case 0:
				return AppConstants.THEME_BLUE_GREY;
			case 1:
				return AppConstants.THEME_BLACK;
			case 2:
				return AppConstants.THEME_TEAL;
			case 3:
				return AppConstants.THEME_BLUE;
			case 4:
				return AppConstants.THEME_PURPLE;
			case 5:
				return AppConstants.THEME_PINK;
			case 6:
				return AppConstants.THEME_ORANGE;
			case 7:
				return AppConstants.THEME_RED;
			case 8:
				return AppConstants.THEME_BROWN;
			default:
				return AppConstants.DEFAULT_THEME_COLOR;
		}
	}

	private int colorKeyToPosition(String colorKey) {
		switch (colorKey) {
			default:
			case AppConstants.THEME_BLUE_GREY:
				return 0;
			case AppConstants.THEME_BLACK:
				return 1;
			case AppConstants.THEME_TEAL:
				return 2;
			case AppConstants.THEME_BLUE:
				return 3;
			case AppConstants.THEME_PURPLE:
				return 4;
			case AppConstants.THEME_PINK:
				return 5;
			case AppConstants.THEME_ORANGE:
				return 6;
			case AppConstants.THEME_RED:
				return 7;
			case AppConstants.THEME_BROWN:
				return 8;
		}
	}

	private int namingFormatToPosition(String namingFormat) {
		switch (namingFormat) {
			case AppConstants.NAME_FORMAT_DATE:
				return 1;
			case AppConstants.NAME_FORMAT_RECORD:
			default:
				return 0;
		}
	}

	private String positionToNamingFormat(int position) {
		switch (position) {
			case 1:
				return AppConstants.NAME_FORMAT_DATE;
			case 0:
				return AppConstants.NAME_FORMAT_RECORD;
			default:
				return AppConstants.DEFAULT_NAME_FORMAT;
		}
	}

	private int keyToSampleRate(String sampleRateKey) {
		switch (sampleRateKey) {
			case SAMPLE_RATE_8000:
				return AppConstants.RECORD_SAMPLE_RATE_8000;
			case SAMPLE_RATE_16000:
				return AppConstants.RECORD_SAMPLE_RATE_16000;
			case SAMPLE_RATE_32000:
				return AppConstants.RECORD_SAMPLE_RATE_32000;
			case SAMPLE_RATE_44100:
				return AppConstants.RECORD_SAMPLE_RATE_44100;
			case SAMPLE_RATE_48000:
				return AppConstants.RECORD_SAMPLE_RATE_48000;
			default:
				return AppConstants.DEFAULT_RECORD_SAMPLE_RATE;
		}
	}

	private String sampleRateToKey(int sampleRate) {
		switch (sampleRate) {
			case AppConstants.RECORD_SAMPLE_RATE_8000:
				return SAMPLE_RATE_8000;
			case AppConstants.RECORD_SAMPLE_RATE_16000:
				return SAMPLE_RATE_16000;
			case AppConstants.RECORD_SAMPLE_RATE_32000:
				return SAMPLE_RATE_32000;
			case AppConstants.RECORD_SAMPLE_RATE_44100:
			default:
				return SAMPLE_RATE_44100;
			case AppConstants.RECORD_SAMPLE_RATE_48000:
				return SAMPLE_RATE_48000;
		}
	}

	private int keyToBitrate(String bitrateKey) {
		switch (bitrateKey) {
			case BITRATE_24000:
				return AppConstants.RECORD_ENCODING_BITRATE_24000;
			case BITRATE_48000:
				return AppConstants.RECORD_ENCODING_BITRATE_48000;
			case BITRATE_96000:
				return AppConstants.RECORD_ENCODING_BITRATE_96000;
			case BITRATE_128000:
				return AppConstants.RECORD_ENCODING_BITRATE_128000;
			case BITRATE_192000:
				return AppConstants.RECORD_ENCODING_BITRATE_192000;
			default:
				return AppConstants.DEFAULT_RECORD_ENCODING_BITRATE;
		}
	}

	private String bitrateToKey(int bitrate) {
		switch (bitrate) {
			case AppConstants.RECORD_ENCODING_BITRATE_24000:
				return BITRATE_24000;
			case AppConstants.RECORD_ENCODING_BITRATE_48000:
				return BITRATE_48000;
			case AppConstants.RECORD_ENCODING_BITRATE_96000:
				return BITRATE_96000;
			case AppConstants.RECORD_ENCODING_BITRATE_128000:
			default:
				return BITRATE_128000;
			case AppConstants.RECORD_ENCODING_BITRATE_192000:
				return BITRATE_192000;
		}
	}

	private int keyToChannelCount(String key) {
		switch (key) {
			case CHANNEL_COUNT_MONO:
				return AppConstants.RECORD_AUDIO_MONO;
			case CHANNEL_COUNT_STEREO:
				return AppConstants.RECORD_AUDIO_STEREO;
			default:
				return AppConstants.DEFAULT_CHANNEL_COUNT;
		}
	}

	private String channelCountToKey(int count) {
		switch (count) {
			case AppConstants.RECORD_AUDIO_MONO:
				return CHANNEL_COUNT_MONO;
			case AppConstants.RECORD_AUDIO_STEREO:
			default:
				return CHANNEL_COUNT_STEREO;
		}
	}
}

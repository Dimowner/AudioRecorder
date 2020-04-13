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
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.Switch;
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

import androidx.core.content.ContextCompat;
import timber.log.Timber;

public class SetupActivity extends Activity implements SetupContract.View {

	private Switch swPublicDir;
	private Switch swRecordInStereo;

	private Spinner nameFormatSelector;
	private Spinner formatSelector;
	private Spinner sampleRateSelector;
	private Spinner bitrateSelector;

	private SetupContract.UserActionsListener presenter;
	private ColorMap colorMap;
	private ColorMap.OnThemeColorChangeListener onThemeColorChangeListener;
	private CompoundButton.OnCheckedChangeListener publicDirListener = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton btn, boolean isChecked) {
			presenter.storeInPublicDir(isChecked);
			if (isChecked) {
				showDialogPublicDirInfo();
			} else {
				showDialogPrivateDirInfo();
			}
		}
	};

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

		swPublicDir = findViewById(R.id.swPublicDir);
		swRecordInStereo = findViewById(R.id.swRecordInStereo);

		swPublicDir.setOnCheckedChangeListener(publicDirListener);
		swRecordInStereo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton btn, boolean isChecked) {
				presenter.recordInStereo(isChecked);
			}
		});

		findViewById(R.id.btn_get_started).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				presenter.executeFirstRun();
				startActivity(MainActivity.getStartIntent(getApplicationContext()));
				finish();
			}
		});

		final SettingView settingTheme = findViewById(R.id.setting_item_theme);
		String[] names = new String[9];
		names[0] = "Black";
		names[1] = "Teal";
		names[2] = "Blue";
		names[3] = "Purple";
		names[4] = "Pink";
		names[5] = "Orange";
		names[6] = "Red";
		names[7] = "Brown";
		names[8] = "Blue gray";
		int[] colors = new int[9];
		colors[0] = ContextCompat.getColor(getApplicationContext(), R.color.md_black_1000);
		colors[1] = ContextCompat.getColor(getApplicationContext(), R.color.md_teal_700);
		colors[2] = ContextCompat.getColor(getApplicationContext(), R.color.md_blue_700);
		colors[3] = ContextCompat.getColor(getApplicationContext(), R.color.md_deep_purple_700);
		colors[4] = ContextCompat.getColor(getApplicationContext(), R.color.md_pink_800);
		colors[5] = ContextCompat.getColor(getApplicationContext(), R.color.md_deep_orange_800);
		colors[6] = ContextCompat.getColor(getApplicationContext(), R.color.md_red_700);
		colors[7] = ContextCompat.getColor(getApplicationContext(), R.color.md_brown_700);
		colors[8] = ContextCompat.getColor(getApplicationContext(), R.color.md_blue_gray_700);
		settingTheme.setData(names, names, colors);
//		settingView.setData(names);
		settingTheme.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				Timber.v("onCheck key = " + key + " name = " + name + " checked = " + checked);
			}
		});
		settingTheme.setSelected("Brown");
		settingTheme.setTitle("Theme color");
		settingTheme.setImageInfo(R.drawable.ic_color_lens);
		settingTheme.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showSimpleDialog(
						SetupActivity.this,
						R.drawable.ic_info,
						R.string.info,
						R.string.app_name,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Timber.v("onClick");
							}
						});
			}
		});

		final SettingView settingStorage = findViewById(R.id.setting_storage);
		String[] storage = new String[2];
		storage[0] = "Public";
		storage[1] = "Private";
		settingStorage.setData(storage, storage);
		settingStorage.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				Timber.v("onCheck key = " + key + " name = " + name + " checked = " + checked);
			}
		});
		settingStorage.setSelected("Private");
		settingStorage.setTitle("Records storage");
		settingStorage.setImageInfo(R.drawable.ic_folder_open);
		settingStorage.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showSimpleDialog(
						SetupActivity.this,
						R.drawable.ic_info,
						R.string.info,
						R.string.app_name,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Timber.v("onClick");
							}
						});
			}
		});

		final SettingView settingNameFormat = findViewById(R.id.setting_name_format);
		String[] nameFormat = new String[3];
		nameFormat[0] = "Record-1.m4a";
		nameFormat[1] = "127371273717.m4a";
		nameFormat[2] = "13.04.2020 20.52.05.m4a";
		settingNameFormat.setData(nameFormat, nameFormat);
		settingNameFormat.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				Timber.v("onCheck key = " + key + " name = " + name + " checked = " + checked);
			}
		});
		settingNameFormat.setSelected("Record-1.m4a");
		settingNameFormat.setTitle("Name format");
		settingNameFormat.setImageInfo(R.drawable.ic_audiotrack);
		settingNameFormat.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showSimpleDialog(
						SetupActivity.this,
						R.drawable.ic_info,
						R.string.info,
						R.string.app_name,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Timber.v("onClick");
							}
						});
			}
		});

		final SettingView settingRecFormat = findViewById(R.id.setting_recording_format);
		String[] recFormat = new String[3];
		recFormat[0] = "M4a";
		recFormat[1] = "Wav";
		recFormat[2] = "3gp";
		settingRecFormat.setData(recFormat, recFormat);
		settingRecFormat.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				Timber.v("onCheck key = " + key + " name = " + name + " checked = " + checked);
			}
		});
		settingRecFormat.setSelected("Wav");
		settingRecFormat.setTitle("Recording format");
		settingRecFormat.setTitle("Recording format");
		settingRecFormat.setImageInfo(R.drawable.ic_audiotrack);
		settingRecFormat.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showSimpleDialog(
						SetupActivity.this,
						R.drawable.ic_info,
						R.string.info,
						R.string.app_name,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Timber.v("onClick");
							}
						});
			}
		});

		final SettingView settingFrequency = findViewById(R.id.setting_frequency);
		String[] recFrequency = new String[5];
		recFrequency[0] = "8000";
		recFrequency[1] = "16000";
		recFrequency[2] = "32000";
		recFrequency[3] = "44100";
		recFrequency[4] = "48000";
		settingFrequency.setData(recFrequency, recFrequency);
		settingFrequency.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				Timber.v("onCheck key = " + key + " name = " + name + " checked = " + checked);
			}
		});
		settingFrequency.setSelected("44100");
		settingFrequency.setTitle("Frequency");
		settingFrequency.setImageInfo(R.drawable.ic_audiotrack);
		settingFrequency.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showSimpleDialog(
						SetupActivity.this,
						R.drawable.ic_info,
						R.string.info,
						R.string.app_name,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Timber.v("onClick");
							}
						});
			}
		});

		final SettingView settingBitrate = findViewById(R.id.setting_bitrate);
		String[] recBitrate = new String[5];
		recBitrate[0] = "24 kb/s";
		recBitrate[1] = "48 kb/s";
		recBitrate[2] = "96 kb/s";
		recBitrate[3] = "128 kb/s";
		recBitrate[4] = "192 kb/s";
		settingBitrate.setData(recBitrate, recBitrate);
		settingBitrate.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				Timber.v("onCheck key = " + key + " name = " + name + " checked = " + checked);
			}
		});
		settingBitrate.setSelected("128 kb/s");
		settingBitrate.setTitle("Bitrate");
		settingBitrate.setImageInfo(R.drawable.ic_audiotrack);
		settingBitrate.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showSimpleDialog(
						SetupActivity.this,
						R.drawable.ic_info,
						R.string.info,
						R.string.app_name,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Timber.v("onClick");
							}
						});
			}
		});

		final SettingView settingChannels = findViewById(R.id.setting_channels);
		String[] recChannels = new String[2];
		recChannels[0] = "Mono";
		recChannels[1] = "Stereo";
		settingChannels.setData(recChannels, recChannels);
		settingChannels.setOnChipCheckListener(new ChipsView.OnCheckListener() {
			@Override
			public void onCheck(String key, String name, boolean checked) {
				Timber.v("onCheck key = " + key + " name = " + name + " checked = " + checked);
			}
		});
		settingChannels.setSelected("Stereo");
		settingChannels.setTitle("Channels");
		settingChannels.setImageInfo(R.drawable.ic_surround_sound_2_0);
		settingChannels.setOnInfoClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AndroidUtils.showSimpleDialog(
						SetupActivity.this,
						R.drawable.ic_info,
						R.string.info,
						R.string.app_name,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Timber.v("onClick");
							}
						});
			}
		});

		presenter = ARApplication.getInjector().provideSetupPresenter();

		initThemeColorSelector();
		initNameFormatSelector();
		initFormatSelector();
		initSampleRateSelector();
		initBitrateSelector();
	}

	private void initThemeColorSelector() {
		final Spinner themeColor = findViewById(R.id.themeColor);
		List<AppSpinnerAdapter.ThemeItem> items = new ArrayList<>();
		String values[] = getResources().getStringArray(R.array.theme_colors);
		int[] colorRes = colorMap.getColorResources();
		for (int i = 0; i < values.length; i++) {
			items.add(new AppSpinnerAdapter.ThemeItem(values[i], getApplicationContext().getResources().getColor(colorRes[i])));
		}
		AppSpinnerAdapter adapter = new AppSpinnerAdapter(SetupActivity.this,
				R.layout.list_item_spinner, R.id.txtItem, items, R.drawable.ic_color_lens);
		themeColor.setAdapter(adapter);

		onThemeColorChangeListener = new ColorMap.OnThemeColorChangeListener() {
			@Override
			public void onThemeColorChange(int pos) {
				setTheme(colorMap.getAppThemeResource());
				recreate();
			}
		};
		colorMap.addOnThemeColorChangeListener(onThemeColorChangeListener);

		if (colorMap.getSelected() > 0) {
			themeColor.setSelection(colorMap.getSelected());
		}
		themeColor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				colorMap.updateColorMap(position);
			}
			@Override public void onNothingSelected(AdapterView<?> parent) { }
		});
	}

	private void initNameFormatSelector() {
		nameFormatSelector = findViewById(R.id.name_format);
		List<AppSpinnerAdapter.ThemeItem> items = new ArrayList<>();
		String[] values = new String[4];
		values[0] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameCounted(1) + ".m4a";
		values[1] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameDate() + ".m4a";
		values[2] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameDateVariant() + ".m4a";
		values[3] = getResources().getString(R.string.naming) + " " + FileUtil.generateRecordNameMills() + ".m4a";
		for (int i = 0; i < values.length; i++) {
			items.add(new AppSpinnerAdapter.ThemeItem(values[i],
					getApplicationContext().getResources().getColor(colorMap.getPrimaryColorRes())));
		}
		AppSpinnerAdapter adapter = new AppSpinnerAdapter(SetupActivity.this,
				R.layout.list_item_spinner, R.id.txtItem, items, R.drawable.ic_title);
		nameFormatSelector.setAdapter(adapter);

		nameFormatSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (position == 0) {
					presenter.setNamingFormat(AppConstants.NAMING_COUNTED);
				} else {
					presenter.setNamingFormat(AppConstants.NAMING_DATE);
				}
			}
			@Override public void onNothingSelected(AdapterView<?> parent) { }
		});
	}

	private void initFormatSelector() {
		formatSelector = findViewById(R.id.format);
		List<AppSpinnerAdapter.ThemeItem> items = new ArrayList<>();
		String[] values = getResources().getStringArray(R.array.formats);
		for (int i = 0; i < values.length; i++) {
			items.add(new AppSpinnerAdapter.ThemeItem(values[i],
					getApplicationContext().getResources().getColor(colorMap.getPrimaryColorRes())));
		}
		AppSpinnerAdapter adapter = new AppSpinnerAdapter(SetupActivity.this,
				R.layout.list_item_spinner, R.id.txtItem, items, R.drawable.ic_audiotrack);
		formatSelector.setAdapter(adapter);

		formatSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				if (position == 0) {
					presenter.setRecordingFormat(AppConstants.RECORDING_FORMAT_M4A);
				} else {
					presenter.setRecordingFormat(AppConstants.RECORDING_FORMAT_WAV);
				}
			}
			@Override public void onNothingSelected(AdapterView<?> parent) { }
		});
		if (ARApplication.isRecording()) {
			formatSelector.setEnabled(false);
			formatSelector.setClickable(false);
		}
	}

	private void initSampleRateSelector() {
		sampleRateSelector = findViewById(R.id.sample_rate);
		List<AppSpinnerAdapter.ThemeItem> items = new ArrayList<>();
		String[] values = getResources().getStringArray(R.array.sample_rates);
		for (int i = 0; i < values.length; i++) {
			items.add(new AppSpinnerAdapter.ThemeItem(values[i],
					getApplicationContext().getResources().getColor(colorMap.getPrimaryColorRes())));
		}
		AppSpinnerAdapter adapter = new AppSpinnerAdapter(SetupActivity.this,
				R.layout.list_item_spinner, R.id.txtItem, items, R.drawable.ic_audiotrack);
		sampleRateSelector.setAdapter(adapter);

		sampleRateSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				presenter.setSampleRate(position);
			}
			@Override public void onNothingSelected(AdapterView<?> parent) { }
		});
	}

	private void initBitrateSelector() {
		bitrateSelector = findViewById(R.id.bit_rate);
		List<AppSpinnerAdapter.ThemeItem> items3 = new ArrayList<>();
		String[] values3 = getResources().getStringArray(R.array.bit_rates);
		for (int i = 0; i < values3.length; i++) {
			items3.add(new AppSpinnerAdapter.ThemeItem(values3[i],
					getApplicationContext().getResources().getColor(colorMap.getPrimaryColorRes())));
		}
		AppSpinnerAdapter adapter3 = new AppSpinnerAdapter(SetupActivity.this,
				R.layout.list_item_spinner, R.id.txtItem, items3, R.drawable.ic_audiotrack);
		bitrateSelector.setAdapter(adapter3);

		bitrateSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				presenter.setRecordingBitrate(position);
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
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
	public void onBackPressed() {
		super.onBackPressed();
		ARApplication.getInjector().releaseSetupPresenter();
	}

	@Override
	public void showStoreInPublicDir(boolean b) {
		swPublicDir.setOnCheckedChangeListener(null);
		swPublicDir.setChecked(b);
		swPublicDir.setOnCheckedChangeListener(publicDirListener);
	}

	@Override
	public void showRecordInStereo(boolean b) {
		swRecordInStereo.setChecked(b);
	}

	@Override
	public void showRecordingBitrate(int bitrate) {
		bitrateSelector.setSelection(bitrate);
	}

	@Override
	public void showRecordingSampleRate(int rate) {
		sampleRateSelector.setSelection(rate);
	}

	@Override
	public void showRecordingFormat(int format) {
		formatSelector.setSelection(format);
	}

	@Override
	public void showNamingFormat(int format) {
		nameFormatSelector.setSelection(format);
	}

	@Override
	public void showBitrateSelector() {
//		bitrateSelector.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideBitrateSelector() {
		bitrateSelector.setVisibility(View.GONE);
	}

	@Override
	public void showDialogPublicDirInfo() {
		AndroidUtils.showDialog(this, R.string.warning, R.string.public_dir_warning,
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
					}
				}, null
		);
	}

	@Override
	public void showDialogPrivateDirInfo() {
		AndroidUtils.showDialog(this, R.string.warning, R.string.private_dir_warning,
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
					}
				}, null
		);
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

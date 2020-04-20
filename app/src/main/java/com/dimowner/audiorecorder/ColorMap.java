package com.dimowner.audiorecorder;

import com.dimowner.audiorecorder.data.Prefs;

import java.util.ArrayList;
import java.util.List;

public class ColorMap {

	private static ColorMap singleton;

	public static ColorMap getInstance(Prefs prefs) {
		if (singleton == null) {
			singleton = new ColorMap(prefs);
		}
		return singleton;
	}

	private int appThemeResource = 0;
	private int primaryColorRes = R.color.md_blue_700;
	private int playbackPanelBackground = R.drawable.panel_amber;
	private String selectedKey;
	private List<OnThemeColorChangeListener> onThemeColorChangeListeners;

	private ColorMap(Prefs prefs) {
		onThemeColorChangeListeners = new ArrayList<>();
		selectedKey = prefs.getSettingThemeColor();
		init();
	}

	private void init() {
		switch (selectedKey) {
			case AppConstants.THEME_BLACK:
				appThemeResource = R.style.AppTheme_Black;
				primaryColorRes = R.color.md_black_1000;
				playbackPanelBackground = R.drawable.panel_red;
				break;
			case AppConstants.THEME_TEAL:
				appThemeResource = R.style.AppTheme_Teal;
				primaryColorRes = R.color.md_teal_700;
				playbackPanelBackground = R.drawable.panel_green;
				break;
			case AppConstants.THEME_PURPLE:
				appThemeResource = R.style.AppTheme_Purple;
				primaryColorRes = R.color.md_deep_purple_700;
				playbackPanelBackground = R.drawable.panel_pink;
				break;
			case AppConstants.THEME_PINK:
				appThemeResource = R.style.AppTheme_Pink;
				primaryColorRes = R.color.md_pink_800;
				playbackPanelBackground = R.drawable.panel_purple;
				break;
			case AppConstants.THEME_ORANGE:
				appThemeResource = R.style.AppTheme_DeepOrange;
				primaryColorRes = R.color.md_deep_orange_800;
				playbackPanelBackground = R.drawable.panel_yellow;
				break;
			case AppConstants.THEME_RED:
				appThemeResource = R.style.AppTheme_Red;
				primaryColorRes = R.color.md_red_700;
				playbackPanelBackground = R.drawable.panel_purple_light;
				break;
			case AppConstants.THEME_BROWN:
				appThemeResource = R.style.AppTheme_Brown;
				primaryColorRes = R.color.md_brown_700;
				playbackPanelBackground = R.drawable.panel_deep_orange;
				break;
			case AppConstants.THEME_BLUE:
				primaryColorRes = R.color.md_blue_700;
				appThemeResource = R.style.AppTheme_Blue;
				playbackPanelBackground = R.drawable.panel_amber;
				break;
			case AppConstants.THEME_BLUE_GREY:
			default:
				appThemeResource = R.style.AppTheme_Gray;
				primaryColorRes = R.color.md_blue_gray_700;
				playbackPanelBackground = R.drawable.panel_red;
		}
	}

	public void updateColorMap(String colorKey) {
		String oldSelected = selectedKey;
		selectedKey = colorKey;
		if (!oldSelected.equals(selectedKey)) {
			init();
			onThemeColorChange(selectedKey);
		}
	}

	public String getSelected() {
		return selectedKey;
	}

	public int getAppThemeResource() {
		return appThemeResource;
	}

	public int getPrimaryColorRes() {
		return primaryColorRes;
	}

	public int getPlaybackPanelBackground() {
		return playbackPanelBackground;
	}

	public int[] getColorResources() {
		return new int[] {
				R.color.md_blue_gray_700,
				R.color.md_black_1000,
				R.color.md_teal_700,
				R.color.md_blue_700,
				R.color.md_deep_purple_700,
				R.color.md_pink_800,
				R.color.md_deep_orange_800,
				R.color.md_red_700,
				R.color.md_brown_700
		};
	}

	public void addOnThemeColorChangeListener(OnThemeColorChangeListener onThemeColorChangeListener) {
		this.onThemeColorChangeListeners.add(onThemeColorChangeListener);
	}

	public void removeOnThemeColorChangeListener(OnThemeColorChangeListener onThemeColorChangeListener) {
		this.onThemeColorChangeListeners.remove(onThemeColorChangeListener);
	}

	public void onThemeColorChange(String colorKey) {
		for (int i = 0; i < onThemeColorChangeListeners.size(); i++) {
			onThemeColorChangeListeners.get(i).onThemeColorChange(colorKey);
		}
	}

	public interface OnThemeColorChangeListener {
		void onThemeColorChange(String colorKey);
	}
}

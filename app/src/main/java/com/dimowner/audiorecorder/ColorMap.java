package com.dimowner.audiorecorder;

import java.util.Random;

public class ColorMap {

	private static ColorMap singleton;

	public static ColorMap getInstance() {
		if (singleton == null) {
			singleton = new ColorMap();
		}
		return singleton;
	}

	private int appThemeResource = 0;
	private int primaryColorRes = R.color.md_blue_700;
	private int textColor = R.color.text_primary_light;
	private int playbackPanelBackground = R.drawable.panel_amber;

	private ColorMap() {
		init();
	}

	private void init() {
		switch (new Random().nextInt(7)) {
			case 1:
				appThemeResource = R.style.AppTheme_Brown;
				primaryColorRes = R.color.md_brown_700;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_deep_orange;
				break;
			case 2:
				appThemeResource = R.style.AppTheme_DeepOrange;
				primaryColorRes = R.color.md_deep_orange_800;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_yellow;
				break;
			case 3:
				appThemeResource = R.style.AppTheme_Pink;
				primaryColorRes = R.color.md_pink_800;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_purple;
				break;
			case 4:
				appThemeResource = R.style.AppTheme_Purple;
				primaryColorRes = R.color.md_deep_purple_700;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_pink;
				break;
			case 5:
				appThemeResource = R.style.AppTheme_Red;
				primaryColorRes = R.color.md_red_700;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_purple_light;
				break;
			case 6:
				appThemeResource = R.style.AppTheme_Teal;
				primaryColorRes = R.color.md_teal_700;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_green;
				break;
			case 0:
			default:
				primaryColorRes = R.color.md_blue_700;
				appThemeResource = R.style.AppTheme;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_amber;
		}
	}

	public int getAppThemeResource() {
		return appThemeResource;
	}

	public int getPrimaryColorRes() {
		return primaryColorRes;
	}

	public int getTextColor() {
		return textColor;
	}

	public int getPlaybackPanelBackground() {
		return playbackPanelBackground;
	}
}

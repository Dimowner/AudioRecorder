package com.dimowner.audiorecorder;

import com.dimowner.audiorecorder.data.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import timber.log.Timber;

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
	private int textColor = R.color.text_primary_light;
	private int playbackPanelBackground = R.drawable.panel_amber;
	private int selected = 0;
	private List<OnThemeColorChangeListener> onThemeColorChangeListeners;
	private Prefs prefs;

	private ColorMap(Prefs prefs) {

		onThemeColorChangeListeners = new ArrayList<>();
		this.prefs = prefs;
		selected = prefs.getThemeColor();
		init(selected);
	}

	private void init(int color) {
		if (color < 1 || color > 7) {
			color = new Random().nextInt(7);
		}
		switch (color) {
			case 2:
				appThemeResource = R.style.AppTheme_Brown;
				primaryColorRes = R.color.md_brown_700;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_deep_orange;
				break;
			case 3:
				appThemeResource = R.style.AppTheme_DeepOrange;
				primaryColorRes = R.color.md_deep_orange_800;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_yellow;
				break;
			case 4:
				appThemeResource = R.style.AppTheme_Pink;
				primaryColorRes = R.color.md_pink_800;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_purple;
				break;
			case 5:
				appThemeResource = R.style.AppTheme_Purple;
				primaryColorRes = R.color.md_deep_purple_700;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_pink;
				break;
			case 6:
				appThemeResource = R.style.AppTheme_Red;
				primaryColorRes = R.color.md_red_700;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_purple_light;
				break;
			case 7:
				appThemeResource = R.style.AppTheme_Teal;
				primaryColorRes = R.color.md_teal_700;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_green;
				break;
			case 1:
			default:
				primaryColorRes = R.color.md_blue_700;
				appThemeResource = R.style.AppTheme;
				textColor = R.color.text_primary_light;
				playbackPanelBackground = R.drawable.panel_amber;
		}
	}

	public void updateColorMap(int num) {
		int ondSelected = selected;
		selected = num;
		prefs.setAppThemeColor(selected);
		init(selected);
		if (ondSelected != selected) {
			onThemeColorChange(selected);
		}
	}

	public int getSelected() {
		return selected;
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

	public int[] getColorResources() {
		return new int[] {
				R.color.transparent,
				R.color.md_blue_700,
				R.color.md_brown_700,
				R.color.md_deep_orange_800,
				R.color.md_pink_800,
				R.color.md_deep_purple_700,
				R.color.md_red_700,
				R.color.md_teal_700
		};
	}

	public void addOnThemeColorChangeListener(OnThemeColorChangeListener onThemeColorChangeListener) {
		this.onThemeColorChangeListeners.add(onThemeColorChangeListener);
	}

	public void removeOnThemeColorChangeListener(OnThemeColorChangeListener onThemeColorChangeListener) {
		this.onThemeColorChangeListeners.remove(onThemeColorChangeListener);
	}

	public void onThemeColorChange(int pos) {
		for (int i = 0; i < onThemeColorChangeListeners.size(); i++) {
			onThemeColorChangeListeners.get(i).onThemeColorChange(pos);
		}
	}

	public interface OnThemeColorChangeListener {
		void onThemeColorChange(int pos);
	}
}

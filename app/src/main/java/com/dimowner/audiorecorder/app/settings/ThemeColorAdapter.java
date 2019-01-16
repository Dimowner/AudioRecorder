package com.dimowner.audiorecorder.app.settings;

import android.app.Activity;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.view.LayoutInflater;
import android.widget.TextView;

import com.dimowner.audiorecorder.R;

import java.util.List;

public class ThemeColorAdapter extends ArrayAdapter<ThemeColorAdapter.ThemeItem> {

	private LayoutInflater inflater;
	private List<ThemeItem> data;

	ThemeColorAdapter(Activity context, int res, int txtRes, List<ThemeItem> items){

		super(context, res, txtRes, items);
		this.inflater = context.getLayoutInflater();
		this.data = items;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		return getView(convertView, position, parent, true);
	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		return getView(convertView, position, parent, false);
	}

	private View getView(View convertView, int position, ViewGroup parent, boolean showDrawable) {
		if(convertView == null){
			convertView = inflater.inflate(R.layout.list_item_spinner, parent, false);
		}
		TextView txtColor = convertView.findViewById(R.id.txtColor);
		txtColor.setText(data.get(position).getColorName());
		txtColor.setBackgroundColor(data.get(position).getColor());
		if (!showDrawable) {
			txtColor.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
			Resources r = getContext().getResources();
			float n = r.getDimension(R.dimen.spacing_normal);
			txtColor.setPadding((int)r.getDimension(R.dimen.spacing_huge), (int)n, (int)n, (int)n);
		}
		return txtColor;
	}

	public static class ThemeItem {
		private String colorName;
		private int color;

		ThemeItem(String colorName, int color) {
			this.colorName = colorName;
			this.color = color;
		}

		String getColorName() {
			return colorName;
		}

		public int getColor() {
			return color;
		}
	}
}

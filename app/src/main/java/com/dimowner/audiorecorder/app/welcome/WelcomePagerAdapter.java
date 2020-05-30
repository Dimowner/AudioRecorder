package com.dimowner.audiorecorder.app.welcome;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dimowner.audiorecorder.R;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import static com.dimowner.audiorecorder.app.welcome.WelcomePagerAdapter.*;

/**
 * Created on 05.04.2020.
 * @author Dimowner
 */
public class WelcomePagerAdapter extends RecyclerView.Adapter<PagerItemViewHolder> {

	@NonNull
	@Override
	public PagerItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new PagerItemViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.welcome_page_item, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull PagerItemViewHolder holder, int position) {
		switch (position) {
			case 0:
				holder.txtTitle.setText(R.string.title_1);
				holder.txtDetails.setText(R.string.welcome_1);
				break;
			case 1:
				holder.txtTitle.setText(R.string.title_2);
				holder.txtDetails.setText(R.string.welcome_2);
				break;
			case 2:
				holder.txtTitle.setText(R.string.title_3);
				holder.txtDetails.setText(R.string.welcome_3);
				break;
		}
	}

	@Override
	public int getItemCount() {
		return 1;
	}

	public static class PagerItemViewHolder extends RecyclerView.ViewHolder {
		View view;
		TextView txtTitle;
		TextView txtDetails;

		PagerItemViewHolder(View view) {
			super(view);
			this.view = view;
			txtTitle = view.findViewById(R.id.txt_title);
			txtDetails = view.findViewById(R.id.txt_details);
		}
	}
}

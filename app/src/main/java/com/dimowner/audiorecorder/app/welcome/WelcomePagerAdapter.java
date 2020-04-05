package com.dimowner.audiorecorder.app.welcome;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dimowner.audiorecorder.R;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Created on 05.04.2020.
 * @author Dimowner
 */
public class WelcomePagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

//	private int curPosition = 0;
//
//	private PagerItemViewHolder curItem = null;
//	private Map<Integer, PagerItemViewHolder> holdersMap = new HashMap<>();

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new PagerItemViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.welcome_page_item, parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
	}

//	@Override
//	public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
//		super.onViewAttachedToWindow(holder);
//		holdersMap.put(holder.getAdapterPosition(), (PagerItemViewHolder) holder);
//	}
//
//	@Override
//	public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
//		super.onViewDetachedFromWindow(holder);
//		PagerItemViewHolder vh = (PagerItemViewHolder) holder;
//		vh.resetImagePosition();
//		holdersMap.remove(vh.getAdapterPosition());
//	}

	@Override
	public int getItemCount() {
		return 4;
	}

	public static class PagerItemViewHolder extends RecyclerView.ViewHolder {
		View view;
//		ImageView itemImage;
		TextView txtTitle;
		TextView txtDetails;

		PagerItemViewHolder(View view) {
			super(view);
			this.view = view;
//			itemImage = view.findViewById(R.id.item_image);
			txtTitle = view.findViewById(R.id.txt_title);
			txtDetails = view.findViewById(R.id.txt_details);
		}

//		public void setImageTranslation(int pos) {
//			itemImage.setTranslationY(-pos);
//			itemImage.setTranslationX(pos);
//		}
//
//		public void resetImagePosition() {
//			itemImage.setTranslationY(0);
//		}
	}

//	public void setCurPosition(int position) {
//		curPosition = position;
//		curItem = holdersMap.get(0);
//		if (curItem != null) {
//			curItem.resetImagePosition();
//		}
//	}
//
//	public void setOffset(int offset) {
//		if (curItem != null) {
//			curItem.setImageTranslation(offset);
//		}
//	}
}
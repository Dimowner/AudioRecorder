package com.dimowner.audiorecorder.ui.records;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.AnimationUtil;

import java.util.ArrayList;
import java.util.List;

public class RecordsActivity extends Activity {

	private RecyclerView recyclerView;
	private LinearLayoutManager layoutManager;
	private RecordsAdapter adapter;
	private LinearLayout toolbar;
	private View bottomDivider;

	public static Intent getStartIntent(Context context) {
		return new Intent(context, RecordsActivity.class);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setTheme(ARApplication.getAppThemeResource(getApplicationContext()));
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_records);

		AndroidUtils.setTranslucent(this, true);

		ImageButton btnBack = findViewById(R.id.btn_back);
		btnBack.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { finish(); }});
		toolbar = findViewById(R.id.toolbar);
		toolbar.setBackgroundResource(ARApplication.getPrimaryColorRes(getApplicationContext()));

		bottomDivider = findViewById(R.id.bottomDivider);

		recyclerView = findViewById(R.id.recycler_view);
		recyclerView.setHasFixedSize(true);
		layoutManager = new LinearLayoutManager(getApplicationContext());
		recyclerView.setLayoutManager(layoutManager);

		recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView rv, int dx, int dy) {
				super.onScrolled(rv, dx, dy);
				handleToolbarScroll(dy);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					if (isListOnTop()) {
						AnimationUtil.viewElevationAnimation(toolbar, 0f);
					} else {
						AnimationUtil.viewElevationAnimation(toolbar, getResources().getDimension(R.dimen.toolbar_elevation));
					}
				}
				if (isListOnBottom()) {
					bottomDivider.setVisibility(View.GONE);
				} else {
					bottomDivider.setVisibility(View.VISIBLE);
				}
			}
		});

		adapter = new RecordsAdapter();
		recyclerView.setAdapter(adapter);
		List<ListItem> data = new ArrayList<>(20);
		data.add(new ListItem(ListItem.ITEM_TYPE_HEADER, "HEADER", "Header Description"));
		for (int i = 1; i < 20; i++) {
			data.add(new ListItem(ListItem.ITEM_TYPE_NORMAL,"Name "+ i, "Description " + i));
		}
		adapter.setData(data);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			// Set the padding to match the Status Bar height
			toolbar.setPadding(0, AndroidUtils.getStatusBarHeight(getApplicationContext()), 0, 0);
		}
	}

	private void handleToolbarScroll(int dy) {
		float inset = toolbar.getTranslationY() - dy;
		int height;
		if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			height = toolbar.getHeight() + AndroidUtils.getStatusBarHeight(getApplicationContext());
		} else {
			height = toolbar.getHeight();
		}

		if (inset < -height) {
			inset = -height;
		}

		if (toolbar.getTranslationY() <= 0 && inset > 0) {
			toolbar.setTranslationY(0);

		} else {
			toolbar.setTranslationY(inset);
		}
	}

	public boolean isListOnTop() {
		return (layoutManager.findFirstCompletelyVisibleItemPosition() == 0);
	}

	public boolean isListOnBottom() {
		return (layoutManager.findLastCompletelyVisibleItemPosition() == adapter.getItemCount()-1);
	}
}

package com.dimowner.audiorecorder.app.lostrecords;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 14.12.2019.
 * @author Dimowner
 */
public class LostRecordsActivity extends Activity implements LostRecordsContract.View {

	public static final String EXTRAS_RECORDS_LIST = "records_list";

	private LostRecordsContract.UserActionsListener presenter;

	private LostRecordsAdapter adapter;

	public static Intent getStartIntent(Context context, ArrayList<RecordItem> data) {
		Intent intent = new Intent(context, LostRecordsActivity.class);
		intent.putParcelableArrayListExtra(EXTRAS_RECORDS_LIST, data);
		return intent;
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		setTheme(ARApplication.getInjector().provideColorMap().getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lost_records);

		ImageButton btnBack = findViewById(R.id.btn_back);
		btnBack.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ARApplication.getInjector().releaseLostRecordsPresenter();
				finish();
			}
		});

		Button btnDeleteAll = findViewById(R.id.btn_delete_all);
		btnDeleteAll.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(LostRecordsActivity.this);
				builder.setTitle(R.string.warning)
						.setIcon(R.drawable.ic_delete_forever)
						.setMessage(R.string.delete_all_records)
						.setCancelable(false)
						.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								presenter.deleteRecords(adapter.getData());
								dialog.dismiss();
							}
						})
						.setNegativeButton(R.string.btn_no,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
										dialog.dismiss();
									}
								});
				AlertDialog alert = builder.create();
				alert.show();
			}
		});

		RecyclerView recyclerView = findViewById(R.id.recycler_view);
		recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
		adapter = new LostRecordsAdapter();
		recyclerView.setAdapter(adapter);
		if (getIntent() != null) {
			Bundle extras = getIntent().getExtras();
			if (extras != null && extras.containsKey(EXTRAS_RECORDS_LIST)) {
				adapter.setData(extras.<RecordItem>getParcelableArrayList(EXTRAS_RECORDS_LIST));
			}
		}

		presenter = ARApplication.getInjector().provideLostRecordsPresenter();
	}

	@Override
	protected void onStart() {
		super.onStart();
		presenter.bindView(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (presenter != null) {
			presenter.unbindView();
		}
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		ARApplication.getInjector().releaseLostRecordsPresenter();
	}

	@Override
	public void showLostRecords(List<RecordItem> items) {
		adapter.setData(items);
	}

	@Override
	public void onDeletedRecord(int id) {
		adapter.removeItem(id);
	}

	@Override
	public void clearList() {
		adapter.clearData();
	}

	@Override
	public void showProgress() {

	}

	@Override
	public void hideProgress() {

	}

	@Override
	public void showError(String message) {

	}

	@Override
	public void showError(int resId) {

	}

	@Override
	public void showMessage(int resId) {

	}
}

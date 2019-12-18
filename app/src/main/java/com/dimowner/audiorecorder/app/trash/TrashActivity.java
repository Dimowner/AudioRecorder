package com.dimowner.audiorecorder.app.trash;

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
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.info.ActivityInformation;
import com.dimowner.audiorecorder.app.lostrecords.RecordItem;

import java.util.List;

/**
 * Created on 15.12.2019.
 * @author Dimowner
 */
public class TrashActivity extends Activity implements TrashContract.View {

	//TODO: Remove record from trash after 60 days

	private TrashContract.UserActionsListener presenter;

	private TrashAdapter adapter;
	private TextView txtEmpty;
	private Button btnDeleteAll;

	public static Intent getStartIntent(Context context) {
		return new Intent(context, TrashActivity.class);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		setTheme(ARApplication.getInjector().provideColorMap().getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_trash);

		ImageButton btnBack = findViewById(R.id.btn_back);
		btnBack.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ARApplication.getInjector().releaseTrashPresenter();
				finish();
			}
		});

		btnDeleteAll = findViewById(R.id.btn_delete_all);
		btnDeleteAll.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				AlertDialog.Builder builder = new AlertDialog.Builder(TrashActivity.this);
				builder.setTitle(R.string.warning)
						.setIcon(R.drawable.ic_delete_forever)
						.setMessage(R.string.delete_all_records)
						.setCancelable(false)
						.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
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

		txtEmpty = findViewById(R.id.txtEmpty);
		RecyclerView recyclerView = findViewById(R.id.recycler_view);
		recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
		adapter = new TrashAdapter();
		adapter.setOnItemClickListener(new TrashAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(RecordItem record) {
				presenter.onRecordInfo(record.getName(), record.getDuration()/1000, record.getPath());
			}

			@Override
			public void onDeleteItemClick(final RecordItem record) {
				AlertDialog.Builder builder = new AlertDialog.Builder(TrashActivity.this);
				builder.setTitle(R.string.warning)
						.setIcon(R.drawable.ic_delete_forever)
						.setMessage(R.string.delete_record_forever)
						.setCancelable(false)
						.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								presenter.deleteRecordFromTrash(record.getId(), record.getPath());
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

			@Override
			public void onRestoreItemClick(final RecordItem record) {
				AlertDialog.Builder builder = new AlertDialog.Builder(TrashActivity.this);
				builder.setTitle(R.string.warning)
						.setIcon(R.drawable.ic_restore_from_trash)
						.setMessage(R.string.restore_record)
						.setCancelable(false)
						.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								presenter.restoreRecordFromTrash(record.getId());
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
		recyclerView.setAdapter(adapter);
		presenter = ARApplication.getInjector().provideTrashPresenter();
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

	@Override
	public void showRecords(List<RecordItem> items) {
		adapter.setData(items);
	}

	@Override
	public void showRecordInfo(String name, String format, long duration, long size, String location) {
		startActivity(ActivityInformation.getStartIntent(getApplicationContext(), name, format, duration, size, location));
	}

	@Override
	public void recordDeleted(int resId) {
		adapter.removeItem(resId);
		if (adapter.getItemCount() == 0) {
			showEmpty();
		}
	}

	@Override
	public void recordRestored(int resId) {
		adapter.removeItem(resId);
		if (adapter.getItemCount() == 0) {
			showEmpty();
		}
	}

	@Override
	public void showEmpty() {
		txtEmpty.setVisibility(View.VISIBLE);
		btnDeleteAll.setVisibility(View.GONE);
	}

	@Override
	public void hideEmpty() {
		txtEmpty.setVisibility(View.GONE);
		btnDeleteAll.setVisibility(View.VISIBLE);
	}
}

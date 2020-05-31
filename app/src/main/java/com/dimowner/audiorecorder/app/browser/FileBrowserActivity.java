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

package com.dimowner.audiorecorder.app.browser;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.DownloadService;
import com.dimowner.audiorecorder.app.info.ActivityInformation;
import com.dimowner.audiorecorder.app.info.RecordInfo;
import com.dimowner.audiorecorder.util.AndroidUtils;

import java.util.List;

/**
 * Created on 30.05.2020.
 * @author Dimowner
 */
public class FileBrowserActivity extends Activity implements FileBrowserContract.View, View.OnClickListener {

	public static final int REQ_CODE_READ_EXTERNAL_STORAGE_LOAD_PUBLIC_DIR = 466;

	private FileBrowserContract.UserActionsListener presenter;
	private TextView txtEmpty;
	private TextView txtPath;
	private ProgressBar progressBar;
	private Button btnPrivateDir;
	private Button btnPublicDir;
	private LinearLayout pnlImportProgress;
	private TextView txtImportMessage;

	private FileBrowserAdapter adapter;

	public static Intent getStartIntent(Context context) {
		return new Intent(context, FileBrowserActivity.class);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		setTheme(ARApplication.getInjector().provideColorMap().getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_file_browser);

		ImageButton btnBack = findViewById(R.id.btn_back);
		btnBack.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ARApplication.getInjector().releaseFileBrowserPresenter();
				finish();
			}
		});

		txtEmpty = findViewById(R.id.txtEmpty);
		txtPath = findViewById(R.id.files_path);
		progressBar = findViewById(R.id.progress);
		pnlImportProgress = findViewById(R.id.pnl_import_progress);
		txtImportMessage = findViewById(R.id.txt_import_message);

		btnPrivateDir = findViewById(R.id.tab_private_dir);
		btnPublicDir = findViewById(R.id.tab_public_dir);
		btnPrivateDir.setOnClickListener(this);
		btnPublicDir.setOnClickListener(this);

		RecyclerView recyclerView = findViewById(R.id.recycler_view);
		recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
		adapter = new FileBrowserAdapter(getApplicationContext(), ARApplication.getInjector().provideSettingsMapper());
		adapter.setOnItemClickListener(new FileBrowserAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(RecordInfo record) {
				presenter.onRecordInfo(record);
			}

			@Override
			public void onImportItemClick(RecordInfo record) {
				presenter.importAudioFile(getApplicationContext(), record);
			}

			@Override
			public void onDownloadItemClick(RecordInfo record) {
				DownloadService.startNotification(getApplicationContext(), record.getNameWithExtension(), record.getLocation());
			}

			@Override
			public void onRemoveItemClick(final RecordInfo record) {
				AndroidUtils.showSimpleDialog(
						FileBrowserActivity.this,
						R.drawable.ic_delete_forever,
						R.string.warning,
						getApplicationContext().getString(R.string.delete_record, record.getName()),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								presenter.deleteRecord(record);
							}
						}
				);
			}
		});
		recyclerView.setAdapter(adapter);
		presenter = ARApplication.getInjector().provideFileBrowserPresenter();
	}

	@Override
	protected void onStart() {
		super.onStart();
		presenter.bindView(this);
		presenter.loadFiles(getApplicationContext());
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
		ARApplication.getInjector().releaseFileBrowserPresenter();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.tab_private_dir:
				presenter.selectPrivateDir(getApplicationContext());
				break;
			case R.id.tab_public_dir:
				if (checkStoragePermissionImport(REQ_CODE_READ_EXTERNAL_STORAGE_LOAD_PUBLIC_DIR)) {
					presenter.selectPublicDir(getApplicationContext());
				}
				break;
		}
	}

	@Override
	public void showFileItems(List<RecordInfo> items) {
		adapter.setData(items);
	}

	@Override
	public void showSelectedPrivateDir() {
		btnPrivateDir.setBackgroundResource(R.color.white_transparent_80);
		btnPublicDir.setBackgroundResource(R.color.transparent);
	}

	@Override
	public void showSelectedPublicDir() {
		btnPublicDir.setBackgroundResource(R.color.white_transparent_80);
		btnPrivateDir.setBackgroundResource(R.color.transparent);
	}

	@Override
	public void showRecordInfo(RecordInfo info) {
		startActivity(ActivityInformation.getStartIntent(getApplicationContext(), info));
	}

	@Override
	public void onDeletedRecord(String path) {
		adapter.removeItem(path);
		Toast.makeText(getApplicationContext(), R.string.record_deleted_successfully, Toast.LENGTH_LONG).show();
	}

	@Override
	public void onImportedRecord(String path) {
		adapter.setRecordInDatabase(path);
		Toast.makeText(getApplicationContext(), R.string.record_imported_successfully, Toast.LENGTH_LONG).show();
	}

	@Override
	public void updatePath(String path) {
		txtPath.setText(getString(R.string.records_location, path));
	}

	@Override
	public void showEmpty() {
		adapter.clearData();
		txtEmpty.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideEmpty() {
		txtEmpty.setVisibility(View.GONE);
	}

	@Override
	public void showRecordProcessing() {
		txtImportMessage.setText(R.string.record_processing);
		pnlImportProgress.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideRecordProcessing() {
		pnlImportProgress.setVisibility(View.GONE);
	}

	@Override
	public void showImportStart() {
		txtImportMessage.setText(R.string.import_progress);
		pnlImportProgress.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideImportProgress() {
		pnlImportProgress.setVisibility(View.GONE);
	}

	@Override
	public void showProgress() {
		progressBar.setVisibility(View.VISIBLE);
	}

	@Override
	public void hideProgress() {
		progressBar.setVisibility(View.GONE);
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

	private boolean checkStoragePermissionImport(int code) {
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
					&& checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(
						new String[]{
								Manifest.permission.WRITE_EXTERNAL_STORAGE,
								Manifest.permission.READ_EXTERNAL_STORAGE},
						code);
				return false;
			}
		}
		return true;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == REQ_CODE_READ_EXTERNAL_STORAGE_LOAD_PUBLIC_DIR && grantResults.length > 0
				&& grantResults[0] == PackageManager.PERMISSION_GRANTED
				&& grantResults[1] == PackageManager.PERMISSION_GRANTED) {
			presenter.selectPublicDir(getApplicationContext());
		}
	}
}

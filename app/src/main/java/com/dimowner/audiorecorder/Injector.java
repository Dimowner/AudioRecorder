package com.dimowner.audiorecorder;

import android.content.Context;

import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.FileRepositoryImpl;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.LocalRepositoryImpl;
import com.dimowner.audiorecorder.data.database.RecordsDataSource;
import com.dimowner.audiorecorder.ui.MainContract;
import com.dimowner.audiorecorder.ui.MainPresenter;
import com.dimowner.audiorecorder.ui.records.RecordsContract;
import com.dimowner.audiorecorder.ui.records.RecordsPresenter;

public class Injector {

	private Context context;

//	private Prefs prefs;
//	private RecordsDataSource dataSource;
//	private FileRepository fileRepository;
//	private LocalRepository localRepository;

	private MainContract.UserActionsListener mainPresenter;
	private RecordsContract.UserActionsListener recordsPresenter;

	public Injector(Context context) {
		this.context = context;
	}

	public Prefs providePrefs() {
		return Prefs.getInstance(context);
	}

	public RecordsDataSource provideRecordsDataSource() {
		return RecordsDataSource.getInstance(context);
	}

	public FileRepository provideFileRepository() {
		return FileRepositoryImpl.getInstance(context, providePrefs());
	}

	public LocalRepository provideLocalRepository() {
		return LocalRepositoryImpl.getInstance(provideRecordsDataSource());
	}

	public MainContract.UserActionsListener provideMainPresenter() {
//		return new MainPresenter(providePrefs(), provideFileRepository(), provideLocalRepository());
		if (mainPresenter == null) {
			mainPresenter = new MainPresenter(providePrefs(), provideFileRepository(), provideLocalRepository());
		}
		return mainPresenter;
	}

	public RecordsContract.UserActionsListener provideRecordPresenter() {
		if (recordsPresenter == null) {
			recordsPresenter = new RecordsPresenter(provideLocalRepository());
		}
		return recordsPresenter;
	}

//	private static class FileRepositoryHolder {
//		private static final FileRepository fileRepository = new FileRepositoryImpl();
//	}
}

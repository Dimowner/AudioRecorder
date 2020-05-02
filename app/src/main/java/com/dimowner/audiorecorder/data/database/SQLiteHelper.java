/*
 * Copyright 2018 Dmitriy Ponomarenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.data.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * SQLite database manager class.
 * @author Dimowner
 */
public class SQLiteHelper extends SQLiteOpenHelper {

	SQLiteHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(CREATE_RECORDS_TABLE_SCRIPT);
		db.execSQL(CREATE_TRASH_TABLE_SCRIPT);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(SQLiteHelper.class.getName(),
				"Upgrading database from version " + oldVersion + " to "
						+ newVersion + ", which will destroy all old data");
		if (newVersion == 1) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECORDS);
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRASH);
			onCreate(db);
		} else if (newVersion == 2) {
			db.execSQL(CREATE_TRASH_TABLE_SCRIPT);
		} else if (oldVersion == 1 && newVersion == 3) {
			db.beginTransaction();

			db.execSQL(CREATE_TRASH_TABLE_SCRIPT);

			//Add new fields to the table Records.
			db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_FORMAT + " TEXT NOT NULL DEFAULT '';");
			db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_SIZE + " LONG NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_SAMPLE_RATE + " INTEGER NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_CHANNEL_COUNT + " INTEGER NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_BITRATE + " INTEGER NOT NULL DEFAULT 0;");

			//Add new fields to the table Trash.
			db.execSQL("ALTER TABLE " + TABLE_TRASH + " ADD COLUMN " + COLUMN_FORMAT + " TEXT NOT NULL DEFAULT '';");
			db.execSQL("ALTER TABLE " + TABLE_TRASH + " ADD COLUMN " + COLUMN_SIZE + " LONG NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_TRASH + " ADD COLUMN " + COLUMN_SAMPLE_RATE + " INTEGER NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_TRASH + " ADD COLUMN " + COLUMN_CHANNEL_COUNT + " INTEGER NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_TRASH + " ADD COLUMN " + COLUMN_BITRATE + " INTEGER NOT NULL DEFAULT 0;");

			db.setTransactionSuccessful();
			db.endTransaction();
		} else if (oldVersion == 2 && newVersion == 3) {
			db.beginTransaction();

			//Add new fields to the table Records.
			db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_FORMAT + " TEXT NOT NULL DEFAULT '';");
			db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_SIZE + " LONG NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_SAMPLE_RATE + " INTEGER NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_CHANNEL_COUNT + " INTEGER NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_RECORDS + " ADD COLUMN " + COLUMN_BITRATE + " INTEGER NOT NULL DEFAULT 0;");

			//Add new fields to the table Trash.
			db.execSQL("ALTER TABLE " + TABLE_TRASH + " ADD COLUMN " + COLUMN_FORMAT + " TEXT NOT NULL DEFAULT '';");
			db.execSQL("ALTER TABLE " + TABLE_TRASH + " ADD COLUMN " + COLUMN_SIZE + " LONG NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_TRASH + " ADD COLUMN " + COLUMN_SAMPLE_RATE + " INTEGER NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_TRASH + " ADD COLUMN " + COLUMN_CHANNEL_COUNT + " INTEGER NOT NULL DEFAULT 0;");
			db.execSQL("ALTER TABLE " + TABLE_TRASH + " ADD COLUMN " + COLUMN_BITRATE + " INTEGER NOT NULL DEFAULT 0;");

			db.setTransactionSuccessful();
			db.endTransaction();
		}
	}


	private static final String DATABASE_NAME = "records.db";
	private static final int DATABASE_VERSION = 3;

	//Tables names
	static final String TABLE_RECORDS = "records";
	static final String TABLE_TRASH = "trash";

	//Fields for table Records
	static final String COLUMN_ID = "_id";
	static final String COLUMN_NAME = "name";
	static final String COLUMN_DURATION = "duration";
	static final String COLUMN_CREATION_DATE = "created";
	static final String COLUMN_DATE_ADDED = "added";
	static final String COLUMN_DATE_REMOVED = "removed";
	static final String COLUMN_PATH = "path";
	/** Simplified array of audio record amplitudes that represents waveform. */
	static final String COLUMN_DATA = "data";
	static final String COLUMN_DATA_STR = "data_str";
	static final String COLUMN_WAVEFORM_PROCESSED = "waveform_processed";
	static final String COLUMN_BOOKMARK = "bookmark";
	static final String COLUMN_FORMAT = "format";
	static final String COLUMN_SIZE = "size";
	static final String COLUMN_SAMPLE_RATE = "sample_rate";
	static final String COLUMN_CHANNEL_COUNT = "channel_count";
	static final String COLUMN_BITRATE = "bitrate";

	//Create records table sql statement
	private static final String CREATE_RECORDS_TABLE_SCRIPT =
			"CREATE TABLE " + TABLE_RECORDS + " ("
					+ COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ COLUMN_NAME + " TEXT NOT NULL, "
					+ COLUMN_DURATION + " LONG NOT NULL, "
					+ COLUMN_CREATION_DATE + " LONG NOT NULL, "
					+ COLUMN_DATE_ADDED + " LONG NOT NULL, "
					+ COLUMN_PATH + " TEXT NOT NULL, "
					+ COLUMN_FORMAT + " TEXT NOT NULL DEFAULT '', "
					+ COLUMN_SIZE + " LONG NOT NULL DEFAULT 0, "
					+ COLUMN_SAMPLE_RATE + " INTEGER NOT NULL DEFAULT 0, "
					+ COLUMN_CHANNEL_COUNT + " INTEGER NOT NULL DEFAULT 0, "
					+ COLUMN_BITRATE + " INTEGER NOT NULL DEFAULT 0, "
					+ COLUMN_DATA + " BLOB NOT NULL, "
					+ COLUMN_BOOKMARK + " INTEGER NOT NULL DEFAULT 0, "
					+ COLUMN_WAVEFORM_PROCESSED + " INTEGER NOT NULL DEFAULT 0, "
					+ COLUMN_DATA_STR + " BLOB NOT NULL);";

	//Create trash table sql statement
	private static final String CREATE_TRASH_TABLE_SCRIPT =
			"CREATE TABLE " + TABLE_TRASH + " ("
					+ COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
					+ COLUMN_NAME + " TEXT NOT NULL, "
					+ COLUMN_DURATION + " LONG NOT NULL, "
					+ COLUMN_CREATION_DATE + " LONG NOT NULL, "
					+ COLUMN_DATE_ADDED + " LONG NOT NULL, "
					+ COLUMN_DATE_REMOVED + " LONG NOT NULL, "
					+ COLUMN_PATH + " TEXT NOT NULL, "
					+ COLUMN_FORMAT + " TEXT NOT NULL DEFAULT '', "
					+ COLUMN_SIZE + " LONG NOT NULL DEFAULT 0, "
					+ COLUMN_SAMPLE_RATE + " INTEGER NOT NULL DEFAULT 0, "
					+ COLUMN_CHANNEL_COUNT + " INTEGER NOT NULL DEFAULT 0, "
					+ COLUMN_BITRATE + " INTEGER NOT NULL DEFAULT 0, "
					+ COLUMN_DATA + " BLOB NOT NULL, "
					+ COLUMN_BOOKMARK + " INTEGER NOT NULL DEFAULT 0, "
					+ COLUMN_WAVEFORM_PROCESSED + " INTEGER NOT NULL DEFAULT 0, "
					+ COLUMN_DATA_STR + " BLOB NOT NULL);";
}

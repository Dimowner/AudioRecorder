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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.dimowner.audiorecorder.util.FileUtil;

import java.util.Date;

import timber.log.Timber;

/**
 * Class to communicate with table: {@link SQLiteHelper#TABLE_TRASH} in database.
 * @author Dimowner
 */
public class TrashDataSource extends DataSource<Record> {

	private volatile static TrashDataSource instance;

	public static TrashDataSource getInstance(Context context) {
		if (instance == null) {
			synchronized (RecordsDataSource.class) {
				if (instance == null) {
					instance = new TrashDataSource(context);
				}
			}
		}
		return instance;
	}

	private TrashDataSource(Context context) {
		super(context, SQLiteHelper.TABLE_TRASH);
	}

	@Override
	public ContentValues itemToContentValues(Record item) {
		if (item.getName() != null) {
			ContentValues values = new ContentValues();
			if (item.getId() != Record.NO_ID) {
				values.put(SQLiteHelper.COLUMN_ID, item.getId());
			}
			values.put(SQLiteHelper.COLUMN_NAME, item.getName());
			values.put(SQLiteHelper.COLUMN_DURATION, item.getDuration());
			values.put(SQLiteHelper.COLUMN_CREATION_DATE, item.getCreated());
			values.put(SQLiteHelper.COLUMN_DATE_ADDED, item.getAdded());
			values.put(SQLiteHelper.COLUMN_DATE_REMOVED, new Date().getTime());
			values.put(SQLiteHelper.COLUMN_PATH, item.getPath());
			values.put(SQLiteHelper.COLUMN_FORMAT, item.getFormat());
			values.put(SQLiteHelper.COLUMN_SIZE, item.getSize());
			values.put(SQLiteHelper.COLUMN_SAMPLE_RATE, item.getSampleRate());
			values.put(SQLiteHelper.COLUMN_CHANNEL_COUNT, item.getChannelCount());
			values.put(SQLiteHelper.COLUMN_BITRATE, item.getBitrate());
			values.put(SQLiteHelper.COLUMN_BOOKMARK, item.isBookmarked() ? 1 : 0);
			values.put(SQLiteHelper.COLUMN_WAVEFORM_PROCESSED, item.isWaveformProcessed() ? 1 : 0);
			values.put(SQLiteHelper.COLUMN_DATA, item.getData());
			//TODO: Remove this field from database.
			values.put(SQLiteHelper.COLUMN_DATA_STR, "");
			return values;
		} else {
			Timber.e("Can't convert Record with empty Name!");
			return null;
		}
	}

	@Override
	public Record recordToItem(Cursor cursor) {
		return new Record(
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_ID)),
				cursor.getString(cursor.getColumnIndex(SQLiteHelper.COLUMN_NAME)),
				cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_DURATION)),
				cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_CREATION_DATE)),
				cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_DATE_ADDED)),
				cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_DATE_REMOVED)),
				cursor.getString(cursor.getColumnIndex(SQLiteHelper.COLUMN_PATH)),
				cursor.getString(cursor.getColumnIndex(SQLiteHelper.COLUMN_FORMAT)),
				cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_SIZE)),
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_SAMPLE_RATE)),
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_CHANNEL_COUNT)),
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_BITRATE)),
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_BOOKMARK)) != 0,
				cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_WAVEFORM_PROCESSED)) != 0,
				cursor.getBlob(cursor.getColumnIndex(SQLiteHelper.COLUMN_DATA))
//				Record.stringToArray(
//						cursor.getString(cursor.getColumnIndex(SQLiteHelper.COLUMN_DATA_STR)))
		);
	}
}

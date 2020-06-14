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

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BuildConfig;

/**
 * Base class to communicate with some table T in database.
 * @author Dimowner
 */
public abstract class DataSource<T> {

	/** SQLite database manager. */
	protected SQLiteHelper dbHelper;

	/** Class provides access to database. */
	protected SQLiteDatabase db;

	/** Source table name. */
	protected String tableName;

	/** Tag for logging messages. */
	private final String LOG_TAG = getClass().getSimpleName();


	/**
	 * Constructor.
	 * @param context Application context.
	 * @param tableName Table name.
	 */
	public DataSource (Context context, String tableName) {
		dbHelper = new SQLiteHelper(context);
		this.tableName = tableName;
	}

	/**
	 * Open connection to SQLite database.
	 */
	public void open() {
		db = dbHelper.getWritableDatabase();
	}

	/**
	 * Close connection to SQLite database.
	 */
	public void close() {
		db.close();
		dbHelper.close();
	}

	public boolean isOpen() {
		return db != null && db.isOpen();
	}

	/**
	 * Insert new item into database for table T.
	 * @param item Item that will be inserted ind database.
	 */
	public T insertItem(T item) {
		ContentValues values = itemToContentValues(item);
		if (values != null) {
			int insertId = (int) db.insert(tableName, null, values);
			Log.d(LOG_TAG, "Insert into " + tableName + " id = " + insertId);
			return getItem(insertId);
		} else {
			Log.e(LOG_TAG, "Unable to write empty item!");
			return null;
		}
	}

	/**
	 * Convert item into {@link android.content.ContentValues ContentValues}
	 * @param item Item to convert
	 * @return Converted item into {@link android.content.ContentValues ContentValues}.
	 */
	public abstract ContentValues itemToContentValues(T item);

	/**
	 * Delete item from database for table T.
	 * @param id Item id of element that will be deleted from table T.
	 */
	public int deleteItem(int id) {
		Log.d(LOG_TAG, tableName + " deleted ID = " + id);
		return db.delete(tableName, SQLiteHelper.COLUMN_ID + " = " + id, null);
	}

	/**
	 * Update item in database for table T.
	 * @param item Item that will be updated.
	 */
	public int updateItem(T item) {
		ContentValues values = itemToContentValues(item);
		if (values != null && values.containsKey(SQLiteHelper.COLUMN_ID)) {
			String where = SQLiteHelper.COLUMN_ID + " = "
					+ values.get(SQLiteHelper.COLUMN_ID);
			int n = db.update(tableName, values, where, null);
			Log.d(LOG_TAG, "Updated records count = " + n);
			return n;
		} else {
			Log.e(LOG_TAG, "Unable to update empty item!");
			return 0;
		}
	}

	/**
	 * Get all records from database for table T.
	 * @return List that contains all records of table T.
	 */
	public ArrayList<T> getAll() {
		Cursor cursor = queryLocal("SELECT * FROM " + tableName + " ORDER BY " + SQLiteHelper.COLUMN_DATE_ADDED + " DESC");
		return convertCursor(cursor);
	}

	/**
	 * Get all records from database for table T.
	 * @return List that contains all records of table T.
	 */
	public ArrayList<Integer> getAllItemsIds() {
		Cursor cursor = queryLocal("SELECT " + SQLiteHelper.COLUMN_ID + " FROM " + tableName + " ORDER BY " + SQLiteHelper.COLUMN_DATE_ADDED + " DESC");
		return convertCursorIds(cursor);
	}

	/**
	 * Get records from database for table T.
	 * @return List that contains all records of table T.
	 */
	public ArrayList<T> getRecords(int page) {
		Cursor cursor = queryLocal("SELECT * FROM " + tableName
				+ " ORDER BY " + SQLiteHelper.COLUMN_DATE_ADDED + " DESC"
				+ " LIMIT " + AppConstants.DEFAULT_PER_PAGE
				+ " OFFSET " + (page-1) * AppConstants.DEFAULT_PER_PAGE);
		return convertCursor(cursor);
	}

	/**
	 * Get total records count database for table T.
	 * @return Existing records count of table T.
	 */
	public int getCount() {
		Cursor cursor = queryLocal("SELECT COUNT(*) FROM " + tableName);
		if (cursor != null) {
			cursor.moveToFirst();
			return cursor.getInt(0);
		} else {
			return -1;
		}
	}

	/**
	 * Get records from database for table T.
	 * @return List that contains all records of table T.
	 */
	public ArrayList<T> getRecords(int page, String order) {
		Cursor cursor = queryLocal("SELECT * FROM " + tableName
				+ " ORDER BY " + order
				+ " LIMIT " + AppConstants.DEFAULT_PER_PAGE
				+ " OFFSET " + (page-1) * AppConstants.DEFAULT_PER_PAGE);
		return convertCursor(cursor);
	}

	/**
	 * Delete all records from the table
	 * @throws SQLException on error
	 */
	public void deleteAll() throws SQLException {
		db.execSQL("DELETE FROM " + tableName);
	}

	/**
	 * Get items that match the conditions from table T.
	 * @param where Conditions to select some items.
	 * @return List of some records from table T.
	 */
	public ArrayList<T> getItems(String where) {
		Cursor cursor = queryLocal("SELECT * FROM "
				+ tableName + " WHERE " + where);
		return convertCursor(cursor);
	}

	/**
	 * Get item from table T.
	 * @param id Item id to select.
	 * @return Selected item from table.
	 */
	public T getItem(int id) {
		Cursor cursor = queryLocal("SELECT * FROM " + tableName
				+ " WHERE " + SQLiteHelper.COLUMN_ID + " = " + id);
		List<T> list = convertCursor(cursor);
		if (list.size() > 0) {
			return list.get(0);
		}
		return null;
	}

	/**
	 * Convert {@link android.database.Cursor Cursor} into item T
	 * @param cursor Cursor.
	 * @return T item which corresponds some table in database.
	 */
	public ArrayList<T> convertCursor(Cursor cursor) {
		ArrayList<T> items = new ArrayList<>();
		cursor.moveToFirst();
		while (!cursor.isAfterLast() && !cursor.isBeforeFirst()) {
			items.add(recordToItem(cursor));
			cursor.moveToNext();
		}
		cursor.close();
		if (items.size() > 0) {
			return items;
		}
		return items;
	}

	/**
	 * Convert {@link android.database.Cursor Cursor} into item T
	 * @param cursor Cursor.
	 * @return T item which corresponds some table in database.
	 */
	public ArrayList<Integer> convertCursorIds(Cursor cursor) {
		ArrayList<Integer> items = new ArrayList<>();
		cursor.moveToFirst();
		while (!cursor.isAfterLast() && !cursor.isBeforeFirst()) {
			items.add(cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_ID)));
			cursor.moveToNext();
		}
		cursor.close();
		if (items.size() > 0) {
			return items;
		}
		return items;
	}

	/**
	 * Convert one record of {@link android.database.Cursor Cursor} into item T
	 * @param cursor Cursor positioned to item need to convert.
	 * @return T item which corresponds some table in database.
	 */
	public abstract T recordToItem(Cursor cursor);

	/**
	 * Query to local SQLite database with write to log query text and query result.
	 * @param query Query string.
	 * @return Cursor that contains query result.
	 */
	protected Cursor queryLocal(String query) {
		Log.d(LOG_TAG, "queryLocal: " + query);
		Cursor c = db.rawQuery(query, null);
		if (BuildConfig.DEBUG) {
			StringBuilder data = new StringBuilder("Cursor[");
			if (c.moveToFirst()) {
				do {
					int columnCount = c.getColumnCount();
					data.append("row[");
					for (int i = 0; i < columnCount; ++i) {
						data.append(c.getColumnName(i)).append(" = ");

						switch (c.getType(i)) {
							case Cursor.FIELD_TYPE_BLOB:
								data.append("byte array");
								break;
							case Cursor.FIELD_TYPE_FLOAT:
								data.append(c.getFloat(i));
								break;
							case Cursor.FIELD_TYPE_INTEGER:
								data.append(c.getInt(i));
								break;
							case Cursor.FIELD_TYPE_NULL:
								data.append("null");
								break;
							case Cursor.FIELD_TYPE_STRING:
								data.append(c.getString(i));
								break;
						}
						if (i != columnCount - 1) {
							data.append(", ");
						}
					}
					data.append("]\n");
				} while (c.moveToNext());
			}
			data.append("]");
			Log.d(LOG_TAG, data.toString());
		}
		return c;
	}

	//TODO: move this method
	public List<Long> getRecordsDurations() {
		Cursor cursor = queryLocal("SELECT " + SQLiteHelper.COLUMN_DURATION + " FROM " + tableName);
		ArrayList<Long> items = new ArrayList<>();
		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			items.add(cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_DURATION)));
			cursor.moveToNext();
		}
		cursor.close();
		if (items.size() > 0) {
			return items;
		}
		return items;
	}
}

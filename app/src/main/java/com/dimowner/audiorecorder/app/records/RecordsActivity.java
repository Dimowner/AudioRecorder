/*
 * Copyright 2018 Dmitriy Ponomarenko
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

package com.dimowner.audiorecorder.app.records;

import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.PlaybackService;
import com.dimowner.audiorecorder.app.widget.SimpleWaveformView;
import com.dimowner.audiorecorder.app.widget.TouchLayout;
import com.dimowner.audiorecorder.app.widget.WaveformView;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.AnimationUtil;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class RecordsActivity extends Activity implements RecordsContract.View, View.OnClickListener {

	private RecyclerView recyclerView;
	private LinearLayoutManager layoutManager;
	private RecordsAdapter adapter;

	private LinearLayout toolbar;
	private ProgressBar progressBar;
	private View bottomDivider;
	private ImageButton btnPlay;
	private ImageButton btnStop;
	private ImageButton btnNext;
	private ImageButton btnPrev;
	private ImageButton btnDelete;
	private ImageButton btnBookmarks;
	private ImageButton btnCheckBookmark;
	private TextView txtProgress;
	private TextView txtDuration;
	private TextView txtName;
	private TextView txtEmpty;
	private TextView txtTitle;
	private TouchLayout touchLayout;
	private WaveformView waveformView;
	private ProgressBar panelProgress;
	private SeekBar playProgress;

	private RecordsContract.UserActionsListener presenter;
	private ServiceConnection serviceConnection;
	private PlaybackService playbackService;
	private ColorMap colorMap;
	private boolean isBound = false;

	public static Intent getStartIntent(Context context) {
		Intent intent = new Intent(context, RecordsActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		return intent;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		colorMap = ARApplication.getInjector().provideColorMap();
		setTheme(colorMap.getAppThemeResource());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_records);

		AndroidUtils.setTranslucent(this, true);

		ImageButton btnBack = findViewById(R.id.btn_back);
		btnBack.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) {
				finish();
				ARApplication.getInjector().releaseRecordsPresenter();
			}});
		toolbar = findViewById(R.id.toolbar);

		bottomDivider = findViewById(R.id.bottomDivider);
		progressBar = findViewById(R.id.progress);
		panelProgress = findViewById(R.id.wave_progress);
		btnPlay = findViewById(R.id.btn_play);
		btnStop = findViewById(R.id.btn_stop);
		btnNext = findViewById(R.id.btn_next);
		btnPrev = findViewById(R.id.btn_prev);
		btnDelete = findViewById(R.id.btn_delete);
		btnBookmarks = findViewById(R.id.btn_bookmarks);
		btnCheckBookmark = findViewById(R.id.btn_check_bookmark);
		txtEmpty = findViewById(R.id.txtEmpty);
		txtTitle = findViewById(R.id.txt_title);
		btnPlay.setOnClickListener(this);
		btnStop.setOnClickListener(this);
		btnNext.setOnClickListener(this);
		btnPrev.setOnClickListener(this);
		btnDelete.setOnClickListener(this);
		btnBookmarks.setOnClickListener(this);
		btnCheckBookmark.setOnClickListener(this);

		playProgress = findViewById(R.id.play_progress);
		txtProgress = findViewById(R.id.txt_progress);
		txtDuration = findViewById(R.id.txt_duration);
		txtName = findViewById(R.id.txt_name);
		waveformView = findViewById(R.id.record);

		txtName.setOnClickListener(this);

		playProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					int val = (int)AndroidUtils.dpToPx(progress * waveformView.getWaveformLength() / 1000);
					waveformView.seekPx(val);
					presenter.seekPlayback(val);
				}
			}
			@Override public void onStartTrackingTouch(SeekBar seekBar) { }
			@Override public void onStopTrackingTouch(SeekBar seekBar) { }
		});

		touchLayout = findViewById(R.id.touch_layout);
		touchLayout.setBackgroundResource(colorMap.getPlaybackPanelBackground());
		touchLayout.setOnThresholdListener(new TouchLayout.ThresholdListener() {
			@Override public void onTopThreshold() {
				hidePanel();
				presenter.stopPlayback();
			}
			@Override public void onBottomThreshold() {
				hidePanel();
				presenter.stopPlayback();
			}
			@Override public void onTouchDown() { }
			@Override public void onTouchUp() { }
		});

		recyclerView = findViewById(R.id.recycler_view);
		recyclerView.setHasFixedSize(true);
		layoutManager = new LinearLayoutManager(getApplicationContext());
		recyclerView.setLayoutManager(layoutManager);
		recyclerView.addOnScrollListener(new MyScrollListener(layoutManager));

		recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView rv, int dx, int dy) {
				super.onScrolled(rv, dx, dy);
				handleToolbarScroll(dy);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
					if (isListOnTop()) {
						AnimationUtil.viewElevationAnimation(toolbar, 0f, new Animator.AnimatorListener() {
							@Override public void onAnimationStart(Animator animation) { }
							@Override public void onAnimationEnd(Animator animation) {
								toolbar.setBackgroundResource(android.R.color.transparent);
							}
							@Override public void onAnimationCancel(Animator animation) { }
							@Override public void onAnimationRepeat(Animator animation) { }
						});
					}
				}
				if (adapter.getItemCount() < 5 || isListOnBottom()) {
					bottomDivider.setVisibility(View.GONE);
				} else {
					bottomDivider.setVisibility(View.VISIBLE);
				}
			}
		});

		SimpleWaveformView.setWaveformColorRes(colorMap.getPrimaryColorRes());
		adapter = new RecordsAdapter();
		adapter.setItemClickListener(new RecordsAdapter.ItemClickListener() {
			@Override
			public void onItemClick(View view, long id, String path, final int position) {
				presenter.setActiveRecord(id, new RecordsContract.Callback() {
					@Override public void onSuccess() {
						presenter.stopPlayback();
						presenter.startPlayback();
						adapter.setActiveItem(position);
					}
					@Override public void onError(Exception e) {
						Timber.e(e);
					}
				});
			}
		});
		adapter.setOnAddToBookmarkListener(new RecordsAdapter.OnAddToBookmarkListener() {
			@Override public void onAddToBookmarks(int id) {
				presenter.addToBookmark(id);
			}
			@Override public void onRemoveFromBookmarks(int id) {
				presenter.removeFromBookmarks(id);
			}
		});
		recyclerView.setAdapter(adapter);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			// Set the padding to match the Status Bar height
			toolbar.setPadding(0, AndroidUtils.getStatusBarHeight(getApplicationContext()), 0, 0);
		}
		presenter = ARApplication.getInjector().provideRecordsPresenter();

		waveformView.setOnSeekListener(new WaveformView.OnSeekListener() {
			@Override
			public void onSeek(int px) {
				presenter.seekPlayback(px);
			}
			@Override
			public void onSeeking(int px, long mills) {
				if (waveformView.getWaveformLength() > 0) {
					playProgress.setProgress(1000 * (int) AndroidUtils.pxToDp(px) / waveformView.getWaveformLength());
				}
				txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
			}
		});
	}

	@Override
	public void showPlayerPanel() {
		if (touchLayout.getVisibility() != View.VISIBLE) {
			touchLayout.setVisibility(View.VISIBLE);
			if (touchLayout.getHeight() == 0) {
				touchLayout.setTranslationY(AndroidUtils.dpToPx(800));
			} else {
				touchLayout.setTranslationY(touchLayout.getHeight());
			}
			adapter.showFooter();
			final ViewPropertyAnimator animator = touchLayout.animate();
			animator.translationY(0)
					.setDuration(200)
					.setListener(new Animator.AnimatorListener() {
						@Override public void onAnimationStart(Animator animation) { }
						@Override public void onAnimationEnd(Animator animation) {
							int o = recyclerView.computeVerticalScrollOffset();
							int r = recyclerView.computeVerticalScrollRange();
							int e = recyclerView.computeVerticalScrollExtent();
							float k = (float)o/(float)(r-e);
							recyclerView.smoothScrollBy(0, (int)(touchLayout.getHeight()*k));
							animator.setListener(null);
						}
						@Override public void onAnimationCancel(Animator animation) { }
						@Override public void onAnimationRepeat(Animator animation) { }
					})
					.start();
		}
	}

	@Override
	public void startPlaybackService() {
		Intent intent = new Intent(getApplicationContext(), PlaybackService.class);
		startService(intent);
		serviceConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName n, IBinder service) {
				PlaybackService.PlaybackBinder pb = (PlaybackService.PlaybackBinder) service;
				playbackService = pb.getService();
				playbackService.startForeground(presenter.getRecordName());
				isBound = true;
			}

			@Override
			public void onServiceDisconnected(ComponentName n) {
				isBound = false;
			}
		};

		bindService(intent, serviceConnection, Context.BIND_IMPORTANT);
	}

	@Override
	public void stopPlaybackService() {
		if (isBound && serviceConnection != null) {
			unbindService(serviceConnection);
			isBound = false;
		}
	}

	public void hidePanel() {
		if (touchLayout.getVisibility() == View.VISIBLE) {
			adapter.hideFooter();
			showToolbar();
			final ViewPropertyAnimator animator = touchLayout.animate();
			animator.translationY(touchLayout.getHeight())
					.setDuration(200)
					.setListener(new Animator.AnimatorListener() {
						@Override public void onAnimationStart(Animator animation) { }
						@Override public void onAnimationEnd(Animator animation) {
							touchLayout.setVisibility(View.GONE);
							animator.setListener(null);
						}
						@Override public void onAnimationCancel(Animator animation) { }
						@Override public void onAnimationRepeat(Animator animation) { }
					})
					.start();
		}
	}

	private void showToolbar() {
		AnimationUtil.viewAnimationY(toolbar, 0f, null);
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.btn_play:
				//This method Starts or Pause playback.
				presenter.startPlayback();
				break;
			case R.id.btn_stop:
				presenter.stopPlayback();
				hidePanel();
				break;
			case R.id.btn_next:
				presenter.pausePlayback();
				final long id = adapter.getNextTo(presenter.getActiveRecordId());
				presenter.setActiveRecord(id, new RecordsContract.Callback() {
					@Override public void onSuccess() {
						presenter.stopPlayback();
						presenter.startPlayback();
						int pos = adapter.findPositionById(id);
						if (pos >= 0) {
							recyclerView.scrollToPosition(pos);
							int o = recyclerView.computeVerticalScrollOffset();
							if (o > 0) {
								if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
									toolbar.setTranslationZ(getResources().getDimension(R.dimen.toolbar_elevation));
									toolbar.setBackgroundResource(colorMap.getPrimaryColorRes());
								}
							}
							adapter.setActiveItem(pos);
						}
					}
					@Override public void onError(Exception e) {
						Timber.e(e);
					}
				});
				break;
			case R.id.btn_prev:
				presenter.pausePlayback();
				final long id2 = adapter.getPrevTo(presenter.getActiveRecordId());
				presenter.setActiveRecord(id2, new RecordsContract.Callback() {
					@Override public void onSuccess() {
						presenter.stopPlayback();
						presenter.startPlayback();
						int pos2 = adapter.findPositionById(id2);
						if (pos2 >= 0) {
							recyclerView.scrollToPosition(pos2);
							adapter.setActiveItem(pos2);
						}
					}
					@Override public void onError(Exception e) {
						Timber.e(e);
					}
				});
				break;
			case R.id.btn_delete:
				presenter.pausePlayback();
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.warning)
						.setIcon(R.drawable.ic_delete_forever)
						.setMessage(R.string.delete_record)
						.setCancelable(false)
						.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								presenter.deleteActiveRecord();
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
				break;
			case R.id.btn_check_bookmark:
				presenter.checkBookmarkActiveRecord();
				break;
			case R.id.btn_bookmarks:
				presenter.applyBookmarksFilter();
				break;
			case R.id.txt_name:
				if (presenter.getActiveRecordId() != -1) {
					setRecordName(presenter.getActiveRecordId(), new File(presenter.getActiveRecordPath()));
				}
				break;
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		presenter.bindView(this);
		presenter.loadRecords();
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (presenter != null) {
			presenter.unbindView();
		}
		stopPlaybackService();
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		ARApplication.getInjector().releaseRecordsPresenter();
	}

	private void handleToolbarScroll(int dy) {
		float inset = toolbar.getTranslationY() - dy;
		int height;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			height = toolbar.getHeight() + AndroidUtils.getStatusBarHeight(getApplicationContext());
		} else {
			height = toolbar.getHeight();
		}

		if (inset < -height) {
			inset = -height;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				toolbar.setTranslationZ(getResources().getDimension(R.dimen.toolbar_elevation));
				toolbar.setBackgroundResource(colorMap.getPrimaryColorRes());
			}
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

	@Override
	public void showPlayStart() {
		btnPlay.setImageResource(R.drawable.ic_pause_64);
	}

	@Override
	public void showPlayPause() {
		btnPlay.setImageResource(R.drawable.ic_play_64);
	}

	@Override
	public void showPlayStop() {
		waveformView.setPlayback(-1);
		btnPlay.setImageResource(R.drawable.ic_play_64);
		playProgress.setProgress(0);
		adapter.setActiveItem(-1);
	}

	@Override
	public void showNextRecord() {

	}

	@Override
	public void showPrevRecord() {

	}

	@Override
	public void showWaveForm(int[] waveForm, long duration) {
		waveformView.setWaveform(waveForm);
		waveformView.setPxPerSecond(AndroidUtils.dpToPx(ARApplication.getDpPerSecond((float)duration/1000000f)));
	}

	@Override
	public void showDuration(final String duration) {
		txtProgress.setText(duration);
		txtDuration.setText(duration);
	}

	@Override
	public void onPlayProgress(final long mills, final int px, final int percent) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				waveformView.setPlayback(px);
				txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
				playProgress.setProgress(percent);
			}
		});
	}

	@Override
	public void showRecords(List<ListItem> records) {
		if (records.size() == 0) {
//			txtEmpty.setVisibility(View.VISIBLE);
			adapter.setData(new ArrayList<ListItem>());
		} else {
			adapter.setData(records);
			txtEmpty.setVisibility(View.GONE);
			if (touchLayout.getVisibility() == View.VISIBLE) {
				adapter.showFooter();
			}
		}
	}

	@Override
	public void addRecords(List<ListItem> records) {
		adapter.addData(records);
		txtEmpty.setVisibility(View.GONE);
		if (touchLayout.getVisibility() == View.VISIBLE) {
			adapter.showFooter();
		}
	}

	@Override
	public void showEmptyList() {
		txtEmpty.setText(R.string.no_records);
		txtEmpty.setVisibility(View.VISIBLE);
	}

	@Override
	public void showEmptyBookmarksList() {
		txtEmpty.setText(R.string.no_bookmarks);
		txtEmpty.setVisibility(View.VISIBLE);
	}

	@Override
	public void showPanelProgress() {
		panelProgress.setVisibility(View.VISIBLE);
	}

	@Override
	public void hidePanelProgress() {
		panelProgress.setVisibility(View.GONE);
	}

	@Override
	public void showRecordName(String name) {
		txtName.setText(name);
	}

	@Override
	public void onDeleteRecord(long id) {
		adapter.deleteItem(id);
		if (adapter.getAudioRecordsCount() == 0) {
			showEmptyList();
		}
		hidePanel();
	}

	@Override
	public void addedToBookmarks(int id, boolean isActive) {
		if (isActive) {
			btnCheckBookmark.setImageResource(R.drawable.ic_bookmark);
		}
		adapter.markAddedToBookmarks(id);
	}

	@Override
	public void removedFromBookmarks(int id, boolean isActive) {
		if (isActive) {
			btnCheckBookmark.setImageResource(R.drawable.ic_bookmark_bordered);
		}
		adapter.markRemovedFromBookmarks(id);
	}

	@Override
	public void bookmarksSelected() {
		btnBookmarks.setImageResource(R.drawable.ic_bookmark);
		txtTitle.setText(R.string.bookmarks);
	}

	@Override
	public void bookmarksUnselected() {
		btnBookmarks.setImageResource(R.drawable.ic_bookmark_bordered);
		txtTitle.setText(R.string.records);
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

	public void setRecordName(final long recordId, File file) {
		//Create dialog layout programmatically.
		LinearLayout container = new LinearLayout(getApplicationContext());
		container.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		container.setLayoutParams(containerLp);

		final EditText editText = new EditText(getApplicationContext());
		ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		editText.setLayoutParams(lp);
		editText.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
			@Override public void afterTextChanged(Editable s) {
				if (s.length() > AppConstants.MAX_RECORD_NAME_LENGTH) {
					s.delete(s.length() - 1, s.length());
				}
			}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
		});
		editText.setTextColor(getResources().getColor(R.color.text_primary_light));
		editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_medium));

		int pad = (int) getResources().getDimension(R.dimen.spacing_normal);
		ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(editText.getLayoutParams());
		params.setMargins(pad, pad, pad, pad);
		editText.setLayoutParams(params);
		container.addView(editText);

		final String fileName = FileUtil.removeFileExtension(file.getName());
		editText.setText(fileName);

		AlertDialog alertDialog = new AlertDialog.Builder(this)
				.setTitle(R.string.record_name)
				.setView(container)
				.setPositiveButton(R.string.btn_save, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						String newName = editText.getText().toString();
						if (!fileName.equalsIgnoreCase(newName)) {
							presenter.renameRecord(recordId, newName);
						}
						hideKeyboard();
						dialog.dismiss();
					}
				})
				.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						hideKeyboard();
						dialog.dismiss();
					}
				})
				.create();
		alertDialog.show();
		editText.requestFocus();
		editText.setSelection(editText.getText().length());
		showKeyboard();
	}

	/** Show soft keyboard for a dialog. */
	public void showKeyboard(){
		InputMethodManager inputMethodManager = (InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
	}

	/** Hide soft keyboard after a dialog. */
	public void hideKeyboard(){
		InputMethodManager inputMethodManager = (InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
	}

	public class MyScrollListener extends EndlessRecyclerViewScrollListener {

		public <L extends RecyclerView.LayoutManager> MyScrollListener(L layoutManager) {
			super(layoutManager);
		}

		@Override
		public void onLoadMore(int page, int totalItemsCount) {
//			Timber.v("onLoadMore page = " + page + " count = " + totalItemsCount);
			presenter.loadRecordsPage(page);
		}
	}
}

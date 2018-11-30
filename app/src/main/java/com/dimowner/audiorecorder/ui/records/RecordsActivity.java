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

package com.dimowner.audiorecorder.ui.records;

import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.ui.PlaybackService;
import com.dimowner.audiorecorder.ui.widget.SimpleWaveformView;
import com.dimowner.audiorecorder.ui.widget.ThresholdListener;
import com.dimowner.audiorecorder.ui.widget.TouchLayout;
import com.dimowner.audiorecorder.ui.widget.WaveformView;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.AnimationUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

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
	private TextView txtProgress;
	private TextView txtDuration;
	private TextView txtName;
	private TouchLayout touchLayout;
	private WaveformView waveformView;
	private ProgressBar panelProgress;
	private ProgressBar playProgress;

	private RecordsContract.UserActionsListener presenter;
	private ColorMap colorMap;

	public static Intent getStartIntent(Context context) {
		return new Intent(context, RecordsActivity.class);
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
				ARApplication.getInjector().clearRecordsPresenter();
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
		btnPlay.setOnClickListener(this);
		btnStop.setOnClickListener(this);
		btnNext.setOnClickListener(this);
		btnPrev.setOnClickListener(this);
		btnDelete.setOnClickListener(this);

		playProgress = findViewById(R.id.play_progress);
		txtProgress = findViewById(R.id.txt_progress);
		txtDuration = findViewById(R.id.txt_duration);
		txtName = findViewById(R.id.txt_name);
		waveformView = findViewById(R.id.record);

		touchLayout = findViewById(R.id.touch_layout);
		touchLayout.setBackgroundResource(colorMap.getPlaybackPanelBackground());
		touchLayout.setOnThresholdListener(new ThresholdListener() {
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
//		recyclerView.addItemDecoration(new DividerItemDecoration(getApplicationContext(), DividerItemDecoration.VERTICAL));

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
//					else {
//						AnimationUtil.viewElevationAnimation(toolbar, getResources().getDimension(R.dimen.toolbar_elevation));
//					}
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
			public void onItemClick(View view, long id, String path, int position) {
				presenter.setActiveRecord(id, new RecordsContract.Callback() {
					@Override public void onSuccess() {
						presenter.startPlayback();
					}
					@Override public void onError(Exception e) {
						Timber.e(e);
					}
				});
//				showPlayerPanel();
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
			public void onSeeking(int px) {
				playProgress.setProgress(1000*(int)AndroidUtils.pxToDp(px)/waveformView.getWaveformLength());
				txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2((long) AndroidUtils.convertPxToMills(px)));
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
		intent.setAction(PlaybackService.ACTION_START_PLAYBACK_SERVICE);
		startService(intent);
	}

	@Override
	public void stopPlaybackService() {
		Intent intent = new Intent(getApplicationContext(), PlaybackService.class);
		intent.setAction(PlaybackService.ACTION_STOP_PLAYBACK_SERVICE);
		startService(intent);
	}

	public void hidePanel() {
		if (touchLayout.getVisibility() == View.VISIBLE) {
			adapter.hideFooter();
			final ViewPropertyAnimator animator = touchLayout.animate();
			animator.translationY(touchLayout.getHeight())
					.setDuration(200)
					.setListener(new Animator.AnimatorListener() {
						@Override public void onAnimationStart(Animator animation) { }
						@Override public void onAnimationEnd(Animator animation) {
							touchLayout.setVisibility(View.GONE);
//							recyclerView.smoothScrollBy(0, -touchLayout.getHeight());
							animator.setListener(null);
						}
						@Override public void onAnimationCancel(Animator animation) { }
						@Override public void onAnimationRepeat(Animator animation) { }
					})
					.start();
		}
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
				presenter.setActiveRecord(adapter.getNextTo(presenter.getActiveRecordId()), new RecordsContract.Callback() {
					@Override public void onSuccess() {
						presenter.pausePlayback();
					}
					@Override public void onError(Exception e) {
						Timber.e(e);
					}
				});
				break;
			case R.id.btn_prev:
				presenter.setActiveRecord(adapter.getPrevTo(presenter.getActiveRecordId()), new RecordsContract.Callback() {
					@Override public void onSuccess() {
						presenter.pausePlayback();
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
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		ARApplication.getInjector().clearRecordsPresenter();
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
	}

	@Override
	public void showNextRecord() {

	}

	@Override
	public void showPrevRecord() {

	}

	@Override
	public void showWaveForm(int[] waveForm) {
		waveformView.setWaveform(waveForm);
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
//				Timber.v("onPlayProgress: " + px);
				waveformView.setPlayback(px);
				txtProgress.setText(TimeUtils.formatTimeIntervalHourMinSec2(mills));
				playProgress.setProgress(percent);
			}
		});
	}

	@Override
	public void showRecords(List<ListItem> records) {
		adapter.setData(records);
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
		hidePanel();
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
}

/*
 *  Copyright Jake Song (songyuming1985@gmail.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.example.lt.recorder;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;

import com.example.lt.recorder.utils.Constants;
import com.example.lt.recorder.utils.Utils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;


public class PlayService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener {

    private static final String TAG = "PlayService";

    private MediaPlayer mPlayer;
    private String mFileDir;
    private File[] mFileList;
    private String mFileName;

    private boolean mPaused;

    private NotificationManager mNotifMgr;
    private Notification.Builder mBuilder;
    private RemoteViews mNotificationView;
    private BroadcastReceiver mNotifControlReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        initPlayer();
        initNotification();
        initReceiver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPlayer != null) {
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }
        mNotifMgr.cancel(Constants.NOTIFICATION_ID);
        unregisterReceiver(mNotifControlReceiver);
    }

    private void initPlayer() {
        if (mPlayer == null) {
            mPlayer = new MediaPlayer();
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
        }
        mPlayer.reset();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private IPlayServiceInterface.Stub mBinder = new IPlayServiceInterface.Stub() {

        @Override
        public void setPlayingDir(String fileDir) throws RemoteException {
            doSetPlayingDir(fileDir);
        }

        @Override
        public void playFile(String fileName) throws RemoteException {
            doPlay(fileName);
        }

        @Override
        public void start() throws RemoteException {
            doStart();
        }

        @Override
        public void pause() throws RemoteException {
            doPause();
        }

        @Override
        public void resume() throws RemoteException {
            doResume();
        }
    };

    private void doSetPlayingDir(String fileDir) {
        mFileDir = fileDir;

        mFileList = new File(mFileDir).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(Utils.getAudioFileExt());
            }
        });
        Arrays.sort(mFileList, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return -o1.compareTo(o2);
            }
        });
        updateNotification(Constants.PLAY_STATUS_STOP);
    }

    private void doPlay(String fileName) {
        mFileName = fileName;
        if (mPlayer != null){
            try {
                mPlayer.reset();
                mPlayer.setDataSource(new File(mFileDir, fileName).toString());
                mPlayer.prepare();
                mPlayer.start();
                reqAudioFocus();
                mPaused = false;
                sendStatusChangeBroadcast(Constants.PLAY_STATUS_ING);
                updateNotification(Constants.PLAY_STATUS_ING);
                Log.d(TAG, "doPlay:");
            } catch (IOException e) {
                Log.e(TAG, "doPlay: ", e);
                mPlayer.reset();
                mPlayer.stop();
                updateNotification(Constants.PLAY_STATUS_ERR);
                sendStatusChangeBroadcast(Constants.PLAY_STATUS_ERR);
            }
        }
    }

    private void doStart() {
        if (mFileName == null) {
            return;
        }
        if (mPlayer != null){
            try {
                mPlayer.reset();
                mPlayer.setDataSource(new File(mFileDir, mFileName).toString());
                mPlayer.prepare();
                mPlayer.start();
                reqAudioFocus();
                mPaused = false;
                sendStatusChangeBroadcast(Constants.PLAY_STATUS_ING);
                updateNotification(Constants.PLAY_STATUS_ING);
            } catch (IOException e) {
                Log.e(TAG, "doStart: ", e);
                mPlayer.reset();
                mPlayer.stop();
                updateNotification(Constants.PLAY_STATUS_ERR);
                sendStatusChangeBroadcast(Constants.PLAY_STATUS_ERR);
            }
        }
    }

    private void doPause() {
        if (mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.pause();
            mPaused = true;
            sendStatusChangeBroadcast(Constants.PLAY_STATUS_PAUSED);
            updateNotification(Constants.PLAY_STATUS_PAUSED);
        }
    }

    private void doResume() {
        if (mPlayer != null && mPaused) {
            mPlayer.start();
            reqAudioFocus();
            mPaused = false;
            sendStatusChangeBroadcast(Constants.PLAY_STATUS_ING);
            updateNotification(Constants.PLAY_STATUS_ING);
        }
    }

    private String nextFileName() {
        if (mFileList != null && mFileName != null) {
            int index = -1;
            for (int i = 0; i < mFileList.length; i++) {
                String name = mFileList[i].getName();
                if (name.equalsIgnoreCase(mFileName)) {
                    index = i + 1;
                    break;
                }
            }
            if (index != -1 && index < mFileList.length) {
                return mFileList[index].getName();
            }
        }
        return null;
    }

    private void sendStatusChangeBroadcast(int status) {
        Intent intent = new Intent(Constants.SERVICE_PLAY_STATUS_CHANGE);
        intent.putExtra(Constants.STATUS_CHANGE_FILE, mFileName);
        intent.putExtra(Constants.STATUS_CHANGE_PLAYING, status);
        sendBroadcast(intent);
    }

    @SuppressWarnings("deprecation")
    private void initNotification() {
        mNotifMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new Notification.Builder(this);
        mNotificationView = new RemoteViews(getPackageName(), R.layout.notif_small);
        mNotificationView.setTextViewText(R.id.notif_title, getString(R.string.app_name));
        mNotificationView.setOnClickPendingIntent(R.id.notif_play, getPlayIntent());
        mNotificationView.setOnClickPendingIntent(R.id.notif_next, getPlayNextIntent());
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pintent = PendingIntent.getActivity(this, 0, intent, 0);
        Notification notification = mBuilder.setContent(mNotificationView)
                .setOngoing(true)
                .setContentIntent(pintent)
                .setSmallIcon(R.drawable.ic_notif)
                .build();
        mNotifMgr.notify(Constants.NOTIFICATION_ID, notification);
    }

    private PendingIntent getPlayIntent() {
        Intent intent = new Intent();
        intent.setAction(Constants.NOTIF_ACTION_MEDIA_TOGGLE);
        return PendingIntent.getBroadcast(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getPlayNextIntent() {
        Intent intent = new Intent();
        intent.setAction(Constants.NOTIF_ACTION_MEDIA_NEXT);
        return PendingIntent.getBroadcast(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressWarnings("deprecation")
    private void updateNotification(int status) {
        String title = getString(R.string.app_name);
        if (mFileName != null) {
            title = Utils.getDateString(mFileName);
        }
        if (status == Constants.PLAY_STATUS_ERR) {
            title += ":Playback error";
        }
        mNotificationView.setTextViewText(R.id.notif_title, title);
        mNotificationView.setBoolean(R.id.notif_next, "setEnabled", nextFileName() != null);
        if (TextUtils.isEmpty(mFileName)) {
            mNotificationView.setBoolean(R.id.notif_play, "setEnabled", false);
        } else {
            mNotificationView.setBoolean(R.id.notif_play, "setEnabled", true);
            int resid = status == Constants.PLAY_STATUS_ING ? R.drawable.ic_notif_pause : R.drawable.ic_notif_play;
            Bitmap imgdata = ((BitmapDrawable) getResources().getDrawable(resid, null)).getBitmap();
            mNotificationView.setImageViewBitmap(R.id.notif_play, imgdata);
        }
        Notification notification = mBuilder.setContent(mNotificationView).build();
        mNotifMgr.notify(Constants.NOTIFICATION_ID, notification);
    }

    private void initReceiver() {
        mNotifControlReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && !TextUtils.isEmpty(intent.getAction())) {
                    switch (intent.getAction()) {
                        case Constants.NOTIF_ACTION_MEDIA_TOGGLE:
                            Log.d(TAG, "onReceive: TOGGLE");
                            if (mPlayer != null && mPlayer.isPlaying()) {
                                doPause();
                            } else if (mPlayer != null && !TextUtils.isEmpty(mFileName)) {
                                if (mPaused) {
                                    doResume();
                                } else {
                                    doStart();
                                }
                            }
                            break;
                        case Constants.NOTIF_ACTION_MEDIA_NEXT:
                            Log.d(TAG, "onReceive: NEXT");
                            String next = nextFileName();
                            if (next != null) {
                                doPlay(next);
                            }
                            break;
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.NOTIF_ACTION_MEDIA_TOGGLE);
        filter.addAction(Constants.NOTIF_ACTION_MEDIA_NEXT);
        registerReceiver(mNotifControlReceiver, filter);
    }


    private boolean reqAudioFocus() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        int result = am.requestAudioFocus(mAudioFocusChangeListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private AudioManager.OnAudioFocusChangeListener mAudioFocusChangeListener
            = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.d(TAG, "onAudioFocusChange: LOSS");
                    doPause();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.d(TAG, "onAudioFocusChange: GAIN");
                    doResume();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.d(TAG, "onAudioFocusChange: LOSS_TRANSIENT");
                    doPause();
                    break;
            }
        }
    };

    @Override
    public void onCompletion(MediaPlayer mp) {
        String next = nextFileName();
        if (next != null) {
            doPlay(next);
        } else {
            mPlayer.stop();
            mPaused = false;
            updateNotification(Constants.PLAY_STATUS_STOP);
            sendStatusChangeBroadcast(Constants.PLAY_STATUS_STOP);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mPlayer.reset();
        mPlayer.stop();
        updateNotification(Constants.PLAY_STATUS_ERR);
        sendStatusChangeBroadcast(Constants.PLAY_STATUS_ERR);
        return true;
    }
}

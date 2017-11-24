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

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.lt.recorder.utils.Utils;

import java.io.File;

import static com.example.lt.recorder.utils.Constants.PERMISSION_REQ_CODE_FOR_AUDIO_RECORD;


@SuppressWarnings("deprecation")
public class RecordActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "RecordActivity";

    private TextView mStatusText;
    private ImageView mRecordBtn;
    private WavRecorder mRecorder;
    private String mRecordDir;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        setContentView(R.layout.activity_record);
        mStatusText = (TextView) findViewById(R.id.status_txt);
        mRecordBtn = (ImageView) findViewById(R.id.record_btn);
        mRecordBtn.setOnClickListener(this);
        findViewById(R.id.finish_btn).setOnClickListener(this);
        mRecordDir = Utils.getRecordDir(this);
        if (TextUtils.isEmpty(mRecordDir)) {
            mStatusText.setTextColor(getResources().getColor(R.color.textcolor_highlight));
            mStatusText.setText(R.string.err_nosd);
            mRecordBtn.setEnabled(false);
        } else {
            mStatusText.setTextColor(getResources().getColor(R.color.textcolor_main));
            mStatusText.setText(R.string.rec_status_ready);
            mRecordBtn.setEnabled(true);
        }
        if (mRecorder == null) {
            mRecorder = new WavRecorder();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                stopRecord();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.record_btn:
                toggleRecord();
                break;
            case R.id.finish_btn:
                stopRecord();
                break;
        }
    }

    @Override
    public void onBackPressed() {
        stopRecord();
    }

    private void toggleRecord() {
        if (mRecorder.isRecoding()) {
            if (!mRecorder.isPaused()) {
                pauseRecord();
            } else {
                resumeRecord();
            }
        } else {
            startRecord();
        }
    }

    private boolean reqRecordPermission() {
        //检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "reqRecordPermission: ");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQ_CODE_FOR_AUDIO_RECORD);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQ_CODE_FOR_AUDIO_RECORD: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startRecord();
                } else {
                    mStatusText.setTextColor(getResources().getColor(R.color.textcolor_highlight));
                    mStatusText.setText(R.string.err_cannot_record);
                    mRecordBtn.setEnabled(false);
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
            }
        }
    }

    private void startRecord() {
        if (reqRecordPermission()){
            File file = new File(mRecordDir, System.currentTimeMillis() + Utils.getAudioFileExt());
            mRecorder.setOutputFile(file.toString());
            mRecorder.start();
            mRecordBtn.setImageResource(R.drawable.ic_recording);
            AnimationDrawable frameAnimation = (AnimationDrawable) mRecordBtn.getDrawable();
            frameAnimation.start();
            mStatusText.setTextColor(getResources().getColor(R.color.textcolor_main));
            mStatusText.setText(R.string.rec_status_recording);
        }
    }

    private void pauseRecord() {
        mRecorder.pause();
        Drawable bg = mRecordBtn.getDrawable();
        if (bg instanceof AnimationDrawable){
            ((AnimationDrawable) bg).stop();
        }
        mRecordBtn.setImageResource(R.drawable.record_start);
        mStatusText.setTextColor(getResources().getColor(R.color.textcolor_main));
        mStatusText.setText(R.string.rec_status_paused);
    }

    private void resumeRecord() {
        mRecorder.resume();
        mRecordBtn.setImageResource(R.drawable.ic_recording);
        AnimationDrawable frameAnimation = (AnimationDrawable) mRecordBtn.getDrawable();
        frameAnimation.start();
        mStatusText.setTextColor(getResources().getColor(R.color.textcolor_main));
        mStatusText.setText(R.string.rec_status_recording);
    }

    private void stopRecord() {
        mRecorder.stop();
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRecorder != null) {
            mRecorder.release();
        }
    }
}

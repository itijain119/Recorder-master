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

import android.content.Context;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.lt.recorder.utils.Constants;
import com.example.lt.recorder.utils.Utils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;

class MainAdapter extends RecyclerView.Adapter<MainAdapter.ViewHolder>
        implements PlayServiceAgent.PlayingStatusListener{

    private Context mContext;
    private File[] mRecordFiles;

    MainAdapter(Context context){
        mContext = context;
        PlayServiceAgent.getInstance().addPlayingStatusListener(this);
    }

    boolean isEmpty(){
        return mRecordFiles == null || mRecordFiles.length == 0;
    }

    void loadData() {
        String dir = Utils.getRecordDir(mContext);
        new ListFilesTask().execute(dir);
    }

    @Override
    public MainAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        LinearLayout v = (LinearLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(MainAdapter.ViewHolder holder, int position) {
        File f = mRecordFiles[position];
        String fileName = f.getName();
        holder.mFileName = fileName;
        if (fileName.equalsIgnoreCase(PlayServiceAgent.getInstance().getPlayingFile())){
            holder.mPlayingFlag.setVisibility(View.VISIBLE);
            switch (PlayServiceAgent.getInstance().getPlayState()){
                case Constants.PLAY_STATUS_ING:
                    holder.mPlayingFlag.setText(R.string.play_status_playing);
                    holder.mPlayBtn.setImageResource(R.drawable.ic_pause);
                    break;
                case Constants.PLAY_STATUS_STOP:
                    holder.mPlayingFlag.setText(R.string.play_status_stop);
                    holder.mPlayBtn.setImageResource(R.drawable.ic_play);
                    break;
                case Constants.PLAY_STATUS_PAUSED:
                    holder.mPlayingFlag.setText(R.string.play_status_paused);
                    holder.mPlayBtn.setImageResource(R.drawable.ic_play);
                    break;
                case Constants.PLAY_STATUS_ERR:
                    holder.mPlayingFlag.setText(R.string.play_status_err);
                    holder.mPlayBtn.setImageResource(R.drawable.ic_play);
                    break;
            }
        } else {
            holder.mPlayingFlag.setVisibility(View.GONE);
            holder.mPlayBtn.setImageResource(R.drawable.ic_play);
        }
        holder.mTimeText.setText(Utils.getDateString(fileName));
        holder.mLengthText.setText(mContext.getString(R.string.item_file_size,
                Utils.getFileSizeString(f.length())));
    }

    @Override
    public int getItemCount() {
        if (mRecordFiles == null) {
            return 0;
        } else {
            return mRecordFiles.length;
        }
    }

    @Override
    public void onPlayStatusChange(String fileName, int playState) {
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView mTimeText;
        TextView mLengthText;
        TextView mPlayingFlag;
        ImageView mPlayBtn;
        String mFileName;

        ViewHolder(View v) {
            super(v);
            mTimeText = (TextView) v.findViewById(R.id.item_time);
            mLengthText = (TextView) v.findViewById(R.id.item_length);
            mPlayingFlag = (TextView) v.findViewById(R.id.item_playing);
            mPlayBtn = (ImageView) v.findViewById(R.id.item_play_btn);
            mPlayBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String playingFileName = PlayServiceAgent.getInstance().getPlayingFile();
                    if (mFileName.equalsIgnoreCase(playingFileName)){
                        switch (PlayServiceAgent.getInstance().getPlayState()){
                            case Constants.PLAY_STATUS_ING:
                                PlayServiceAgent.getInstance().pause();
                                break;
                            case Constants.PLAY_STATUS_STOP:
                                PlayServiceAgent.getInstance().start();
                                break;
                            case Constants.PLAY_STATUS_PAUSED:
                                PlayServiceAgent.getInstance().resume();
                                break;
                            case Constants.PLAY_STATUS_ERR:
                                PlayServiceAgent.getInstance().start();
                                break;
                        }
                    } else {
                        PlayServiceAgent.getInstance().playFile(mFileName);
                    }
                }
            });
        }
    }


    private class ListFilesTask extends AsyncTask<String, Void, File[]> {
        @Override
        protected File[] doInBackground(String... params) {
            File dir = new File(params[0]);
            File[] files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(Utils.getAudioFileExt());
                }
            });

            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return -o1.compareTo(o2);
                }
            });
            return files;
        }

        @Override
        protected void onPostExecute(File[] files) {
            mRecordFiles = files;
            notifyDataSetChanged();
        }
    }
}

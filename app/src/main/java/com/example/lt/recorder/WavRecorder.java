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

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.example.lt.recorder.utils.Constants;

import java.io.IOException;
import java.io.RandomAccessFile;



class WavRecorder {

    private AudioRecord mAudioRecord;
    private String mFilePath;
    private int mDataSize;
    private int mMinBufferSize;
    private byte[] mBuffer;
    private Thread mRecordingThread;

    private volatile boolean mPaused;

    WavRecorder() {
        mMinBufferSize = AudioRecord.getMinBufferSize(Constants.RECORD_SAMPLE_RATE,
                Constants.RECORD_CHANNEL_CONFIG,
                Constants.RECORD_FORMAT);
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                Constants.RECORD_SAMPLE_RATE,
                Constants.RECORD_CHANNEL_CONFIG,
                Constants.RECORD_FORMAT,
                mMinBufferSize);
        mBuffer = new byte[mMinBufferSize];
    }

    void setOutputFile(String filePath) {
        mFilePath = filePath;
        mDataSize = 0;
    }

    void start() {

        if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            return;
        }
        if (mFilePath == null) {
            throw new IllegalStateException("You need to set location to save the file！");
        }
        if (mAudioRecord.getState() == AudioRecord.STATE_UNINITIALIZED){//初始化时没有权限
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    Constants.RECORD_SAMPLE_RATE,
                    Constants.RECORD_CHANNEL_CONFIG,
                    Constants.RECORD_FORMAT,
                    mMinBufferSize);
        }
        mAudioRecord.startRecording();
        mRecordingThread = new RecordingThread();
        mRecordingThread.start();
    }


    void pause() {
        mPaused = true;
        mRecordingThread.interrupt();
        mRecordingThread = null;
    }


    void resume() {
        if (mPaused) {
            mRecordingThread = new RecordingThread();
            mRecordingThread.start();
            mPaused = false;
        }
    }


    void stop() {
        if (mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED){
            mAudioRecord.stop();
        }
        if (mRecordingThread != null && mRecordingThread.isAlive()) {
            mPaused = false;
            mRecordingThread.interrupt();
        }
        mRecordingThread = null;
    }


    void release() {
        mAudioRecord.release();
        mAudioRecord = null;
        mBuffer = null;
    }

    boolean isPaused() {
        return mPaused;
    }

    boolean isRecoding() {
        return mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
    }


    private class RecordingThread extends Thread {

        private final String THREAD_TAG = "RecordingThread";
        private RandomAccessFile mFileWriter;

        RecordingThread() {
            super("recording thread");
        }

        @Override
        public void run() {
            try {
                mFileWriter = new RandomAccessFile(mFilePath, "rw");
                if (mDataSize == 0) {
                    writeWavHeader(mFileWriter, (short) 1, Constants.RECORD_SAMPLE_RATE, (short) 16);
                }

                mFileWriter.seek(44 + mDataSize);
            } catch (IOException e) {
                mFileWriter = null;
                Log.e(THREAD_TAG, "run: init file writer", e);
            }

            while (!Thread.interrupted()) {
                if (mFileWriter == null){
                    break;
                }
                int size = mAudioRecord.read(mBuffer, 0, mMinBufferSize);
                if (size > 0) {
                    try {
                        mFileWriter.write(mBuffer, 0, size);
                        mDataSize += size;
                    } catch (IOException e) {
                        Log.e(THREAD_TAG, "run: write recording data", e);
                    }
                }
            }

            if (mFileWriter != null){
                try {
                    updateWavHeader();
                } catch (IOException e) {
                    Log.e(THREAD_TAG, "run: updateWavHeader", e);
                }
            }

            if (mFileWriter != null) {
                try {
                    mFileWriter.close();
                } catch (IOException e) {
                    Log.e(THREAD_TAG, "run: closeFile ", e);
                }
                mFileWriter = null;
            }
        }

        private void writeWavHeader(RandomAccessFile raWriter,
                                    short channels,
                                    int sampleRate,
                                    short bitDepth
        ) throws IOException {
            raWriter.setLength(0); // Set file length to 0, to prevent unexpected behavior in case the file already existed
            raWriter.writeBytes("RIFF");
            raWriter.writeInt(0); // Final file size not known yet, write 0
            raWriter.writeBytes("WAVE");
            raWriter.writeBytes("fmt ");
            raWriter.writeInt(Integer.reverseBytes(16)); // Sub-chunk size, 16 for PCM
            raWriter.writeShort(Short.reverseBytes((short) 1)); // AudioFormat, 1 for PCM
            raWriter.writeShort(Short.reverseBytes(channels));// Number of channels, 1 for mono, 2 for stereo
            raWriter.writeInt(Integer.reverseBytes(sampleRate)); // Sample rate
            raWriter.writeInt(Integer.reverseBytes(sampleRate * channels * bitDepth / 8)); // Byte rate, SampleRate*NumberOfChannels*mBitsPersample/8
            raWriter.writeShort(Short.reverseBytes((short) (channels * bitDepth / 8))); // Block align, NumberOfChannels*mBitsPersample/8
            raWriter.writeShort(Short.reverseBytes(bitDepth)); // Bits per sample
            raWriter.writeBytes("data");
            raWriter.writeInt(0);
        }

        private void updateWavHeader() throws IOException {
            Log.d(THREAD_TAG, "updateWavHeader: RIFF header size = " + (36 + mDataSize));
            mFileWriter.seek(4); // Write size to RIFF header
            mFileWriter.writeInt(Integer.reverseBytes(36 + mDataSize));
            Log.d(THREAD_TAG, "updateWavHeader: Subchunk2Size =" + mDataSize);
            mFileWriter.seek(40); // Write size to Subchunk2Size field
            mFileWriter.writeInt(Integer.reverseBytes(mDataSize));
        }
    }
}

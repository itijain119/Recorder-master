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
package com.example.lt.recorder.utils;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class Utils {

    private static String RecordDir = "";

    public static String getRecordDir(Context c) {
        if (TextUtils.isEmpty(RecordDir)) {
            File f = c.getExternalFilesDir(null);
            if (f != null) {
                RecordDir = f.toString();
            }
        }
        return RecordDir;
    }

    private static ThreadLocal<DateFormat> mFormater = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat("yyyy-M-d HH:mm:ss", Locale.getDefault());
        }
    };

    private static String formatDate(long time) {
        if (time == 0) {
            return "未知";
        }
        return mFormater.get().format(time);
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getAudioFileExt() {
        return ".wav";
    }

    public static String getDateString(@NonNull String fileName) {
        int index = fileName.indexOf(getAudioFileExt());
        String dateStr = fileName.substring(0, index);
        long time = parseLong(dateStr);
        return formatDate(time);
    }

    public static String getFileSizeString(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format(Locale.getDefault(),
                "%.1f %sB", (double) v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}

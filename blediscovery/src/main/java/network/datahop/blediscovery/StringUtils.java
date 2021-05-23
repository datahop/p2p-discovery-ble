/*******************************************************
 * Copyright (C) 2020 DataHop Labs Ltd <sergi@datahop.network>
 *
 * This file is part of DataHop Network project.
 *
 * All rights reserved
 *******************************************************/

package network.datahop.blediscovery;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;


public class StringUtils {

    private static final String TAG = "StringUtils";

    @Nullable
    public static String stringFromBytes(byte[] bytes) {
        String byteString = null;
        try {
            byteString = new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Unable to convert message bytes to string");
        }
        return byteString;
    }
}

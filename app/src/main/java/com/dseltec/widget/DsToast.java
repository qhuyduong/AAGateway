package com.dseltec.widget;

import android.content.Context;
import android.widget.Toast;

public class DsToast extends Toast {
    public DsToast(Context context) {
        super(context);
    }

    public static DsToast makeText(Context context, CharSequence text, int duration) {
        DsToast toast = new DsToast(context);
        return toast;
    }
}

package com.example.BookMate;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/** Keep controls scrollable at large font sizes without consuming the online reader. */
public class LoanControlsView extends ScrollView {
    public LoanControlsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int maximum = Math.round(220 * getResources().getDisplayMetrics().density);
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            maximum = Math.min(maximum, MeasureSpec.getSize(heightSpec) / 3);
        }
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST));
    }
}

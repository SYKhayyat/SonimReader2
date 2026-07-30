package com.sonim.reader.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.sonim.reader.core.LineProvider;
import com.sonim.reader.core.ReaderSettings;

/**
 * Renders lines using the <em>live</em> {@link ReaderSettings}, so a font,
 * theme, or direction change is applied with {@code notifyDataSetChanged()}
 * instead of rebuilding the list. That is the fix for the old bug where every
 * appearance toggle threw the reader back to the top of the book.
 */
public final class LineAdapter extends ArrayAdapter<String> {

    private final LineProvider lines;
    private final ReaderSettings settings;

    public LineAdapter(Context context, LineProvider lines, ReaderSettings settings) {
        super(context, 0);
        this.lines = lines;
        this.settings = settings;
    }

    @Override
    public int getCount() {
        return lines.size();
    }

    @Override
    public String getItem(int position) {
        return lines.getLine(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView tv = (TextView) convertView;
        if (tv == null) {
            tv = new TextView(getContext());
            tv.setPadding(12, 6, 12, 6);
        }
        tv.setText(lines.getLine(position));
        tv.setTextSize(settings.getFontSize());
        tv.setTextColor(settings.isNightMode() ? Color.WHITE : Color.BLACK);
        tv.setTextDirection(settings.isRtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        tv.setGravity(settings.isRtl() ? Gravity.RIGHT : Gravity.LEFT);
        return tv;
    }
}

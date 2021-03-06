/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package ir.besteveryeverapp.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import chitchat.FontManager;
import chitchat.skin.SkinMan;
import ir.besteveryeverapp.telegram.AndroidUtilities;
import ir.besteveryeverapp.telegram.FileLog;
import ir.besteveryeverapp.ui.Components.LayoutHelper;

public class DrawerActionCell extends FrameLayout {

    private TextView textView;
    int color;
    public DrawerActionCell(Context context) {
        super(context);

        color= SkinMan.currentSkin.drawerNamesColor();

        //:ramin
        textView = new TextView(context);
        textView.setTypeface(FontManager.instance().getTypeface());
        textView.setTextColor(
                //0xff444444
                color
        );

        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(FontManager.instance().getTypeface());
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setCompoundDrawablePadding(AndroidUtilities.dp(34));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 14, 0, 16, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
    }

    public void setTextAndIcon(String text, int resId) {
        try {
            textView.setText(text);
            Drawable d= ContextCompat.getDrawable(getContext(), resId);
            d.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
            textView.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null);
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
        }
    }
}

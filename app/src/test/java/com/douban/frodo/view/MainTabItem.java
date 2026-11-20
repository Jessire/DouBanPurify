package com.douban.frodo.view;

import android.content.Context;
import android.view.Gravity;
import android.widget.*;

// Same relationships as view_main_tab_item.xml; pixels reproduce the 1440px device.
public class MainTabItem extends RelativeLayout {
    public final FrameLayout icons;
    public final ImageView icon;
    public final TextView title;
    public MainTabItem(Context c) {
        super(c);
        icons = new FrameLayout(c); icon = new ImageView(c); title = new TextView(c);
        LayoutParams box = new LayoutParams(113, 113);
        box.addRule(CENTER_HORIZONTAL); box.topMargin = 8; addView(icons, box);
        icons.addView(icon, new FrameLayout.LayoutParams(105,105,Gravity.CENTER));
        LayoutParams text = new LayoutParams(-2,-2);
        text.addRule(ALIGN_PARENT_BOTTOM); text.addRule(CENTER_HORIZONTAL);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 38); addView(title,text);
    }
}

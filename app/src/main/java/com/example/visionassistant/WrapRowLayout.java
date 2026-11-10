package com.example.visionassistant;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/** Puts all visible children in one row; those that do not fit wrap into extra rows above (first row is at the bottom). */
public class WrapRowLayout extends ViewGroup {
    private final List<List<View>> rows = new ArrayList<>();
    private int rowHeight = 0;

    public WrapRowLayout(Context c) { super(c); }
    public WrapRowLayout(Context c, AttributeSet a) { super(c, a); }

    private int outer(View v) {
        MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
        return lp.leftMargin + lp.rightMargin;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }
    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) { return new MarginLayoutParams(getContext(), attrs); }
    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) { return new MarginLayoutParams(p); }
    @Override
    protected boolean checkLayoutParams(LayoutParams p) { return p instanceof MarginLayoutParams; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int width = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
        rows.clear();
        List<View> cur = new ArrayList<>();
        int used = 0;
        rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            measureChildWithMargins(v, MeasureSpec.UNSPECIFIED, 0, MeasureSpec.UNSPECIFIED, 0);
            int w = v.getMeasuredWidth() + outer(v);
            if (!cur.isEmpty() && used + w > width) {
                rows.add(cur);
                cur = new ArrayList<>();
                used = 0;
            }
            cur.add(v);
            used += w;
            MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
            rowHeight = Math.max(rowHeight, v.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        }
        if (!cur.isEmpty()) rows.add(cur);
        int h = rows.size() * rowHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(MeasureSpec.getSize(wSpec), resolveSize(h, hSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l - getPaddingLeft() - getPaddingRight();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int ri = 0; ri < rows.size(); ri++) {
            List<View> row = rows.get(ri);
            int top = (b - t) - getPaddingBottom() - (ri + 1) * rowHeight;
            int fixed = 0;
            for (View v : row) fixed += outer(v);
            int each = (width - fixed) / row.size();
            int x = rtl ? (r - l - getPaddingRight()) : getPaddingLeft();
            for (View v : row) {
                MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
                v.measure(MeasureSpec.makeMeasureSpec(each, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(rowHeight - lp.topMargin - lp.bottomMargin, MeasureSpec.EXACTLY));
                int left = rtl ? x - lp.rightMargin - each : x + lp.leftMargin;
                v.layout(left, top + lp.topMargin, left + each, top + rowHeight - lp.bottomMargin);
                x = rtl ? left - lp.leftMargin : left + each + lp.rightMargin;
            }
        }
    }
}

package pro.livemic.studio;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Keeps the studio in the measured app window, including cutouts and system bars. */
final class StudioRootLayout extends LinearLayout {
    private int safeLeft,safeTop,safeRight,safeBottom;
    StudioRootLayout(Context context){super(context);setOrientation(VERTICAL);}
    void safeInsets(int left,int top,int right,int bottom){
        if(safeLeft==left&&safeTop==top&&safeRight==right&&safeBottom==bottom)return;
        safeLeft=left;safeTop=top;safeRight=right;safeBottom=bottom;requestLayout();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec)-safeLeft-safeRight;
        int gutter=StudioTheme.dp(getContext(),width/getResources().getDisplayMetrics().density>=400?20:16);
        boolean expanded=getContext() instanceof MainActivity && ((MainActivity)getContext()).tabletLandscape();
        if(expanded)gutter=StudioTheme.dp(getContext(),24);
        gutter=Math.max(gutter,(width-StudioTheme.dp(getContext(),expanded?1600:480))/2);
        int left=safeLeft+gutter,right=safeRight+gutter;
        if(getPaddingLeft()!=left||getPaddingRight()!=right||getPaddingTop()!=safeTop||getPaddingBottom()!=safeBottom)
            setPadding(left,safeTop,right,safeBottom);
        super.onMeasure(widthSpec,heightSpec);
    }
}

/** Supplies the actual space left after fixed recording controls and navigation. */
final class StudioScrollView extends ScrollView {
    int pendingScrollY = -1;

    StudioScrollView(Context context){
        super(context);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    }

    void preserveScroll(int y) {
        pendingScrollY = y;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (pendingScrollY >= 0) {
            int target = pendingScrollY;
            pendingScrollY = -1;
            int maxScroll = getChildCount() > 0 ? Math.max(0, getChildAt(0).getHeight() - getHeight()) : 0;
            scrollTo(0, Math.min(target, maxScroll));
        }
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        if(getChildCount()>0&&getChildAt(0) instanceof StudioPageLayout)
            ((StudioPageLayout)getChildAt(0)).viewport=Math.max(0,MeasureSpec.getSize(heightSpec)-getPaddingTop()-getPaddingBottom());
        super.onMeasure(widthSpec,heightSpec);
    }
}

/** Gives spare space to the waveform/preview; short windows scroll the body only. */
final class StudioPageLayout extends LinearLayout {
    int viewport;
    int maxContentWidthDp;
    private View flexible;
    private int minimum,maximum;
    StudioPageLayout(Context context){super(context);setOrientation(VERTICAL);}
    void flexible(View view,int minDp,int maxDp){
        flexible=view;minimum=StudioTheme.dp(getContext(),minDp);maximum=StudioTheme.dp(getContext(),maxDp);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int inset=maxContentWidthDp>0?Math.max(0,(MeasureSpec.getSize(widthSpec)-StudioTheme.dp(getContext(),maxContentWidthDp))/2):0;
        if(getPaddingLeft()!=inset||getPaddingRight()!=inset)
            setPadding(inset,getPaddingTop(),inset,getPaddingBottom());
        if(flexible==null||viewport<=0){super.onMeasure(widthSpec,heightSpec);return;}
        super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        int fixed=getMeasuredHeight()-flexible.getMeasuredHeight();
        int height=Math.max(minimum,Math.min(maximum,viewport-fixed));
        ViewGroup.LayoutParams params=flexible.getLayoutParams();
        if(params.height!=height){
            params.height=height;
            View current=flexible;
            while(current!=this){current.forceLayout();if(!(current.getParent() instanceof View))break;current=(View)current.getParent();}
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}

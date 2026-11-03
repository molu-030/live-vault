package pro.livemic.studio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

/** A rotary value control; sound-engine updates belong to the supplied listener. */
final class StudioKnob extends View {
    interface Listener { void changed(float value); }
    private static final java.util.regex.Pattern VALUE_PARTS=java.util.regex.Pattern.compile("^([−+\\-]?[0-9]+(?:\\.[0-9]+)?)\\s*(.*)$");
    private final String label;
    private final float min,max;
    private final MainActivity.Formatter formatter;
    private final Listener listener;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds=new RectF();
    private final int touchSlop;
    private float value,downY,downX,downValue,hitX,hitY,hitRadius;
    private boolean tracking;
    private boolean compact;
    boolean dragging;

    StudioKnob(Context context,String label,float min,float max,float initial,MainActivity.Formatter formatter,Listener listener){
        super(context);
        if(!Float.isFinite(min)||!Float.isFinite(max)||max<=min)throw new IllegalArgumentException("Invalid knob range");
        this.label=label;this.min=min;this.max=max;this.formatter=formatter;this.listener=listener;
        touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
        value=clamp(initial);setFocusable(true);setClickable(true);updateDescription();
    }
    /** A fixed-height main-screen dial with a separate, readable LCD value. */
    StudioKnob compact(){compact=true;requestLayout();invalidate();return this;}
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    private float sp(float n){return n*getResources().getDisplayMetrics().scaledDensity;}
    private float clamp(float n){return Float.isFinite(n)?Math.max(min,Math.min(max,n)):min;}
    private float fraction(){return (value-min)/(max-min);}
    private void updateDescription(){setContentDescription(label+", "+formatter.value(value));}
    void sync(float next){if(tracking||!Float.isFinite(next))return;float v=clamp(next);if(v!=value){value=v;updateDescription();invalidate();}}
    private boolean change(float next){
        if(!Float.isFinite(next))return false;float v=clamp(next);if(Math.abs(v-value)<=Math.max(1e-7f,(max-min)*1e-6f))return false;
        value=v;updateDescription();invalidate();if(listener!=null)listener.changed(value);return true;
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        float scale=getResources().getConfiguration().fontScale;
        int w=resolveSize(Math.round(dp(112)),widthSpec),h=resolveSize(Math.round(compact?dp(96):dp(157)+sp(14)*Math.max(1,scale)),heightSpec);
        setMeasuredDimension(w,h);
    }
    private void circle(Canvas c,float x,float y,float r,int color){paint.setShader(null);paint.setColor(color);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,paint);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(compact){drawCompact(canvas);return;}
        float w=getWidth(),h=getHeight(),cx=w/2f;
        text.setTypeface(StudioTheme.medium(getContext()));text.setTextSize(sp(12));text.setColor(StudioTheme.TEXT);
        int labelWidth=Math.max(1,(int)(w-dp(8)));
        StaticLayout labelLayout=StaticLayout.Builder.obtain(label,0,label.length(),text,labelWidth).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build();
        canvas.save();canvas.translate((w-labelWidth)/2f,dp(3));labelLayout.draw(canvas);canvas.restore();
        float labelBottom=dp(4)+labelLayout.getHeight();
        float bottom=dp(8),available=Math.max(dp(55),h-labelBottom-bottom);
        float diameter=Math.min(w-dp(14),available-dp(6));float r=Math.max(dp(20),diameter*.43f),cy=labelBottom+available/2f;
        float outside=r*1.12f;hitX=cx;hitY=cy;hitRadius=outside+dp(5);
        // Drop shadow and a dark, recessed chassis ring give the metal a physical edge.
        circle(canvas,cx,cy+dp(3),outside+dp(3),0x55000000);
        paint.setShader(new LinearGradient(cx,cy-outside,cx,cy+outside,new int[]{0xff687583,0xff252f39,0xff080d12},null,Shader.TileMode.CLAMP));
        canvas.drawCircle(cx,cy,outside,paint);paint.setShader(null);
        circle(canvas,cx,cy,outside-dp(2),0xff101820);
        float tickInner=r*1.015f,tickOuter=r*1.085f;
        paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(dp(1.2f));
        for(int i=0;i<=30;i++){
            double angle=Math.toRadians(135+i*9);float co=(float)Math.cos(angle),si=(float)Math.sin(angle);
            paint.setColor(i<=Math.round(fraction()*30)?0xff9ba43c:0xff465560);
            float inner=i%5==0?tickInner-dp(2):tickInner;
            canvas.drawLine(cx+co*inner,cy+si*inner,cx+co*tickOuter,cy+si*tickOuter,paint);
        }
        bounds.set(cx-r*.94f,cy-r*.94f,cx+r*.94f,cy+r*.94f);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2.4f));paint.setColor(0xff34434d);canvas.drawArc(bounds,135,270,false,paint);
        paint.setColor(0xffc8da35);canvas.drawArc(bounds,135,270*fraction(),false,paint);paint.setStyle(Paint.Style.FILL);
        float face=r*.84f;
        paint.setShader(new LinearGradient(cx-face,cy-face,cx+face,cy+face,new int[]{0xff93a3ad,0xff44545f,0xff17232c,0xff52616b},new float[]{0,.32f,.76f,1},Shader.TileMode.CLAMP));
        canvas.drawCircle(cx,cy,face,paint);paint.setShader(null);
        circle(canvas,cx,cy,face-dp(2),0xff28343e);
        paint.setShader(new RadialGradient(cx-face*.28f,cy-face*.38f,face*1.7f,new int[]{0xff6c7b86,0xff364650,0xff18242d},new float[]{0,.48f,1},Shader.TileMode.CLAMP));
        canvas.drawCircle(cx,cy,face-dp(3),paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x887e919d);canvas.drawCircle(cx,cy,face-dp(3),paint);paint.setStyle(Paint.Style.FILL);
        double angle=Math.toRadians(135+270*fraction());float ux=(float)Math.cos(angle),uy=(float)Math.sin(angle);
        paint.setStrokeWidth(dp(3));paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0xffdce99a);
        canvas.drawLine(cx+ux*face*.58f,cy+uy*face*.58f,cx+ux*face*.84f,cy+uy*face*.84f,paint);
        // A wide inset and a separate unit line preserve legibility at larger font sizes.
        float insetWidth=face*.78f,insetHeight=face*.52f;
        bounds.set(cx-insetWidth,cy-insetHeight,cx+insetWidth,cy+insetHeight);
        paint.setColor(0xff18232b);canvas.drawRoundRect(bounds,dp(7),dp(7),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0xff73838c);canvas.drawRoundRect(bounds,dp(7),dp(7),paint);paint.setStyle(Paint.Style.FILL);
        String display=formatter.value(value).trim(),number=display,unit="";
        java.util.regex.Matcher match=VALUE_PARTS.matcher(display);
        if(match.matches()){number=match.group(1);unit=match.group(2);}
        text.setTypeface(StudioTheme.mono(getContext()));text.setColor(0xfff3f7f8);text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(sp(15));Paint.FontMetrics fm=text.getFontMetrics();
        float baseline=unit.isEmpty()?cy-(fm.ascent+fm.descent)/2:cy-dp(1);
        canvas.drawText(number,cx,baseline,text);
        if(!unit.isEmpty()){text.setTextSize(sp(10));text.setColor(0xffcad7de);Paint.FontMetrics um=text.getFontMetrics();canvas.drawText(unit,cx,baseline+Math.min(insetHeight*.8f,-um.ascent+dp(1)),text);}
        text.setTextAlign(Paint.Align.LEFT);
    }
    private void drawCompact(Canvas canvas){
        float cx=getWidth()/2f,cy=getHeight()/2f,r=dp(22);
        text.setTypeface(StudioTheme.medium(getContext()));text.setTextSize(sp(11));text.setColor(StudioTheme.TEXT);text.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label,cx,dp(3)-text.getFontMetrics().ascent,text);
        hitX=cx;hitY=cy;hitRadius=dp(26);
        circle(canvas,cx,cy+dp(2),r+dp(2),0x66000000);
        circle(canvas,cx,cy,r+dp(2),0xff45515a);
        circle(canvas,cx,cy,r,0xff151f26);
        bounds.set(cx-r+dp(2),cy-r+dp(2),cx+r-dp(2),cy+r-dp(2));
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(0xff46515a);canvas.drawArc(bounds,135,270,false,paint);
        paint.setColor(StudioTheme.ACCENT);canvas.drawArc(bounds,135,270*fraction(),false,paint);paint.setStyle(Paint.Style.FILL);
        float face=r-dp(5);
        paint.setShader(new LinearGradient(cx-face,cy-face,cx+face,cy+face,new int[]{0xff52616b,0xff28343e,0xff1b252e},null,Shader.TileMode.CLAMP));canvas.drawCircle(cx,cy,face,paint);paint.setShader(null);
        double angle=Math.toRadians(135+270*fraction());
        paint.setColor(0xffe0e9ad);paint.setStrokeWidth(dp(3));
        canvas.drawLine(cx+(float)Math.cos(angle)*face*.42f,cy+(float)Math.sin(angle)*face*.42f,cx+(float)Math.cos(angle)*face*.83f,cy+(float)Math.sin(angle)*face*.83f,paint);
        String display=formatter.value(value);text.setTypeface(StudioTheme.mono(getContext()));text.setTextSize(sp(12));text.setColor(0xffedf3cf);
        float base=getHeight()-dp(3)-text.getFontMetrics().descent;
        bounds.set(cx-dp(31),base+text.getFontMetrics().ascent-dp(2),cx+dp(31),getHeight()-dp(1));paint.setColor(0xff151e20);canvas.drawRoundRect(bounds,dp(4),dp(4),paint);
        canvas.drawText(display,cx,base,text);text.setTextAlign(Paint.Align.LEFT);
        if(isFocused()){paint.setColor(StudioTheme.ACCENT);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));bounds.set(dp(1),dp(1),getWidth()-dp(1),getHeight()-dp(1));canvas.drawRoundRect(bounds,dp(8),dp(8),paint);paint.setStyle(Paint.Style.FILL);}
    }
    private boolean startsAtScreenEdge(MotionEvent event){
        int[] rootPosition=new int[2];View root=getRootView();root.getLocationOnScreen(rootPosition);
        float edge=dp(24),left=edge,right=edge;
        if(android.os.Build.VERSION.SDK_INT>=29&&getRootWindowInsets()!=null){android.graphics.Insets insets=getRootWindowInsets().getSystemGestureInsets();left=Math.max(left,insets.left);right=Math.max(right,insets.right);}
        return event.getRawX()<=rootPosition[0]+left||event.getRawX()>=rootPosition[0]+root.getWidth()-right;
    }
    private void updateHitArea(){
        if(compact){hitX=getWidth()/2f;hitY=getHeight()/2f;hitRadius=dp(26);return;}
        text.setTypeface(StudioTheme.medium(getContext()));text.setTextSize(sp(12));
        int width=Math.max(1,(int)(getWidth()-dp(8)));
        StaticLayout layout=StaticLayout.Builder.obtain(label,0,label.length(),text,width).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build();
        float labelBottom=dp(4)+layout.getHeight(),available=Math.max(dp(55),getHeight()-labelBottom-dp(8));
        float diameter=Math.min(getWidth()-dp(14),available-dp(6));
        hitX=getWidth()/2f;hitY=labelBottom+available/2f;hitRadius=Math.max(dp(20),diameter*.43f)*1.12f+dp(5);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!isEnabled())return false;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(compact&&startsAtScreenEdge(event))return false;
                updateHitArea();float hx=event.getX()-hitX,hy=event.getY()-hitY;
                if(hitRadius<=0||hx*hx+hy*hy>hitRadius*hitRadius)return false;
                downX=event.getX();downY=event.getY();downValue=value;tracking=true;dragging=false;
                // Reserve an accepted interior gesture before a surrounding ScrollView can intercept.
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:
                if(!tracking)return false;
                float dx=event.getX()-downX,dy=event.getY()-downY;
                if(compact&&!dragging&&Math.abs(dx)>touchSlop&&Math.abs(dx)>=Math.abs(dy)){tracking=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return false;}
                if(!dragging&&Math.abs(dy)>touchSlop&&Math.abs(dy)>=Math.abs(dx)){dragging=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}
                if(dragging)change(downValue-dy/Math.max(dp(140),getHeight())*(max-min));return true;
            case MotionEvent.ACTION_UP:
                if(!tracking)return false;boolean wasDragging=dragging;tracking=false;dragging=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(!wasDragging)performClick();return true;
            case MotionEvent.ACTION_CANCEL:tracking=false;dragging=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_DPAD_UP||keyCode==KeyEvent.KEYCODE_DPAD_RIGHT||keyCode==KeyEvent.KEYCODE_PLUS||keyCode==KeyEvent.KEYCODE_EQUALS){change(value+(max-min)*.05f);return true;}
        if(keyCode==KeyEvent.KEYCODE_DPAD_DOWN||keyCode==KeyEvent.KEYCODE_DPAD_LEFT||keyCode==KeyEvent.KEYCODE_MINUS){change(value-(max-min)*.05f);return true;}
        return super.onKeyDown(keyCode,event);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);info.setClassName(SeekBar.class.getName());info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,min,max,value));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
    @Override public boolean performAccessibilityAction(int action,Bundle args){
        if(!isEnabled())return false;
        if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){change(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE));return true;}
        if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){change(value+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1)*(max-min)*.05f);return true;}
        return super.performAccessibilityAction(action,args);
    }
}

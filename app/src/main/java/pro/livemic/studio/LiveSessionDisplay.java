package pro.livemic.studio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import java.util.Locale;

/** A readout of actual engine samples. Only decay and peak hold interpolate between samples. */
final class LiveSessionDisplay extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    float inputDb = Float.NEGATIVE_INFINITY, outputDb = Float.NEGATIVE_INFINITY;
    float frequency, cents;
    String note = "—", recordStatus = "READY", recordClock = "00:00", routeStatus = "";
    boolean recording;
    private final float[] shown = {-60, -60}, peaks = {-60, -60};
    private final long[] holds = {0, 0};
    private long lastFrame;
    LiveSessionDisplay(Context context) {
        super(context);
        setBackground(StudioTheme.background(context, StudioTheme.LCD_BG, 12, StudioTheme.LCD_BORDER));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    static float decibels(float rms) {
        return !Float.isFinite(rms) || rms <= .00001f ? Float.NEGATIVE_INFINITY
                : Math.max(-100, Math.min(0, (float)(20 * Math.log10(rms))));
    }
    static String dbLabel(float db) { return Float.isFinite(db) ? String.format(Locale.US, "%.0f dB", db) : "−∞ dB"; }
    void update(float in, float out, float hz, String pitch, String status, String clock, boolean rec, String route) {
        inputDb = decibels(in); outputDb = decibels(out);
        frequency = Float.isFinite(hz) && hz > 0 ? hz : 0;
        note = frequency > 0 ? pitch : "—";
        recordStatus = status; recordClock = clock; recording = rec; routeStatus = route;
        setContentDescription("LIVE SESSION. IN " + dbLabel(inputDb) + ". OUT " + dbLabel(outputDb)
                + ". " + note + (frequency > 0 ? String.format(Locale.US, " %.0f Hz", frequency) : "")
                + (frequency>0?String.format(Locale.US,". %+.0f cents",cents):"")
                + ". " + recordStatus + " " + recordClock + ". " + routeStatus);
        invalidate();
    }
    private void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean mono) {
        text(c,s,x,y,size,color,align,mono,getWidth()-30*getResources().getDisplayMetrics().density);
    }
    private void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align, boolean mono, float maxWidth) {
        float scale=getResources().getDisplayMetrics().scaledDensity/getResources().getDisplayMetrics().density;
        p.setColor(color); p.setTextSize(size*scale); p.setTextAlign(align);
        p.setTypeface(mono ? StudioTheme.mono(getContext()) : StudioTheme.bold(getContext()));
        float measured=p.measureText(s);
        if(measured>maxWidth&&measured>0)p.setTextSize(p.getTextSize()*maxWidth/measured);
        c.drawText(s, x, y, p);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float d = getResources().getDisplayMetrics().density, w = getWidth(), h = getHeight();
        float left = 15*d, right = w-left;
        // Static etched LCD detail: no waveform or synthetic signal activity.
        p.setStrokeWidth(d*.6f);p.setColor(0xff141e17);
        for(float x=left;x<right;x+=18*d)c.drawLine(x,32*d,x,h-31*d,p);
        for(float y=33*d;y<h-30*d;y+=18*d)c.drawLine(left,y,right,y,p);
        p.setColor(0xff54653c);p.setStrokeWidth(d);
        c.drawLine(left,10*d,left+24*d,10*d,p);c.drawLine(left,10*d,left,14*d,p);
        c.drawLine(right-24*d,h-6*d,right,h-6*d,p);c.drawLine(right,h-10*d,right,h-6*d,p);
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(.2f, (now-lastFrame)/1000f); lastFrame = now;
        text(c, "LIVE SESSION", left, 23*d, 11*d, StudioTheme.OLIVE, Paint.Align.LEFT, false, (right-left)*.47f);
        text(c,routeStatus,right,23*d,9*d,routeStatus.toLowerCase(Locale.ROOT).contains("safety")||routeStatus.contains("보호")?StudioTheme.DANGER:StudioTheme.OLIVE,Paint.Align.RIGHT,false,(right-left)*.5f);
        float bottom = h-13*d;
        text(c, recordStatus, left+(recording?10*d:0), bottom, 10*d, recording ? StudioTheme.ACCENT : StudioTheme.OLIVE, Paint.Align.LEFT, true,(right-left)*.48f-(recording?10*d:0));
        text(c, recordClock, right, bottom, 16*d, StudioTheme.ACCENT, Paint.Align.RIGHT, true,(right-left)*.48f);
        // The tall display spends extra space around the pitch, while meters retain readable spacing.
        float meterTop = 42*d, meterGap = 24*d;
        boolean animating = false;
        for (int row=0; row<2; row++) {
            float target = Math.max(-60, row==0 ? inputDb : outputDb);
            shown[row] = Math.max(target, shown[row]-36*dt);
            if (target >= peaks[row]) { peaks[row]=target; holds[row]=now+850; }
            else if (now>holds[row]) peaks[row]=Math.max(target, peaks[row]-20*dt);
            animating |= shown[row]>target+.1f || peaks[row]>target+.1f;
            float y = meterTop+row*meterGap;
            text(c,row==0?"IN":"OUT",left,y+7*d,9*d,StudioTheme.OLIVE,Paint.Align.LEFT,true,27*d);
            float start=left+31*d, end=right-58*d, gap=2*d;
            int segments=24; float sw=(end-start-gap*(segments-1))/segments;
            for(int i=0;i<segments;i++) {
                float threshold=-60+(i+1)*60f/segments;
                boolean lit=shown[row]>=threshold;
                p.setColor(lit ? (i>=22?StudioTheme.DANGER:i>=19?0xffd3d96a:StudioTheme.ACCENT) : 0xff243022);
                c.drawRoundRect(start+i*(sw+gap),y,start+i*(sw+gap)+sw,y+9*d, d,d,p);
            }
            if(peaks[row]>-59.5f) {
                float x=start+(end-start)*Math.max(0,Math.min(1,(peaks[row]+60)/60));
                p.setColor(StudioTheme.TEXT); c.drawRect(Math.min(end-d,x),y-d,Math.min(end,x+d),y+10*d,p);
            }
            text(c,dbLabel(row==0?inputDb:outputDb),right,y+8*d,10*d,StudioTheme.ACCENT,Paint.Align.RIGHT,true,54*d);
        }
        float pitchY = 83*d + Math.max(0,h-151*d)*.48f;
        float pitchSize=Math.max(26*d,Math.min(45*d,(h-94*d)*.48f));
        float pitchBaseline=Math.max(107*d,pitchY+pitchSize*.65f);
        if(h>=190*d) {
            float radius=Math.min(60*d,(h-110*d)*.5f), cy=pitchBaseline-15*d;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d*.8f);p.setColor(0xff283522);
            c.drawCircle(w/2,cy,radius,p);p.setColor(0xff1c291c);c.drawCircle(w/2,cy,radius-7*d,p);
            p.setColor(0xff405131);
            c.drawLine(w/2-radius-7*d,cy,w/2-radius+5*d,cy,p);
            c.drawLine(w/2+radius-5*d,cy,w/2+radius+7*d,cy,p);
            c.drawLine(w/2,cy-radius-4*d,w/2,cy-radius+4*d,p);
            p.setStyle(Paint.Style.FILL);
        }
        text(c,note,w/2,pitchBaseline,pitchSize,StudioTheme.ACCENT,Paint.Align.CENTER,true,w*.7f);
        text(c,frequency>0?String.format(Locale.US,"(%.0f Hz)",frequency):"(— Hz)",w/2,pitchBaseline+15*d,11*d,StudioTheme.OLIVE,Paint.Align.CENTER,true,w*.8f);
        if(h>=230*d) {
            float span=Math.min(75*d,w*.25f), y=Math.min(bottom-30*d,pitchBaseline+50*d);
            p.setColor(0xff3b4a30);p.setStrokeWidth(d);
            c.drawLine(w/2-span,y,w/2+span,y,p);
            for(int i=-2;i<=2;i++) {
                float x=w/2+i*span/2;
                c.drawLine(x,y-3*d,x,y+3*d,p);
            }
            text(c,"−50",w/2-span,y+15*d,8*d,StudioTheme.OLIVE,Paint.Align.CENTER,true,32*d);
            text(c,"0",w/2,y+15*d,8*d,StudioTheme.OLIVE,Paint.Align.CENTER,true,20*d);
            text(c,"+50",w/2+span,y+15*d,8*d,StudioTheme.OLIVE,Paint.Align.CENTER,true,32*d);
            if(frequency>0) {
                float x=w/2+span*Math.max(-50,Math.min(50,cents))/50;
                p.setColor(StudioTheme.ACCENT);p.setStrokeWidth(2*d);c.drawLine(x,y-6*d,x,y+4*d,p);
                c.drawCircle(x,y-6*d,2*d,p);
            }
        }
        if(recording) { p.setColor(StudioTheme.DANGER); p.setAlpha((now/650)%2==0?255:90); c.drawCircle(left+3*d,bottom-4*d,3*d,p); p.setAlpha(255); }
        if(animating || recording) postInvalidateDelayed(32);
    }
}

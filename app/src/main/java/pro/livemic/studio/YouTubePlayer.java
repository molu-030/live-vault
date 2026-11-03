package pro.livemic.studio;

import android.graphics.Rect;
import android.net.Uri;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebMessage;
import android.webkit.WebMessagePort;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/** Visible official iframe only. No Java bridge, capture, download, or background playback. */
final class YouTubePlayer {
    static YouTubePlayer current;
    final MainActivity a;
    WebMessagePort messages;
    Boolean lastVisible;
    final String origin;
    WebView web;
    YouTubeViewport viewport;
    TextView status;
    String id = "";
    boolean selected;
    private final android.view.ViewTreeObserver.OnScrollChangedListener visibility = this::checkVisibility;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener layout = this::checkVisibility;

    YouTubePlayer(MainActivity activity) {
        a = activity;
        // Recreated activities never inherit playback; request/take mic-only flags are independent.
        if (current != null) current.pause();
        current = this;
        AudioService.youtubeSelected = false;
        origin = "https://" + a.getPackageName();
    }

    static String videoId(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.matches("[A-Za-z0-9_-]{11}")) return value;
        try {
            Uri u = Uri.parse(value);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getUserInfo() != null || u.getPort() != -1) return null;
            String host = u.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            List<String> parts = u.getPathSegments();
            String candidate = null;
            if (host.equals("youtu.be") && parts.size() == 1) candidate = parts.get(0);
            if (host.equals("youtube.com") || host.equals("www.youtube.com") || host.equals("m.youtube.com")) {
                if (parts.size() == 1 && parts.get(0).equals("watch")) candidate = u.getQueryParameter("v");
                else if (parts.size() == 2 && (parts.get(0).equals("embed") || parts.get(0).equals("shorts") || parts.get(0).equals("live"))) candidate = parts.get(1);
            }
            return candidate != null && candidate.matches("[A-Za-z0-9_-]{11}") ? candidate : null;
        } catch (RuntimeException e) { return null; }
    }

    /** All native source entrypoints call this before taking playback ownership. */
    static boolean beforeNativePlayback() {
        if ((RecordingManager.active && RecordingManager.micOnly) || (AudioService.waiting() && AudioService.pendingMicOnly)) return false;
        if (current != null && current.selected) current.a.leaveYouTube();
        return true;
    }

    void attach(LinearLayout parent) {
        if (!selected) return;
        if (web == null) create();
        if (viewport.getParent() != null) ((ViewGroup) viewport.getParent()).removeView(viewport);
        // Native measurement and the CSS viewport share the same bounds. Never crop the iframe.
        parent.addView(viewport, new LinearLayout.LayoutParams(-1, -2));
        status = a.text(a.tr("마이크만 녹음 · 이어폰 권장", "Mic only · Headphones recommended"), 11, MainActivity.MUTED);
        status.setContentDescription(a.tr("YouTube · 마이크만 녹음 · 이어폰 권장", "YouTube · microphone-only recording · headphones recommended"));
        parent.addView(status);
    }

    private void create() {
        web = new WebView(a);
        viewport = new YouTubeViewport(a);
        viewport.addView(web, new android.widget.FrameLayout.LayoutParams(-1, -1));
        web.setBackgroundColor(MainActivity.BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Player UI links may leave the app only through an explicit user gesture.
                if (request.isForMainFrame()) {
                    pause();
                    if (request.hasGesture() && "https".equals(request.getUrl().getScheme())) a.open(request.getUrl(), "text/html");
                    return true;
                }
                return !"https".equals(request.getUrl().getScheme());
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!url.equals(origin + "/youtube")) return;
                if (messages != null) messages.close();
                WebMessagePort[] ports = view.createWebMessageChannel();
                messages = ports[0];
                messages.setWebMessageCallback(new WebMessagePort.WebMessageCallback() {
                    @Override public void onMessage(WebMessagePort port, WebMessage message) {
                        String code = message.getData();
                        if (status != null && "ready".equals(code)) {
                            status.setText(a.tr("마이크만 녹음 · 이어폰 권장", "Mic only · Headphones recommended"));
                            status.setContentDescription(a.tr("YouTube · 마이크만 녹음 · 이어폰 권장", "YouTube · microphone-only recording · headphones recommended"));
                        }
                        if (status != null && code != null && code.matches("2|5|100|101|150|153|network|timeout")) {
                            status.setText(a.tr("YouTube 재생 불가 · ", "YouTube playback unavailable · ") + code + a.tr(" · YouTube에서 열거나 다른 동영상을 선택하세요.", " · select again on Online to retry, or open in YouTube."));
                        }
                    }
                });
                view.postWebMessage(new WebMessage("connect", new WebMessagePort[]{ports[1]}), Uri.parse(origin));
                volume(a.values[1]); lastVisible = null; checkVisibility();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame() && status != null) status.setText(a.tr("YouTube를 불러오지 못했습니다. 연결을 확인하고 다시 선택하세요.", "Could not load YouTube. Check the connection and select the video again."));
            }
        });
        web.getViewTreeObserver().addOnScrollChangedListener(visibility);
        web.getViewTreeObserver().addOnGlobalLayoutListener(layout);
        load();
    }

    void load() {
        if (web == null) return;
        if (messages != null) { messages.close(); messages = null; }
        lastVisible = null;
        int gain = Math.round(Math.max(0, Math.min(1, a.values[1])) * 100);
        // All interpolated strings are app-owned constants or a strictly validated video ID.
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='referrer' content='strict-origin-when-cross-origin'><style>html,body{margin:0;width:100%;height:100%;background:#000;overflow:hidden}#player{display:block;width:100%;height:100%;border:0}</style></head><body><div id='player'></div><script>var p,port,lastError='',ready=false,allowed=false;function reportError(code){lastError=String(code);if(port)port.postMessage(lastError);}var readyTimer=setTimeout(function(){if(!ready)reportError('timeout');},15000);window.addEventListener('message',function(e){if(e.source===null&&(e.origin==='" + origin + "'||e.origin===''||e.origin==='null')&&e.data==='connect'&&e.ports.length===1){port=e.ports[0];if(lastError)port.postMessage(lastError);}});var gain=" + gain + ";function volume(v){gain=v;if(p&&p.setVolume){p.setVolume(gain);if(gain===0)p.mute();else p.unMute();}}function pause(){allowed=false;if(p&&p.pauseVideo)p.pauseVideo();}function onYouTubeIframeAPIReady(){p=new YT.Player('player',{host:'https://www.youtube-nocookie.com',videoId:'" + id + "',playerVars:{autoplay:0,controls:1,playsinline:1,fs:0,origin:'" + origin + "',widget_referrer:'" + origin + "'},events:{onReady:function(){ready=true;clearTimeout(readyTimer);lastError='';if(port)port.postMessage('ready');volume(gain);if(!allowed)p.pauseVideo();},onStateChange:function(e){if(e.data===1&&!allowed)p.pauseVideo();},onError:function(e){reportError(e.data);}}});}function visible(v){allowed=v;if(!v&&p&&p.pauseVideo)p.pauseVideo();}document.addEventListener('visibilitychange',function(){if(document.hidden)pause();});</script><script src='https://www.youtube.com/iframe_api' onerror=\"reportError('network')\"></script></body></html>";
        // Official iframe error UI stays visible; a top-frame-only message port reports bounded error codes.
        web.loadDataWithBaseURL(origin + "/youtube", html, "text/html", "UTF-8", null);
    }

    void volume(float value) {
        if (web != null) web.evaluateJavascript("volume(" + Math.round(Math.max(0, Math.min(1, value)) * 100) + ")", null);
    }
    void checkVisibility() {
        if (web == null) return;
        Rect rect = new Rect();
        boolean shown = selected && a.activityVisible && a.hasWindowFocus() && (a.tab == 0 || (a.tab == 1 && a.sourceTab == 2)) && web.isShown()
                && web.getGlobalVisibleRect(rect) && rect.width() >= a.dp(200) && rect.height() >= a.dp(200) && rect.width() >= web.getWidth() && rect.height() >= web.getHeight();
        if (lastVisible == null || lastVisible != shown) {
            lastVisible = shown;
            web.evaluateJavascript("visible(" + shown + ")", null);
        }
    }
    void pause() { lastVisible = false; if (web != null) web.evaluateJavascript("pause()", null); }
    void detach() {
        pause(); status = null;
        if (viewport != null && viewport.getParent() != null) ((ViewGroup) viewport.getParent()).removeView(viewport);
    }
    void destroy() {
        detach();
        if (current == this) current = null;
        if (messages != null) { messages.close(); messages = null; }
        if (web != null) {
            web.getViewTreeObserver().removeOnScrollChangedListener(visibility);
            web.getViewTreeObserver().removeOnGlobalLayoutListener(layout);
            web.stopLoading(); web.destroy(); web = null;
        }
    }
}

"""Offline browser regression of application-emitted HTML; no real YouTube playback.

Requires Python Playwright (pip install playwright) and an installed Chromium.
Generate the app HTML fixture with YouTubePlayerTest before running this script.
Use --help to override the source HTML, JSON report, or Chromium executable.
"""
import argparse
import json
import math
import traceback
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, default=ROOT / 'app/build/outputs/studio-previews/v386-youtube-embed.html')
parser.add_argument('--report', type=Path, default=ROOT / 'app/build/outputs/studio-previews/v386-browser.json')
parser.add_argument('--chromium', default='/usr/bin/chromium')
args = parser.parse_args()
SOURCE = args.source.resolve()
REPORT = args.report.resolve()


def portable_path(path):
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)

ORIGIN = 'https://pro.livemic.studio.personal'
STUB = '''window.calls=[];window.YT={Player:function(id,options){
window.playerOptions=options;window.playerEvents=options.events;
var iframe=document.createElement('iframe');iframe.id=id;
iframe.setAttribute('width','640');iframe.setAttribute('height','360');
iframe.setAttribute('frameborder','0');iframe.setAttribute('allowfullscreen','');
document.getElementById(id).replaceWith(iframe);
var p={setVolume:function(v){calls.push(['volume',v]);},mute:function(){calls.push(['mute']);},unMute:function(){calls.push(['unmute']);},pauseVideo:function(){calls.push(['pause']);},playVideo:function(){calls.push(['play']);}};
setTimeout(function(){options.events.onReady();},0);return p;
}};onYouTubeIframeAPIReady();'''
checks = []
measurements = []
blocked_requests = []
result = {'passed': False, 'source': portable_path(SOURCE), 'report': portable_path(REPORT), 'checks': checks,
          'layoutMeasurements': measurements,
          'boundary': 'OFFLINE YouTube API stub replacing the app placeholder with a real 640x360-attributed iframe. Actual application HTML/CSS/JavaScript run in Chromium; no YouTube network, decoded video aspect ratio, or Android device playback verified.'}


def assert_layout(page, width, height, phase):
    page.wait_for_function('''() => {const r=document.querySelector('iframe#player').getBoundingClientRect();return Math.abs(r.width-innerWidth)<.01&&Math.abs(r.height-innerHeight)<.01;}''')
    bounds = page.evaluate('''() => {
      const r=document.querySelector('iframe#player').getBoundingClientRect();
      return {x:r.x,y:r.y,width:r.width,height:r.height,
        viewportWidth:innerWidth,viewportHeight:innerHeight,
        bodyWidth:document.body.scrollWidth,bodyHeight:document.body.scrollHeight,
        documentWidth:document.documentElement.scrollWidth,documentHeight:document.documentElement.scrollHeight,
        attributeWidth:document.querySelector('#player').getAttribute('width'),
        attributeHeight:document.querySelector('#player').getAttribute('height')};
    }''')
    assert abs(bounds['x']) < .01 and abs(bounds['y']) < .01, bounds
    assert bounds['viewportWidth'] == width and bounds['viewportHeight'] == height, bounds
    assert abs(bounds['width'] - width) < .01 and abs(bounds['height'] - height) < .01, bounds
    assert bounds['bodyWidth'] <= width and bounds['bodyHeight'] <= height, bounds
    assert bounds['documentWidth'] <= width and bounds['documentHeight'] <= height, bounds
    assert bounds['attributeWidth'] == '640' and bounds['attributeHeight'] == '360', bounds
    measurements.append({'phase': phase, **bounds})


try:
    html = SOURCE.read_text()
    with sync_playwright() as pw:
        browser = pw.chromium.launch(executable_path=args.chromium, headless=True,
                                    args=['--no-sandbox', '--disable-dev-shm-usage'])
        context = browser.new_context(viewport={'width': 200, 'height': 200})

        def handle(route):
            if route.request.url == ORIGIN + '/youtube':
                route.fulfill(status=200, content_type='text/html', body=html)
            elif route.request.url == 'https://www.youtube.com/iframe_api':
                route.fulfill(status=200, content_type='application/javascript', body=STUB)
            else:
                blocked_requests.append(route.request.url)
                route.abort()

        context.route('**/*', handle)
        page = context.new_page()
        page.goto(ORIGIN + '/youtube')
        page.wait_for_function('window.playerOptions && window.calls.some(c=>c[0]==="pause")')
        assert page.locator('iframe#player').count() == 1
        assert page.evaluate('playerOptions.host') == 'https://www.youtube-nocookie.com'
        assert page.evaluate('playerOptions.playerVars.autoplay') == 0
        assert page.evaluate('playerOptions.playerVars.controls') == 1
        assert page.evaluate('playerOptions.playerVars.origin') == ORIGIN
        checks.append('Official privacy host, matching origin, visible controls, no autoplay')

        sizes = [(w, max(200, math.ceil(w * 9 / 16))) for w in (200, 308, 348, 600)]
        sizes.append((200, 360))
        for width, height in sizes:
            page.set_viewport_size({'width': width, 'height': height})
            assert_layout(page, width, height, 'resize')
        # Returning to the smallest dimensions detects stale fixed iframe sizing.
        page.set_viewport_size({'width': 200, 'height': 200})
        assert_layout(page, 200, 200, 'resize-back')
        checks.append('Real iframe fills all five native-sized/tall viewports and shrinks back without body or document overflow')

        page.evaluate('calls=[];visible(true);volume(42)')
        assert page.evaluate('calls') == [['volume', 42], ['unmute']]
        page.evaluate('calls=[];volume(0)')
        assert page.evaluate('calls') == [['volume', 0], ['mute']]
        checks.append('Application JavaScript applies user volume and mutes zero gain')
        page.evaluate('calls=[];visible(false);playerEvents.onStateChange({data:1})')
        assert page.evaluate('calls') == [['pause'], ['pause']]
        page.evaluate('calls=[];visible(true)')
        assert page.evaluate('calls') == []
        page.evaluate('calls=[];Object.defineProperty(document,"hidden",{value:true,configurable:true});document.dispatchEvent(new Event("visibilitychange"))')
        assert page.evaluate('calls') == [['pause']]
        page.evaluate('calls=[];Object.defineProperty(document,"hidden",{value:false,configurable:true});document.dispatchEvent(new Event("visibilitychange"));playerEvents.onStateChange({data:1})')
        assert page.evaluate('calls') == [['pause']]
        checks.append('Hidden/native-invisible playback pauses; visibility return never auto-resumes')
        page.evaluate('window.received=[];window.channel=new MessageChannel();channel.port2.onmessage=e=>received.push(e.data);window.dispatchEvent(new MessageEvent("message",{source:null,origin:location.origin,data:"connect",ports:[channel.port1]}));playerEvents.onError({data:153})')
        page.wait_for_function('received.length===1')
        assert page.evaluate('received') == ['153']
        checks.append('Top-frame channel reports iframe error 153')

        failed = context.new_page()
        failed.clock.install()

        def network_failure(route):
            if route.request.url == ORIGIN + '/youtube':
                route.fulfill(status=200, content_type='text/html', body=html)
            else:
                blocked_requests.append(route.request.url)
                route.abort()

        failed.route('**/*', network_failure)
        failed.goto(ORIGIN + '/youtube')
        assert failed.evaluate('lastError') == 'network'
        buffered = failed.evaluate('''async () => {
          window.received=[];window.channel=new MessageChannel();
          const first=new Promise(resolve=>channel.port2.onmessage=e=>{received.push(e.data);resolve(e.data);});
          window.dispatchEvent(new MessageEvent('message',{source:null,origin:location.origin,data:'connect',ports:[channel.port1]}));
          return await first;
        }''')
        assert buffered == 'network'
        failed.clock.fast_forward(15000)
        assert failed.evaluate('lastError') == 'timeout'
        failed.add_script_tag(content=STUB)
        failed.clock.fast_forward(1)
        assert failed.evaluate('ready') is True
        assert failed.evaluate('lastError') == ''
        checks.append('Pre-channel network error buffered; ready timeout reported; late API readiness recovers')
        browser.close()
    result['passed'] = True
except Exception:
    result['error'] = traceback.format_exc()
finally:
    result['blockedNetworkRequests'] = blocked_requests
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))

if not result['passed']:
    raise SystemExit(1)

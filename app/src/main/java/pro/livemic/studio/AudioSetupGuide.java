package pro.livemic.studio;

import android.view.View;
import android.graphics.Rect;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** Guides existing controls without starting audio or claiming an acoustic measurement. */
final class AudioSetupGuide {
    private final MainActivity a;
    private final View[] sections = new View[3];
    private TextView inputStatus, protection;
    private Button mic;
    private String observedRoute;
    private boolean routeChanged;

    AudioSetupGuide(MainActivity activity) { a = activity; }

    void header() {
        String route = routeKey(a);
        if (observedRoute != null && !observedRoute.equals(route)) routeChanged = true;
        observedRoute = route;
        a.note(a.tr("장치 선택 → 입력 레벨 확인 → 싱크·보호 확인. 필요한 단계로 이동하세요.",
                "Choose devices → check input level → check sync and protection. Jump to any step."));
        LinearLayout steps = a.row();
        String[] labels = {a.tr("1 장치", "1 Devices"), a.tr("2 입력", "2 Input"), a.tr("3 싱크·보호", "3 Sync / safety")};
        for (int i = 0; i < labels.length; i++) {
            final int step = i;
            a.weighted(steps, a.button(labels[i], () -> focus(step)), 48);
        }
        a.add(steps);
        a.note(a.tr("현재 장치 · ", "Current devices · ") + a.inputName() + " → " + a.outputName());
        if (routeChanged) a.note(a.tr("장치가 바뀌었습니다. 입력 레벨과 싱크를 다시 확인하세요.",
                "Devices changed. Recheck input level and sync."));
    }

    void section(int index, View section) { sections[index] = section; }
    void next(int step) {
        a.add(a.button(a.tr("다음 단계 →", "Next step →"), () -> focus(step)));
    }
    private void focus(int step) {
        View section = sections[step];
        if (section != null) {
            Rect bounds = new Rect(0, 0, section.getWidth(), section.getHeight());
            a.page.offsetDescendantRectToMyCoords(section, bounds);
            a.pageScroll.scrollTo(0, Math.max(0, bounds.top));
        }
    }

    void inputCheck() {
        a.note(a.tr("평소 크기로 말하거나 노래하고 레벨을 확인하세요. 이 화면을 열어도 마이크가 켜지지 않습니다.",
                "Speak or sing at your usual volume and check the level. Opening this page leaves the microphone unchanged."));
        inputStatus = a.text("", 16, MainActivity.LIME);
        a.add(inputStatus);
        mic = a.button("", a::requestMicToggle);
        a.add(mic);
        a.slider(a.tr("마이크 볼륨", "Microphone volume"), 0, 0, 2, v -> Math.round(v * 100) + "%");
        poll(NativeAudio.stats(), AudioService.active);
    }

    void protectionCheck() {
        protection = a.text(a.protectionStatus(), 14, MainActivity.TEXT);
        a.add(protection);
        a.add(a.button(a.tr("출력 및 하울링 보호 설정 ›", "Output & feedback protection ›"), a::monitoringOptions));
        a.note(a.tr("보정값은 장치별이 아닌 출력 모드별로 공유합니다. 장치를 바꾼 뒤 아래 청취 테스트로 확인하세요.",
                "Offsets are shared by output mode, not saved per device. Recheck with the listening test after changing devices."));
    }

    void poll(float[] stats, boolean running) {
        if (inputStatus != null) {
            float level = stats != null && stats.length > 3 ? stats[3] : 0;
            String state = inputState(level, running);
            String label = state.equals("off") ? a.tr("마이크 꺼짐 · 버튼을 눌러 확인", "Microphone off · use the button to check")
                    : state.equals("high") ? a.tr("입력이 큽니다 · 볼륨을 낮추거나 마이크를 멀리하세요", "Input is high · lower gain or move away")
                    : state.equals("quiet") ? a.tr("입력이 작습니다 · 마이크 연결과 거리를 확인하세요", "Input is quiet · check connection and distance")
                    : a.tr("입력 레벨 확인 중", "Checking input level");
            String db = running && Float.isFinite(level) && level > .00001f
                    ? String.format(Locale.US, " · %.0f dBFS", 20 * Math.log10(level)) : "";
            inputStatus.setText(label + db);
            inputStatus.setTextColor(state.equals("high") ? MainActivity.RED : MainActivity.LIME);
            mic.setText(running ? a.tr("마이크 끄기", "Turn microphone off") : a.tr("마이크 켜고 레벨 확인", "Turn microphone on to check level"));
        }
        if (protection != null) protection.setText(a.protectionStatus());
    }

    static String inputState(float level, boolean running) {
        if (!running) return "off";
        if (!Float.isFinite(level) || level < .01f) return "quiet";
        return level > .8f ? "high" : "normal";
    }

    static String routeKey(android.content.Context c) {
        return AudioRouting.selectedInput(c) + ":" + AudioRouting.selectedOutput(c) + ":" + AudioRouting.selected(c);
    }
}

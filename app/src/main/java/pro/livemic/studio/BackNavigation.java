package pro.livemic.studio;

import android.os.Bundle;
import java.util.ArrayList;

/** Page visits only; rendering and transient windows never add history. */
final class BackNavigation {
    static final class Page {
        final int tab, source, filter, scroll;
        Page(int tab, int source, int filter, int scroll) {
            this.tab = tab; this.source = source; this.filter = filter; this.scroll = scroll;
        }
        boolean sameDestination(Page other) {
            return tab == other.tab && (tab != 1 || source == other.source)
                    && (tab != 4 || filter == other.filter);
        }
        Bundle save() {
            Bundle b = new Bundle(); b.putInt("tab", tab); b.putInt("source", source);
            b.putInt("filter", filter); b.putInt("scroll", scroll); return b;
        }
        static Page restore(Bundle b) {
            return new Page(Math.max(0, Math.min(9, b.getInt("tab"))),
                    Math.max(0, Math.min(2, b.getInt("source", 1))),
                    Math.max(0, Math.min(1, b.getInt("filter"))), Math.max(0, b.getInt("scroll")));
        }
    }
    final ArrayList<Page> history = new ArrayList<>();
    void visit(Page from, Page to) {
        if (from != null && !from.sameDestination(to)) {
            if (history.size() == 128) history.remove(0);
            history.add(from);
        }
    }
    Page previous() { return history.isEmpty() ? null : history.get(history.size() - 1); }
    void pop() { if (!history.isEmpty()) history.remove(history.size() - 1); }
    void save(Bundle out, Page current) {
        out.putBundle("navigationPage", current.save());
        ArrayList<Bundle> saved = new ArrayList<>();
        for (Page page : history) saved.add(page.save());
        out.putParcelableArrayList("navigationHistory", saved);
    }
    Page restore(Bundle state) {
        history.clear();
        if (state == null || state.getBundle("navigationPage") == null) return null;
        ArrayList<Bundle> saved = state.getParcelableArrayList("navigationHistory");
        if (saved != null) for (Bundle b : saved) if (b != null) history.add(Page.restore(b));
        return Page.restore(state.getBundle("navigationPage"));
    }
}

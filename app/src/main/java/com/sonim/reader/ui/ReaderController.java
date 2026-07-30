package com.sonim.reader.ui;

import com.sonim.reader.core.Bookmarks;
import com.sonim.reader.core.EncodingDetector;
import com.sonim.reader.core.FoldingModel;
import com.sonim.reader.core.IndexMap;
import com.sonim.reader.core.InMemoryLineProvider;
import com.sonim.reader.core.LineProvider;
import com.sonim.reader.core.OrgLineProvider;
import com.sonim.reader.core.OrgOutline;
import com.sonim.reader.core.ReaderSettings;
import com.sonim.reader.core.RtlDetector;
import com.sonim.reader.core.SearchService;
import com.sonim.reader.core.StreamOpener;
import com.sonim.reader.core.TextLoader;
import com.sonim.reader.core.TextWriter;
import com.sonim.reader.data.BookmarkRepository;
import com.sonim.reader.data.ReadingStateStore;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Owns all reader behaviour. The Activity forwards key events here and renders
 * what it is told through {@link ReaderView}.
 *
 * <p>Positions travel in two forms. Everything the user cares about keeping
 * &mdash; bookmarks, saved place, search &mdash; is stored as a real <b>file
 * line</b> (called "source" here). What is on screen is a <b>display line</b>,
 * which shifts when Org sections are closed. {@link #map} converts between them,
 * so folding never corrupts a bookmark or a saved place. For plain text the two
 * are the same, so {@code map} is {@link IndexMap#IDENTITY}.
 */
public final class ReaderController {

    private static final long AUTO_SCROLL_INTERVAL_MS = 1400;

    private final ReaderView view;
    private final BookmarkRepository bookmarks;
    private final ReadingStateStore state;
    private final Executor background;
    private final MainThread main;
    private final ReaderSettings settings = new ReaderSettings();

    private LineProvider rawLines;   // the whole file, real line numbers
    private LineProvider lines;      // what is shown (folded view in Org mode)
    private IndexMap map = IndexMap.IDENTITY;
    private StreamOpener opener;
    private DocumentSaver saver;
    private String fileKey;

    private boolean editing = false;
    private String savedText = "";   // editor text as last written, for dirty checks
    private int editReturnSource = 0; // reading position to restore on leaving the editor

    private boolean orgMode = false;
    private OrgOutline outline;
    private FoldingModel folding;

    private String currentQuery = "";
    private int lastMatchSource = SearchService.NOT_FOUND;

    private boolean autoScrolling = false;
    private final Runnable autoScrollTick = new Runnable() {
        @Override
        public void run() {
            if (!autoScrolling || lines == null) return;
            int next = view.getTopLine() + 1;
            if (next >= lines.size() - 1) {
                stopAutoScroll();
                return;
            }
            view.scrollToLine(next);
            main.postDelayed(this, AUTO_SCROLL_INTERVAL_MS);
        }
    };

    public ReaderController(ReaderView view,
                            BookmarkRepository bookmarks,
                            ReadingStateStore state,
                            Executor background,
                            MainThread main) {
        this.view = view;
        this.bookmarks = bookmarks;
        this.state = state;
        this.background = background;
        this.main = main;
    }

    public ReaderSettings settings() {
        return settings;
    }

    public boolean hasDocument() {
        return rawLines != null;
    }

    public boolean isOrgMode() {
        return orgMode;
    }

    public boolean isEditing() {
        return editing;
    }

    // ---------------------------------------------------------------- loading

    public void load(String fileKey, StreamOpener opener, DocumentSaver saver) {
        this.fileKey = fileKey;
        this.opener = opener;
        this.saver = saver;
        background.execute(() -> {
            try {
                int rememberedEnc = state.getEncodingIndex(fileKey, -1);
                LineProvider loaded;
                if (rememberedEnc >= 0) {
                    settings.setEncodingIndex(rememberedEnc);
                    loaded = TextLoader.load(opener, settings.getCharset());
                } else {
                    Charset detected = EncodingDetector.detect(TextLoader.readSample(opener));
                    int idx = EncodingDetector.CYCLE.indexOf(detected);
                    settings.setEncodingIndex(Math.max(0, idx));
                    loaded = TextLoader.load(opener, idx >= 0 ? settings.getCharset() : detected);
                }
                final boolean rtl = state.getRtl(fileKey, RtlDetector.isProbablyRtl(loaded));
                final int savedPos = state.getPosition(fileKey, 0);
                final LineProvider raw = loaded;
                main.post(() -> {
                    settings.setRtl(rtl);
                    buildAndShow(raw, savedPos, true);
                    state.addRecentFile(fileKey);
                });
            } catch (Exception e) {
                main.post(() -> view.showStatus("Cannot open file"));
            }
        });
    }

    /** Builds the shown view (Org or plain) from a freshly loaded document. */
    private void buildAndShow(LineProvider raw, int sourcePos, boolean announce) {
        rawLines = raw;
        outline = isOrgFile() ? OrgOutline.parse(raw) : null;
        orgMode = outline != null && outline.hasHeadings();
        if (orgMode) {
            folding = new FoldingModel(outline);
            applyOrgView();
        } else {
            lines = raw;
            map = IndexMap.IDENTITY;
            view.showDocument(raw, settings);
        }
        goToSource(clampSource(sourcePos));
        if (announce && orgMode) {
            view.showStatus("Org: * fold   < > titles   Menu contents");
        }
    }

    /** Rebuilds the folded view from the current fold state and shows it. */
    private void applyOrgView() {
        List<Integer> visible = folding.visibleLines();
        OrgLineProvider provider = new OrgLineProvider(rawLines, visible, folding.closedHeadingLines());
        lines = provider;
        map = provider;
        view.showDocument(provider, settings);
    }

    private boolean isOrgFile() {
        return fileKey != null && fileKey.toLowerCase(Locale.ROOT).endsWith(".org");
    }

    private int clampSource(int source) {
        if (rawLines == null || rawLines.size() == 0) return 0;
        return Math.max(0, Math.min(source, rawLines.size() - 1));
    }

    // --------------------------------------------------- position translation

    private int currentSource() {
        return map.toSource(view.getTopLine());
    }

    /** Reveal a file line (opening any closed sections around it) and scroll to it. */
    private void goToSource(int source) {
        if (orgMode) {
            if (folding.reveal(source)) applyOrgView();
            view.scrollToLine(map.toDisplay(source));
        } else {
            view.scrollToLine(source);
        }
    }

    // ------------------------------------------------------------ appearance

    public void increaseFont() {
        settings.increaseFont();
        view.refreshAppearance();
    }

    public void decreaseFont() {
        settings.decreaseFont();
        view.refreshAppearance();
    }

    public void toggleNightMode() {
        settings.toggleNightMode();
        view.refreshAppearance();
    }

    public void toggleRtl() {
        settings.toggleRtl();
        if (fileKey != null) state.saveRtl(fileKey, settings.isRtl());
        view.refreshAppearance();
        view.showStatus(settings.isRtl() ? "RTL" : "LTR");
    }

    public void cycleEncoding() {
        settings.cycleEncoding();
        if (fileKey != null) state.saveEncodingIndex(fileKey, settings.getEncodingIndex());
        if (opener == null) return;
        final int keepSource = currentSource();
        final Charset charset = settings.getCharset();
        background.execute(() -> {
            try {
                LineProvider reloaded = TextLoader.load(opener, charset);
                main.post(() -> {
                    buildAndShow(reloaded, keepSource, false);
                    view.showStatus(settings.getEncodingName());
                });
            } catch (Exception e) {
                main.post(() -> view.showStatus("Reload failed"));
            }
        });
    }

    // ----------------------------------------------------------------- paging

    public void pageDown() {
        stopAutoScroll();
        view.pageDown();
    }

    public void pageUp() {
        stopAutoScroll();
        view.pageUp();
    }

    // ------------------------------------------------------------ Org: folding

    public void toggleFoldCurrent() {
        if (!orgMode) return;
        int section = outline.sectionOf(currentSource());
        if (section < 0) {
            view.showStatus("No section here");
            return;
        }
        int headingLine = outline.headings().get(section).line;
        folding.toggle(section);
        applyOrgView();
        view.scrollToLine(map.toDisplay(headingLine));
        view.showStatus(folding.isClosed(section) ? "Section closed" : "Section open");
    }

    public void toggleFoldAll() {
        if (!orgMode) return;
        int keepSource = currentSource();
        boolean closing = folding.anyOpen();
        if (closing) folding.closeAll();
        else folding.openAll();
        applyOrgView();
        view.scrollToLine(map.toDisplay(keepSource));
        view.showStatus(closing ? "All closed" : "All open");
    }

    // --------------------------------------------------------- Org: navigation

    public void nextHeading() {
        if (!orgMode) return;
        int line = outline.nextHeadingLine(currentSource());
        if (line < 0) {
            view.showStatus("Last section");
            return;
        }
        goToSource(line);
        announceHeading(line);
    }

    public void previousHeading() {
        if (!orgMode) return;
        int line = outline.previousHeadingLine(currentSource());
        if (line < 0) {
            view.showStatus("First section");
            return;
        }
        goToSource(line);
        announceHeading(line);
    }

    private void announceHeading(int line) {
        int index = outline.headingIndexAtLine(line);
        if (index >= 0) {
            String title = outline.headings().get(index).title;
            view.showStatus(title.isEmpty() ? "(untitled)" : title);
        }
    }

    public void openContents() {
        if (!orgMode) return;
        view.showContents(outline.headings(), this::goToSource);
    }

    // ----------------------------------------------------------------- search

    public void toggleSearch() {
        view.setSearchVisible(!view.isSearchVisible());
    }

    public void performSearch(String query) {
        if (rawLines == null) return;
        currentQuery = query == null ? "" : query;
        lastMatchSource = SearchService.findNext(rawLines, currentQuery, currentSource());
        afterSearch();
    }

    public void searchNext() {
        if (rawLines == null || currentQuery.isEmpty()) return;
        lastMatchSource = SearchService.findNext(rawLines, currentQuery, lastMatchSource + 1);
        afterSearch();
    }

    public void searchPrevious() {
        if (rawLines == null || currentQuery.isEmpty()) return;
        int from = lastMatchSource == SearchService.NOT_FOUND ? currentSource() : lastMatchSource;
        lastMatchSource = SearchService.findPrevious(rawLines, currentQuery, from);
        afterSearch();
    }

    private void afterSearch() {
        if (lastMatchSource != SearchService.NOT_FOUND) {
            goToSource(lastMatchSource);
            view.showStatus("Match (" + SearchService.count(rawLines, currentQuery) + ")");
        } else {
            view.showStatus("Not found");
        }
    }

    // -------------------------------------------------------------- bookmarks

    public void gotoBookmark(int slot) {
        if (!Bookmarks.isValidSlot(slot) || fileKey == null) return;
        List<Integer> slotLines = bookmarks.get(fileKey, slot);
        int target = Bookmarks.nextAfter(slotLines, currentSource());
        if (target != Bookmarks.NONE) {
            goToSource(target);
            view.showStatus("Bookmark " + slot);
        } else {
            view.showStatus("Slot " + slot + " empty");
        }
    }

    public void saveBookmark(int slot) {
        if (!Bookmarks.isValidSlot(slot) || fileKey == null) return;
        bookmarks.add(fileKey, slot, currentSource());
        view.showStatus("Saved slot " + slot);
    }

    // -------------------------------------------------------- jump / hud / etc

    public void promptJumpToPercentage() {
        view.promptForPercentage(percent -> {
            if (rawLines != null) goToSource(rawLines.lineForPercentage(percent));
        });
    }

    public void toggleHud() {
        if (view.isHudVisible()) {
            view.setHudVisible(false, null);
        } else {
            view.setHudVisible(true, buildHudText());
        }
    }

    private String buildHudText() {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        int battery = view.getBatteryPercent();
        int progress = rawLines == null ? 0 : rawLines.percentageForLine(currentSource());
        String batteryText = battery >= 0 ? battery + "%" : "--";
        return time + " | " + batteryText + " | " + progress + "%";
    }

    // ------------------------------------------------------------ auto-scroll

    public void toggleAutoScroll() {
        if (autoScrolling) {
            stopAutoScroll();
            view.showStatus("Auto-scroll off");
        } else if (lines != null) {
            autoScrolling = true;
            main.postDelayed(autoScrollTick, AUTO_SCROLL_INTERVAL_MS);
            view.showStatus("Auto-scroll on");
        }
    }

    private void stopAutoScroll() {
        if (autoScrolling) {
            autoScrolling = false;
            main.cancel(autoScrollTick);
        }
    }

    // ---------------------------------------------------------------- editing

    /**
     * Long-press OK. Editing is a deliberate act &mdash; we confirm first so a
     * stray key press on a pocket keypad can never drop the reader into the
     * editor. {@link #enterEditMode()} runs only once the user says yes.
     */
    public void requestEditMode() {
        if (editing || rawLines == null || saver == null) return;
        view.confirmEnterEdit(this::enterEditMode);
    }

    private void enterEditMode() {
        if (rawLines == null) return;
        stopAutoScroll();
        if (view.isSearchVisible()) view.setSearchVisible(false);
        if (view.isHudVisible()) view.setHudVisible(false, null);
        // Always edit the real file (never the folded Org view).
        editReturnSource = currentSource();
        String text = TextWriter.serialize(rawTextLines());
        savedText = text;
        editing = true;
        view.showEditor(text, caretOffsetForSource(editReturnSource));
    }

    private List<String> rawTextLines() {
        List<String> out = new ArrayList<>(rawLines.size());
        for (int i = 0; i < rawLines.size(); i++) out.add(rawLines.getLine(i));
        return out;
    }

    /** Character offset of the start of file line {@code source} in the joined text. */
    private int caretOffsetForSource(int source) {
        int offset = 0;
        for (int i = 0; i < source && i < rawLines.size(); i++) {
            offset += rawLines.getLine(i).length() + 1; // + the joining '\n'
        }
        return offset;
    }

    /** OK inside the editor: insert a newline at the caret. */
    public void insertNewlineInEditor() {
        if (editing) view.insertAtCursor("\n");
    }

    /** Menu inside the editor: overwrite the file in place and stay in the editor. */
    public void saveEditsFromEditor() {
        if (editing) saveEdits(view.getEditorText(), null);
    }

    private void saveEdits(String text, Runnable onSaved) {
        if (!editing || saver == null) {
            view.showStatus("Cannot save");
            return;
        }
        final List<String> newLines = TextWriter.split(text);
        final Charset charset = settings.getCharset();
        view.showStatus("Saving…");
        background.execute(() -> {
            try {
                saver.save(newLines, charset);
                main.post(() -> {
                    savedText = text;
                    // Reflect the edits in the reader once we leave the editor.
                    rawLines = new InMemoryLineProvider(newLines);
                    view.showStatus("Saved");
                    if (onSaved != null) onSaved.run();
                });
            } catch (Exception e) {
                // Stay in the editor on failure so nothing typed is lost.
                main.post(() -> view.showStatus("Save failed"));
            }
        });
    }

    /** Back inside the editor: leave, offering to save first if there are changes. */
    public void exitEditRequested() {
        if (!editing) return;
        final String current = view.getEditorText();
        if (current != null && !current.equals(savedText)) {
            view.confirmSaveOrDiscard(new ReaderView.SaveChoiceCallback() {
                @Override public void onSave() { saveEdits(current, ReaderController.this::leaveEditor); }
                @Override public void onDiscard() { leaveEditor(); }
                @Override public void onCancel() { /* stay in the editor */ }
            });
        } else {
            leaveEditor();
        }
    }

    private void leaveEditor() {
        editing = false;
        view.hideEditor();
        // Rebuild the reader (re-parsing Org headings) from the possibly-edited text.
        buildAndShow(rawLines, clampSource(editReturnSource), false);
    }

    // -------------------------------------------------------------- lifecycle

    public void onBackPressed() {
        if (view.isSearchVisible()) {
            view.setSearchVisible(false);
        } else {
            view.confirmExit();
        }
    }

    public void savePosition() {
        stopAutoScroll();
        if (fileKey != null && rawLines != null) {
            state.savePosition(fileKey, currentSource());
        }
    }
}

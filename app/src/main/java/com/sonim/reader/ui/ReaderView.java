package com.sonim.reader.ui;

import com.sonim.reader.core.LineProvider;
import com.sonim.reader.core.OrgHeading;
import com.sonim.reader.core.ReaderSettings;

import java.util.List;

/**
 * Everything the {@link ReaderController} needs from its host screen, expressed
 * as an interface so the controller has no dependency on {@code Activity},
 * views, or the Android lifecycle. The Activity is a thin implementation of
 * this; the controller holds the logic.
 */
public interface ReaderView {

    /** Bind a freshly loaded document. */
    void showDocument(LineProvider lines, ReaderSettings settings);

    /**
     * Re-apply appearance (font, theme, direction) to the already-bound
     * document <em>without</em> losing the current scroll position.
     */
    void refreshAppearance();

    /** Current top visible line index. */
    int getTopLine();

    /** Scroll so {@code line} is the top visible line. */
    void scrollToLine(int line);

    /** Scroll down by roughly one screen. */
    void pageDown();

    /** Scroll up by roughly one screen. */
    void pageUp();

    /** Battery charge 0-100, or -1 if unknown. Used to build the HUD text. */
    int getBatteryPercent();

    /** Transient centre-screen status message (e.g. "Bookmark 3", "Not found"). */
    void showStatus(String message);

    /** Show/hide the search input row. */
    void setSearchVisible(boolean visible);

    boolean isSearchVisible();

    /** Current text in the search box. */
    String getSearchQuery();

    /** Show/hide the time/battery/progress HUD; {@code text} is ignored when hiding. */
    void setHudVisible(boolean visible, String text);

    boolean isHudVisible();

    /** Prompt for a 0-100 percentage; invokes {@code onValue} with the parsed number. */
    void promptForPercentage(PercentageCallback onValue);

    /** Ask the user to confirm leaving the reader. */
    void confirmExit();

    /**
     * Show a pick-list of every Org title (indented by depth). When the user
     * chooses one, {@code onPick} is called with that title's file line.
     */
    void showContents(List<OrgHeading> headings, ContentsCallback onPick);

    // ------------------------------------------------------------------ editing

    /**
     * Enter the full-document editor, pre-filled with {@code text} and with the
     * caret placed at {@code caretOffset}. Text entry, caret movement and
     * scrolling are handled natively by the underlying editable field.
     */
    void showEditor(String text, int caretOffset);

    /** Leave the editor and return focus to the reading list. */
    void hideEditor();

    /** The current contents of the editor. */
    String getEditorText();

    /** Insert {@code s} at the caret, replacing any selection (used for newlines). */
    void insertAtCursor(String s);

    /** Confirm the deliberate step of entering edit mode before opening the editor. */
    void confirmEnterEdit(Runnable onConfirm);

    /** On leaving the editor with unsaved changes, offer Save / Discard / Cancel. */
    void confirmSaveOrDiscard(SaveChoiceCallback callback);

    interface PercentageCallback {
        void onPercentage(int percent);
    }

    interface ContentsCallback {
        void onPick(int sourceLine);
    }

    interface SaveChoiceCallback {
        void onSave();

        void onDiscard();

        void onCancel();
    }
}

package com.sonim.reader.ui;

import android.view.KeyEvent;

/**
 * Single translation point from XP5s physical keys to {@link ReaderController}
 * actions. New bindings are added here without touching the Activity or the
 * controller.
 *
 * <p>Plain-text key map:
 * <pre>
 *   Volume +/-      font larger / smaller
 *   OK / Center tap toggle search box
 *   OK / Center hold enter the editor (after a confirm prompt)
 *   D-pad up/down   scroll line by line (handled by the list itself)
 *   D-pad left/rt   page up / down   (in search: previous / next match)
 *   5               toggle night mode
 *   #               cycle text encoding
 *   *               toggle time/battery/progress HUD
 *   Menu            toggle auto-scroll
 *   1-9 tap         jump to next bookmark in that slot
 *   1-9 long-press  save a bookmark to that slot
 *   0 tap           jump to percentage
 *   0 long-press    toggle reading direction (RTL/LTR)
 * </pre>
 *
 * <p>Extra bindings that switch on ONLY for .org files (everything else is
 * unchanged):
 * <pre>
 *   D-pad left/rt   previous / next title
 *   * tap           close / open the current section
 *   * long-press    close all / open all sections
 *   Menu tap        open the Contents list
 *   Menu long-press time/battery/progress HUD
 * </pre>
 *
 * <p>While the editor is open, key handling is entirely different &mdash; see
 * {@link #onEditKey(KeyEvent)}. Character entry and caret movement are left to
 * the native editable field; only the control keys are intercepted here.
 */
public final class KeyCommandRouter {

    private final ReaderController controller;
    private final ReaderView view;

    public KeyCommandRouter(ReaderController controller, ReaderView view) {
        this.controller = controller;
        this.view = view;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Number keys, OK/Center, and (in Org mode) * and Menu, need
        // tap-vs-long-press, so they start tracking and act on key-up /
        // long-press instead.
        if (isNumberKey(keyCode)) {
            event.startTracking();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            event.startTracking();
            return true;
        }
        if (controller.isOrgMode()
                && (keyCode == KeyEvent.KEYCODE_STAR || keyCode == KeyEvent.KEYCODE_MENU)) {
            event.startTracking();
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                controller.increaseFont();
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                controller.decreaseFont();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (view.isSearchVisible()) controller.searchNext();
                else if (controller.isOrgMode()) controller.nextHeading();
                else controller.pageDown();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (view.isSearchVisible()) controller.searchPrevious();
                else if (controller.isOrgMode()) controller.previousHeading();
                else controller.pageUp();
                return true;
            case KeyEvent.KEYCODE_5:
                controller.toggleNightMode();
                return true;
            case KeyEvent.KEYCODE_POUND:
                controller.cycleEncoding();
                return true;
            case KeyEvent.KEYCODE_STAR:
                controller.toggleHud(); // plain-text only (Org handled above)
                return true;
            case KeyEvent.KEYCODE_MENU:
                controller.toggleAutoScroll(); // plain-text only (Org handled above)
                return true;
            default:
                return false;
        }
    }

    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            controller.requestEditMode();
            return true;
        }
        if (isSlotKey(keyCode)) {
            controller.saveBookmark(slotOf(keyCode));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_0) {
            controller.toggleRtl();
            return true;
        }
        if (controller.isOrgMode()) {
            if (keyCode == KeyEvent.KEYCODE_STAR) {
                controller.toggleFoldAll();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                controller.toggleHud();
                return true;
            }
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // A long-press already handled this key; swallow the trailing up event.
        boolean deferred = isNumberKey(keyCode)
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || (controller.isOrgMode()
                    && (keyCode == KeyEvent.KEYCODE_STAR || keyCode == KeyEvent.KEYCODE_MENU));
        if (deferred && event.isCanceled()) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            controller.toggleSearch();
            return true;
        }
        if (isSlotKey(keyCode)) {
            controller.gotoBookmark(slotOf(keyCode));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_0) {
            controller.promptJumpToPercentage();
            return true;
        }
        if (controller.isOrgMode()) {
            if (keyCode == KeyEvent.KEYCODE_STAR) {
                controller.toggleFoldCurrent();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                controller.openContents();
                return true;
            }
        }
        return false;
    }

    /**
     * The editor's own key map, consulted only while the editor is open. Returns
     * {@code true} for the three control keys it owns and {@code false} for
     * everything else, so ordinary typing and D-pad caret movement flow straight
     * through to the native editable field.
     *
     * <pre>
     *   Back    leave the editor (prompts to save if there are changes)
     *   Menu    save (overwrite the file in place)
     *   OK      insert a newline at the caret
     * </pre>
     */
    public boolean onEditKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (up) controller.exitEditRequested();
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (up) controller.saveEditsFromEditor();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!up && event.getRepeatCount() == 0) controller.insertNewlineInEditor();
                return true;
            default:
                return false;
        }
    }

    private static boolean isNumberKey(int keyCode) {
        return keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9;
    }

    private static boolean isSlotKey(int keyCode) {
        return keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9;
    }

    private static int slotOf(int keyCode) {
        return keyCode - KeyEvent.KEYCODE_1 + 1;
    }
}

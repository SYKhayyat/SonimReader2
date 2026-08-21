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
 *   # tap           cycle text encoding
 *   # long-press    toggle night mode
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
 * <h3>Why night mode is on {@code #} and not on {@code 5}</h3>
 *
 * <p>It was written as {@code case KEYCODE_5} in {@link #onKeyDown}, below the
 * guard that starts tracking every number key &mdash; and that guard returns, so
 * the case was unreachable from the day it was written. Night mode defaults to
 * <em>on</em>, so the practical effect was that it could never be turned off,
 * which is the worse direction for a reader used in daylight. Nothing caught it:
 * the compiler does not warn about an unreachable {@code case} reached through a
 * guard clause, and there was no test on this class.
 *
 * <p>Moving the call above the guard could not fix it on its own, because
 * {@code 5} was never free. {@code 1}-{@code 9} are the nine bookmark slots, so
 * {@code 5} taken for night mode is a slot that can be saved to and never jumped
 * to. Two rows of the key map claimed one key and one of them had to give.
 *
 * <p>{@code #} long-press is the binding with no other claim on it in either
 * mode &mdash; {@code *} long-press is fold-all in Org files and Menu long-press
 * is the HUD there &mdash; so it is the one place a new binding fits without
 * displacing something. The cost is that {@code #} now cycles the encoding on
 * key-<em>up</em> rather than key-down, which is what every other tap/hold key
 * here already does.
 *
 * <h3>Why the decisions are also reachable without a {@link KeyEvent}</h3>
 *
 * <p>Every public entry point below delegates to a package-private overload
 * taking only primitives. {@code KeyEvent} cannot be constructed in a JVM unit
 * test without a mocking framework or an instrumented device, which is the
 * practical reason this class had no test and therefore kept an unreachable
 * binding for its whole life. The overloads carry the routing; the public
 * methods only read the two facts a {@code KeyEvent} holds that matter here
 * &mdash; that a key-up was cancelled, and the editor's action and repeat count.
 */
public final class KeyCommandRouter {

    private final ReaderController controller;
    private final ReaderView view;

    public KeyCommandRouter(ReaderController controller, ReaderView view) {
        this.controller = controller;
        this.view = view;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Number keys, OK/Center, #, and (in Org mode) * and Menu, need
        // tap-vs-long-press, so they start tracking and act on key-up /
        // long-press instead.
        if (isDeferred(keyCode)) {
            event.startTracking();
            return true;
        }
        return onImmediateKeyDown(keyCode);
    }

    /** The key-down half for keys that act at once, with no tap/hold split. */
    boolean onImmediateKeyDown(int keyCode) {
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
        return onKeyLongPress(keyCode);
    }

    boolean onKeyLongPress(int keyCode) {
        if (isConfirmKey(keyCode)) {
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
        if (keyCode == KeyEvent.KEYCODE_POUND) {
            controller.toggleNightMode();
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
        return onKeyUp(keyCode, event.isCanceled());
    }

    boolean onKeyUp(int keyCode, boolean canceled) {
        // A long-press already handled this key; swallow the trailing up event.
        if (canceled && isDeferred(keyCode)) {
            return true;
        }
        if (isConfirmKey(keyCode)) {
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
        if (keyCode == KeyEvent.KEYCODE_POUND) {
            controller.cycleEncoding();
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
        return onEditKey(event.getKeyCode(),
                event.getAction() == KeyEvent.ACTION_UP,
                event.getRepeatCount());
    }

    boolean onEditKey(int keyCode, boolean up, int repeatCount) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (up) controller.exitEditRequested();
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (up) controller.saveEditsFromEditor();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!up && repeatCount == 0) controller.insertNewlineInEditor();
                return true;
            default:
                return false;
        }
    }

    /**
     * Keys whose meaning depends on tap versus long-press, so key-down only
     * starts tracking and the action happens in {@link #onKeyUp} or
     * {@link #onKeyLongPress}.
     *
     * <p>One definition, asked by both. This was written out twice &mdash; once
     * as a run of guards in {@code onKeyDown} and once as a local called
     * {@code deferred} in {@code onKeyUp} &mdash; so adding a tap/hold key meant
     * editing two places, and the second is the one that decides whether a long
     * press's trailing up event is swallowed. Editing only the first would have
     * fired the tap action immediately after every hold.
     */
    boolean isDeferred(int keyCode) {
        return isNumberKey(keyCode)
                || isConfirmKey(keyCode)
                || keyCode == KeyEvent.KEYCODE_POUND
                || (controller.isOrgMode()
                    && (keyCode == KeyEvent.KEYCODE_STAR || keyCode == KeyEvent.KEYCODE_MENU));
    }

    /**
     * The centre key, under both spellings.
     *
     * <p>This handset's {@code soc_matrix_keypad_0.kl} maps it to
     * {@code key 352 ENTER} rather than to {@code DPAD_CENTER}, and other
     * hardware does the reverse, so every site that handles one handles both.
     */
    public static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
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

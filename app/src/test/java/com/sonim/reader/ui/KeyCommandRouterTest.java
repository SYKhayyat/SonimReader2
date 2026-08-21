package com.sonim.reader.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import com.sonim.reader.core.LineProvider;
import com.sonim.reader.core.OrgHeading;
import com.sonim.reader.core.ReaderSettings;
import com.sonim.reader.data.BookmarkRepository;
import com.sonim.reader.data.ReadingStateStore;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The first test this class has ever had, and it exists because of what its
 * absence cost.
 *
 * <p>{@code case KEYCODE_5} sat in {@code onKeyDown} below a guard clause that
 * returns for every number key, so night mode was unreachable from the day it
 * was written. The compiler does not warn about an unreachable {@code case}
 * reached through a guard, and the {@code ui} package had no tests, so the only
 * thing that could have found it was pressing {@code 5} on a handset and
 * noticing that a dark screen stayed dark.
 *
 * <p>Every assertion below is about a key <em>reaching</em> an action. That is
 * the failure this class is prone to, and it is not the failure a test of the
 * action itself would catch.
 *
 * <p>{@link KeyCommandRouter} exposes primitive-only overloads so this runs on
 * the JVM with no emulator and no mocking framework: {@code KeyEvent} cannot be
 * constructed here, but the two facts the router reads from one &mdash; whether
 * a key-up was cancelled, and the editor's action and repeat count &mdash; are
 * plain arguments.
 */
public class KeyCommandRouterTest {

    private RecordingView view;
    private ReaderController controller;
    private KeyCommandRouter router;

    @Before
    public void setUp() {
        view = new RecordingView();
        controller = new ReaderController(
                view,
                new FakeBookmarks(),
                new FakeState(),
                new SameThreadExecutor(),
                new NoopMainThread());
        router = new KeyCommandRouter(controller, view);
    }

    // ------------------------------------------------------------- night mode

    @Test
    public void nightModeIsReachable() {
        boolean before = controller.settings().isNightMode();

        assertTrue("# long-press must be handled", router.onKeyLongPress(KeyEvent.KEYCODE_POUND));

        assertNotEquals("# long-press must toggle night mode",
                before, controller.settings().isNightMode());
    }

    @Test
    public void nightModeTogglesBackAgain() {
        boolean before = controller.settings().isNightMode();
        router.onKeyLongPress(KeyEvent.KEYCODE_POUND);
        router.onKeyLongPress(KeyEvent.KEYCODE_POUND);
        assertEquals(before, controller.settings().isNightMode());
    }

    /**
     * The regression. {@code 5} is one of the nine bookmark slots and nothing
     * else; a key map with two rows claiming it is a key map where one row is a
     * lie.
     */
    @Test
    public void fiveIsABookmarkSlotAndNeverNightMode() {
        boolean before = controller.settings().isNightMode();

        assertTrue("5 must defer, so tap and hold can differ",
                router.isDeferred(KeyEvent.KEYCODE_5));
        assertFalse("nothing may fire on 5's key-down",
                router.onImmediateKeyDown(KeyEvent.KEYCODE_5));

        router.onKeyUp(KeyEvent.KEYCODE_5, false);
        router.onKeyLongPress(KeyEvent.KEYCODE_5);

        assertEquals("5 must not touch night mode",
                before, controller.settings().isNightMode());
    }

    // --------------------------------------------------------------- encoding

    @Test
    public void hashTapCyclesTheEncoding() {
        int before = controller.settings().getEncodingIndex();
        assertTrue(router.onKeyUp(KeyEvent.KEYCODE_POUND, false));
        assertNotEquals(before, controller.settings().getEncodingIndex());
    }

    /**
     * The half that is easy to leave out when a key gains a long press: Android
     * still delivers the up event, flagged cancelled, and without this the tap
     * action fires immediately after every hold.
     */
    @Test
    public void aCancelledUpAfterAHoldDoesNotAlsoFireTheTap() {
        int before = controller.settings().getEncodingIndex();
        router.onKeyLongPress(KeyEvent.KEYCODE_POUND);

        assertTrue("the trailing up must be swallowed",
                router.onKeyUp(KeyEvent.KEYCODE_POUND, true));

        assertEquals("a hold must not also cycle the encoding",
                before, controller.settings().getEncodingIndex());
    }

    // ------------------------------------------------------------- the centre key

    /**
     * This handset's {@code soc_matrix_keypad_0.kl} maps the centre key to
     * {@code ENTER}, not {@code DPAD_CENTER}. Both spellings have to work, and
     * the router got that right all along &mdash; what was wrong was one layer
     * up, in who saw the event first.
     */
    @Test
    public void bothSpellingsOfTheCentreKeyAreAccepted() {
        assertTrue(KeyCommandRouter.isConfirmKey(KeyEvent.KEYCODE_ENTER));
        assertTrue(KeyCommandRouter.isConfirmKey(KeyEvent.KEYCODE_DPAD_CENTER));
        assertFalse(KeyCommandRouter.isConfirmKey(KeyEvent.KEYCODE_SPACE));
    }

    @Test
    public void theCentreKeyTogglesSearchUnderBothSpellings() {
        assertFalse(view.searchVisible);
        router.onKeyUp(KeyEvent.KEYCODE_DPAD_CENTER, false);
        assertTrue("DPAD_CENTER must open the search box", view.searchVisible);

        router.onKeyUp(KeyEvent.KEYCODE_ENTER, false);
        assertFalse("ENTER must close it again", view.searchVisible);
    }

    // ------------------------------------------------------- the tap/hold set

    /**
     * One definition of "this key defers", asked by key-down and by key-up.
     * They used to be two, and only the second decides whether a hold's trailing
     * up event is swallowed.
     */
    @Test
    public void everyTapHoldKeyDefersAndActsOnNothingAtKeyDown() {
        List<Integer> deferred = new ArrayList<>();
        for (int k = KeyEvent.KEYCODE_0; k <= KeyEvent.KEYCODE_9; k++) deferred.add(k);
        Collections.addAll(deferred,
                KeyEvent.KEYCODE_POUND,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER);

        for (int keyCode : deferred) {
            assertTrue("key " + keyCode + " must defer", router.isDeferred(keyCode));
            assertFalse("key " + keyCode + " must do nothing at key-down",
                    router.onImmediateKeyDown(keyCode));
        }
    }

    @Test
    public void starAndMenuActAtOnceInPlainTextAndDoNotDefer() {
        assertFalse(controller.isOrgMode());
        assertFalse(router.isDeferred(KeyEvent.KEYCODE_STAR));
        assertFalse(router.isDeferred(KeyEvent.KEYCODE_MENU));

        assertTrue(router.onImmediateKeyDown(KeyEvent.KEYCODE_STAR));
        assertTrue(router.onImmediateKeyDown(KeyEvent.KEYCODE_MENU));
    }

    @Test
    public void volumeChangesTheFontAtKeyDown() {
        float before = controller.settings().getFontSize();
        assertTrue(router.onImmediateKeyDown(KeyEvent.KEYCODE_VOLUME_UP));
        assertTrue(controller.settings().getFontSize() > before);
        assertTrue(router.onImmediateKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN));
        assertEquals(before, controller.settings().getFontSize(), 0.001f);
    }

    @Test
    public void anUnboundKeyIsNotClaimed() {
        assertFalse(router.isDeferred(KeyEvent.KEYCODE_A));
        assertFalse(router.onImmediateKeyDown(KeyEvent.KEYCODE_A));
        assertFalse(router.onKeyUp(KeyEvent.KEYCODE_A, false));
        assertFalse(router.onKeyLongPress(KeyEvent.KEYCODE_A));
    }

    // ------------------------------------------------------------- the editor

    @Test
    public void theEditorOwnsOnlyItsThreeControlKeys() {
        assertTrue(router.onEditKey(KeyEvent.KEYCODE_BACK, true, 0));
        assertTrue(router.onEditKey(KeyEvent.KEYCODE_MENU, true, 0));
        assertTrue(router.onEditKey(KeyEvent.KEYCODE_DPAD_CENTER, false, 0));
        assertTrue(router.onEditKey(KeyEvent.KEYCODE_ENTER, false, 0));

        // Everything else falls through to the editable field, which is how the
        // T9 keypad types and the D-pad moves the caret.
        assertFalse(router.onEditKey(KeyEvent.KEYCODE_5, false, 0));
        assertFalse(router.onEditKey(KeyEvent.KEYCODE_POUND, false, 0));
        assertFalse(router.onEditKey(KeyEvent.KEYCODE_DPAD_UP, false, 0));
    }

    // ------------------------------------------------------------------ fakes

    private static final class SameThreadExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    /**
     * Deliberately does not run delayed work: auto-scroll re-posts itself, so a
     * fake that ran it at once would not return.
     */
    private static final class NoopMainThread implements MainThread {
        @Override public void post(Runnable action) { action.run(); }
        @Override public void postDelayed(Runnable action, long delayMillis) { }
        @Override public void cancel(Runnable action) { }
    }

    private static final class FakeBookmarks implements BookmarkRepository {
        @Override public void add(String fileKey, int slot, int line) { }
        @Override public List<Integer> get(String fileKey, int slot) { return new ArrayList<>(); }
        @Override public void clear(String fileKey) { }
    }

    private static final class FakeState implements ReadingStateStore {
        @Override public int getPosition(String fileKey, int fallback) { return fallback; }
        @Override public void savePosition(String fileKey, int line) { }
        @Override public int getEncodingIndex(String fileKey, int fallback) { return fallback; }
        @Override public void saveEncodingIndex(String fileKey, int index) { }
        @Override public boolean getRtl(String fileKey, boolean fallback) { return fallback; }
        @Override public void saveRtl(String fileKey, boolean rtl) { }
        @Override public List<String> getRecentFiles() { return new ArrayList<>(); }
        @Override public void addRecentFile(String uri) { }
    }

    private static final class RecordingView implements ReaderView {
        boolean searchVisible = false;
        boolean hudVisible = false;
        final List<String> statuses = new ArrayList<>();

        @Override public void showDocument(LineProvider lines, ReaderSettings settings) { }
        @Override public void refreshAppearance() { }
        @Override public int getTopLine() { return 0; }
        @Override public void scrollToLine(int line) { }
        @Override public void pageDown() { }
        @Override public void pageUp() { }
        @Override public int getBatteryPercent() { return -1; }
        @Override public void showStatus(String message) { statuses.add(message); }
        @Override public void setSearchVisible(boolean visible) { searchVisible = visible; }
        @Override public boolean isSearchVisible() { return searchVisible; }
        @Override public String getSearchQuery() { return ""; }
        @Override public void setHudVisible(boolean visible, String text) { hudVisible = visible; }
        @Override public boolean isHudVisible() { return hudVisible; }
        @Override public void promptForPercentage(PercentageCallback onValue) { }
        @Override public void confirmExit() { }
        @Override public void showContents(List<OrgHeading> headings, ContentsCallback onPick) { }
        @Override public void showEditor(String text, int caretOffset) { }
        @Override public void hideEditor() { }
        @Override public String getEditorText() { return ""; }
        @Override public void insertAtCursor(String s) { }
        @Override public void confirmEnterEdit(Runnable onConfirm) { onConfirm.run(); }
        @Override public void confirmSaveOrDiscard(SaveChoiceCallback callback) { callback.onCancel(); }
    }
}

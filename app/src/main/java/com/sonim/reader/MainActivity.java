package com.sonim.reader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.sonim.reader.core.LineProvider;
import com.sonim.reader.core.OrgHeading;
import com.sonim.reader.core.ReaderSettings;
import com.sonim.reader.data.PrefsBookmarkRepository;
import com.sonim.reader.data.PrefsReadingStateStore;
import com.sonim.reader.ui.AndroidMainThread;
import com.sonim.reader.ui.AtomicFileSaver;
import com.sonim.reader.ui.ContentStreamOpener;
import com.sonim.reader.ui.KeyCommandRouter;
import com.sonim.reader.ui.LineAdapter;
import com.sonim.reader.ui.ReaderController;
import com.sonim.reader.ui.ReaderView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin host screen. It builds the object graph, renders what
 * {@link ReaderController} tells it to, and forwards key events to
 * {@link KeyCommandRouter}. All reading behaviour lives in the controller.
 */
public class MainActivity extends Activity implements ReaderView {

    private static final int PICK_FILE_REQUEST = 1;
    private static final int STORAGE_PERMISSION_REQUEST = 2;

    private ListView listView;
    private LinearLayout searchContainer;
    private EditText searchInput;
    private LinearLayout hudOverlay;
    private TextView hudText;
    private TextView statusOverlay;
    private FrameLayout editContainer;
    private EditText editInput;

    private ReaderController controller;
    private KeyCommandRouter router;
    private ExecutorService background;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Reading with no touchscreen: never let the screen time out mid-page.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        listView = findViewById(R.id.text_list);
        searchContainer = findViewById(R.id.search_container);
        searchInput = findViewById(R.id.search_input);
        hudOverlay = findViewById(R.id.hud_overlay);
        hudText = findViewById(R.id.hud_text);
        statusOverlay = findViewById(R.id.status_overlay);
        editContainer = findViewById(R.id.edit_container);
        editInput = findViewById(R.id.edit_input);
        listView.setDivider(null);

        background = Executors.newSingleThreadExecutor();
        controller = new ReaderController(
                this,
                new PrefsBookmarkRepository(this),
                new PrefsReadingStateStore(this),
                background,
                new AndroidMainThread());
        router = new KeyCommandRouter(controller, this);

        setupSearchListener();
        ensureStoragePermissionThenPick();
    }

    private void setupSearchListener() {
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                controller.performSearch(searchInput.getText().toString());
                return true;
            }
            return false;
        });
    }

    // --------------------------------------------------------- permission flow

    private void ensureStoragePermissionThenPick() {
        // WRITE is requested up front alongside READ so saving edits later needs
        // no second prompt. READ is the gate for even opening a file.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST);
        } else {
            openFilePicker();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            // READ lets us read; WRITE only matters when saving, so a denied WRITE
            // still opens the reader (a later save just reports "Save failed").
            boolean readGranted = false;
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.READ_EXTERNAL_STORAGE.equals(permissions[i])
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    readGranted = true;
                }
            }
            if (readGranted) {
                openFilePicker();
            } else {
                showStatus("Storage permission required");
                finish();
            }
        }
    }

    private void openFilePicker() {
        startActivityForResult(new Intent(this, FilePickerActivity.class), PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            Uri uri = data.getData();
            controller.load(uri.toString(),
                    new ContentStreamOpener(this, uri),
                    new AtomicFileSaver(this, uri));
        } else if (!controller.hasDocument()) {
            finish();
        }
    }

    // ---------------------------------------------------------- key delegation

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // In the editor, the router owns only the control keys (Back/Menu/OK);
        // everything else falls through to the focused EditText so the T9 keypad
        // types and the D-pad moves the caret.
        if (controller.isEditing() && router.onEditKey(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (controller.isEditing()) return super.onKeyDown(keyCode, event);
        return router.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (controller.isEditing()) return super.onKeyUp(keyCode, event);
        return router.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (controller.isEditing()) return super.onKeyLongPress(keyCode, event);
        return router.onKeyLongPress(keyCode, event) || super.onKeyLongPress(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        controller.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        controller.savePosition();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (background != null) background.shutdownNow();
    }

    // ------------------------------------------------------------- ReaderView

    @Override
    public void showDocument(LineProvider lines, ReaderSettings settings) {
        listView.setAdapter(new LineAdapter(this, lines, settings));
        applyBackground(settings);
    }

    @Override
    public void refreshAppearance() {
        applyBackground(controller.settings());
        if (listView.getAdapter() instanceof LineAdapter) {
            ((LineAdapter) listView.getAdapter()).notifyDataSetChanged();
        }
    }

    private void applyBackground(ReaderSettings settings) {
        listView.setBackgroundColor(settings.isNightMode() ? Color.BLACK : Color.WHITE);
    }

    @Override
    public int getTopLine() {
        return listView.getFirstVisiblePosition();
    }

    @Override
    public void scrollToLine(int line) {
        listView.setSelection(line);
    }

    @Override
    public void pageDown() {
        int first = listView.getFirstVisiblePosition();
        int page = Math.max(1, listView.getLastVisiblePosition() - first);
        int count = listView.getAdapter() == null ? 0 : listView.getAdapter().getCount();
        listView.setSelection(Math.min(Math.max(0, count - 1), first + page));
    }

    @Override
    public void pageUp() {
        int first = listView.getFirstVisiblePosition();
        int page = Math.max(1, listView.getLastVisiblePosition() - first);
        listView.setSelection(Math.max(0, first - page));
    }

    @Override
    public int getBatteryPercent() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return -1;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) return -1;
        return level * 100 / scale;
    }

    @Override
    public void showStatus(String message) {
        statusOverlay.setText(message);
        statusOverlay.setVisibility(View.VISIBLE);
        statusHandler.removeCallbacksAndMessages(null);
        statusHandler.postDelayed(() -> statusOverlay.setVisibility(View.GONE), 1200);
    }

    @Override
    public void setSearchVisible(boolean visible) {
        searchContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            searchInput.requestFocus();
        } else {
            listView.requestFocus();
        }
    }

    @Override
    public boolean isSearchVisible() {
        return searchContainer.getVisibility() == View.VISIBLE;
    }

    @Override
    public String getSearchQuery() {
        return searchInput.getText().toString();
    }

    @Override
    public void setHudVisible(boolean visible, String text) {
        if (visible) {
            hudText.setText(text);
            hudOverlay.setVisibility(View.VISIBLE);
        } else {
            hudOverlay.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean isHudVisible() {
        return hudOverlay.getVisibility() == View.VISIBLE;
    }

    @Override
    public void promptForPercentage(PercentageCallback onValue) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle("Jump to % (0-100)")
                .setView(input)
                .setPositiveButton("Go", (dialog, which) -> {
                    try {
                        onValue.onPercentage(Integer.parseInt(input.getText().toString().trim()));
                    } catch (NumberFormatException e) {
                        showStatus("Invalid %");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void confirmExit() {
        new AlertDialog.Builder(this)
                .setMessage("Exit Reader?")
                .setPositiveButton("Yes", (d, w) -> finish())
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    public void showContents(List<OrgHeading> headings, ContentsCallback onPick) {
        if (headings.isEmpty()) {
            showStatus("No sections");
            return;
        }
        String[] labels = new String[headings.size()];
        for (int i = 0; i < headings.size(); i++) {
            OrgHeading h = headings.get(i);
            StringBuilder label = new StringBuilder();
            for (int indent = 1; indent < h.level; indent++) label.append("  ");
            label.append(h.title.isEmpty() ? "(untitled)" : h.title);
            labels[i] = label.toString();
        }
        new AlertDialog.Builder(this)
                .setTitle("Contents")
                .setItems(labels, (dialog, which) -> onPick.onPick(headings.get(which).line))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ editing

    @Override
    public void showEditor(String text, int caretOffset) {
        editInput.setText(text);
        int caret = Math.max(0, Math.min(caretOffset, editInput.getText().length()));
        editInput.setSelection(caret);
        editContainer.setVisibility(View.VISIBLE);
        editInput.requestFocus();
    }

    @Override
    public void hideEditor() {
        editContainer.setVisibility(View.GONE);
        listView.requestFocus();
    }

    @Override
    public String getEditorText() {
        return editInput.getText().toString();
    }

    @Override
    public void insertAtCursor(String s) {
        int start = Math.max(0, editInput.getSelectionStart());
        int end = Math.max(0, editInput.getSelectionEnd());
        editInput.getText().replace(Math.min(start, end), Math.max(start, end), s);
    }

    @Override
    public void confirmEnterEdit(Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle("Edit file?")
                .setMessage("Saving will overwrite the original file.")
                .setPositiveButton("Edit", (d, w) -> onConfirm.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void confirmSaveOrDiscard(SaveChoiceCallback callback) {
        new AlertDialog.Builder(this)
                .setTitle("Unsaved changes")
                .setMessage("Save before leaving?")
                .setPositiveButton("Save", (d, w) -> callback.onSave())
                .setNegativeButton("Discard", (d, w) -> callback.onDiscard())
                .setNeutralButton("Cancel", (d, w) -> callback.onCancel())
                .setOnCancelListener(d -> callback.onCancel())
                .show();
    }
}

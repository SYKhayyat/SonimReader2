package com.sonim.reader;

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Focus-based File Picker for Sonim XP5S.
 * Starts at /storage/ to allow access to Internal Storage and SD Card.
 */
public class FilePickerActivity extends ListActivity {

    private File currentDir;
    private List<File> filesInDir = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Starting at /storage/ ensures we see both Internal and SD card partitions
        currentDir = new File("/sdcard/");
        if (!currentDir.exists() || !currentDir.canRead()) {
            currentDir = new File("/"); // Absolute root fallback
        }

        refreshFiles();
    }

    private void refreshFiles() {
        setTitle(currentDir.getPath());
        filesInDir.clear();

        File[] files = currentDir.listFiles();
        List<String> displayNames = new ArrayList<>();

        // Add "Back" option if we aren't at the absolute root
        if (currentDir.getParentFile() != null) {
            displayNames.add(".. (Go Back)");
            filesInDir.add(currentDir.getParentFile());
        }

        if (files != null) {
            List<File> sortedFiles = new ArrayList<>();
            for (File f : files) {
                // We only care about Directories and readable text files (.txt / .org).
                String lower = f.getName().toLowerCase();
                boolean isText = lower.endsWith(".txt") || lower.endsWith(".org");
                if (f.isDirectory() || isText) {
                    // Filter out hidden folders/files
                    if (!f.getName().startsWith(".")) {
                        sortedFiles.add(f);
                    }
                }
            }

            // Sort so Folders are at the top, then files alphabetically
            Collections.sort(sortedFiles, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File f : sortedFiles) {
                filesInDir.add(f);
                displayNames.add(f.isDirectory() ? "[Folder] " + f.getName() : f.getName());
            }
        }

        setListAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayNames));
    }

    /**
     * Handles the DPAD_CENTER / ENTER click on a list item
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        File selected = filesInDir.get(position);

        if (selected.isDirectory()) {
            currentDir = selected;
            refreshFiles();
            // Reset selection to the top of the new folder
            getListView().setSelection(0);
        } else {
            // Return the file back to the Reader
            Intent result = new Intent();
            result.setData(Uri.fromFile(selected));
            setResult(RESULT_OK, result);
            finish();
        }
    }

    /**
     * Key listener to handle the physical Back button for folder navigation
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // If we are deep in folders, use Back to go up one level
            if (currentDir.getParentFile() != null && !currentDir.getPath().equals("/storage")) {
                currentDir = currentDir.getParentFile();
                refreshFiles();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
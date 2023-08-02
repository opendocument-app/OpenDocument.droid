/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.tomtasche.reader.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.view.ActionMode;
import at.tomtasche.reader.R;

// https://github.com/aosp-mirror/platform_frameworks_base/blob/a8b1b1a2e62bcca18f52ed31549c93d43728d152/core/java/android/webkit/FindActionModeCallback.java
public class FindActionModeCallback implements ActionMode.Callback, TextWatcher,
        View.OnClickListener, WebView.FindListener {
    private final View mCustomView;
    private final EditText mEditText;
    private final TextView mMatches;
    private WebView mWebView;
    private final InputMethodManager mInput;
    private final Resources mResources;
    private boolean mMatchesFound;
    private int mNumberOfMatches;
    private int mActiveMatchIndex;
    private ActionMode mActionMode;

    public FindActionModeCallback(Context context) {
        mCustomView = LayoutInflater.from(context).inflate(
                R.layout.webview_find, null);
        mEditText = mCustomView.findViewById(
                R.id.edit);
        mEditText.setOnClickListener(this);
        setText("");
        mMatches = mCustomView.findViewById(
                R.id.matches);
        mInput = context.getSystemService(InputMethodManager.class);
        mResources = context.getResources();
    }

    public void finish() {
        mActionMode.finish();
    }

    /**
     * Place text in the text field so it can be searched for.  Need to press
     * the find next or find previous button to find all of the matches.
     */
    public void setText(String text) {
        mEditText.setText(text);
        Spannable span = mEditText.getText();
        int length = span.length();
        // Ideally, we would like to set the selection to the whole field,
        // but this brings up the Text selection CAB, which dismisses this
        // one.
        Selection.setSelection(span, length, length);
        // Necessary each time we set the text, so that this will watch
        // changes to it.
        span.setSpan(this, 0, length, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        mMatchesFound = false;
    }

    /**
     * Set the WebView to search.
     *
     * @param webView an implementation of WebView
     */
    public void setWebView(WebView webView) {
        if (null == webView) {
            throw new AssertionError("WebView supplied to "
                    + "FindActionModeCallback cannot be null");
        }
        mWebView = webView;
        mWebView.setFindListener(this);
    }

    @Override
    public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches,
                                     boolean isDoneCounting) {
        if (isDoneCounting) {
            updateMatchCount(activeMatchOrdinal, numberOfMatches, numberOfMatches == 0);
        }
    }

    /**
     * Move the highlight to the next match.
     * @param next If {@code true}, find the next match further down in the document.
     *             If {@code false}, find the previous match, up in the document.
     */
    private void findNext(boolean next) {
        if (mWebView == null) {
            throw new AssertionError(
                    "No WebView for FindActionModeCallback::findNext");
        }
        if (!mMatchesFound) {
            findAll();
            return;
        }
        if (0 == mNumberOfMatches) {
            // There are no matches, so moving to the next match will not do
            // anything.
            return;
        }
        mWebView.findNext(next);
    }

    /**
     * Highlight all the instances of the string from mEditText in mWebView.
     */
    public void findAll() {
        if (mWebView == null) {
            throw new AssertionError(
                    "No WebView for FindActionModeCallback::findAll");
        }
        CharSequence find = mEditText.getText();
        if (0 == find.length()) {
            mWebView.clearMatches();
            mMatches.setVisibility(View.GONE);
            mMatchesFound = false;
        } else {
            mMatchesFound = true;
            mMatches.setVisibility(View.INVISIBLE);
            mNumberOfMatches = 0;
            mWebView.findAllAsync(find.toString());
        }
    }

    public void showSoftInput() {
        if (mEditText.requestFocus()) {
            mInput.showSoftInput(mEditText, 0);
        }
    }

    public void updateMatchCount(int matchIndex, int matchCount, boolean isEmptyFind) {
        if (!isEmptyFind) {
            mNumberOfMatches = matchCount;
            mActiveMatchIndex = matchIndex;
        } else {
            mMatches.setVisibility(View.GONE);
            mNumberOfMatches = 0;
        }
    }

    // OnClickListener implementation

    @Override
    public void onClick(View v) {
        findNext(true);
    }

    // ActionMode.Callback implementation

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        if (!mode.isUiFocusable()) {
            // If the action mode we're running in is not focusable the user
            // will not be able to type into the find on page field. This
            // should only come up when we're running in a dialog which is
            // already less than ideal; disable the option for now.
            return false;
        }

        mode.setCustomView(mCustomView);
        mode.getMenuInflater().inflate(R.menu.webview_find,
                menu);
        mActionMode = mode;
        Editable edit = mEditText.getText();
        Selection.setSelection(edit, edit.length());
        mMatches.setVisibility(View.GONE);
        mMatchesFound = false;
        mMatches.setText("0");
        mEditText.requestFocus();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
        mWebView.setFindListener(null);
        mInput.hideSoftInputFromWindow(mWebView.getWindowToken(), 0);
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        showSoftInput();

        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (mWebView == null) {
            throw new AssertionError(
                    "No WebView for FindActionModeCallback::onActionItemClicked");
        }
        mInput.hideSoftInputFromWindow(mWebView.getWindowToken(), 0);

        int itemId = item.getItemId();
        if (itemId == R.id.find_prev) {
            findNext(false);
        } else if (itemId == R.id.find_next) {
            findNext(true);
        } else {
            return false;
        }
        return true;
    }

    // TextWatcher implementation

    @Override
    public void beforeTextChanged(CharSequence s,
                                  int start,
                                  int count,
                                  int after) {
        // Does nothing.  Needed to implement TextWatcher.
    }

    @Override
    public void onTextChanged(CharSequence s,
                              int start,
                              int before,
                              int count) {
        findAll();
    }

    @Override
    public void afterTextChanged(Editable s) {
        // Does nothing.  Needed to implement TextWatcher.
    }

    private final Rect mGlobalVisibleRect = new Rect();
    private final Point mGlobalVisibleOffset = new Point();
    public int getActionModeGlobalBottom() {
        if (mActionMode == null) {
            return 0;
        }
        View view = (View) mCustomView.getParent();
        if (view == null) {
            view = mCustomView;
        }
        view.getGlobalVisibleRect(mGlobalVisibleRect, mGlobalVisibleOffset);
        return mGlobalVisibleRect.bottom;
    }

    public static class NoAction implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
    }
}
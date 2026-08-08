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

package app.opendocument.droid.ui

import android.content.Context
import android.text.Editable
import android.text.Selection
import android.text.Spannable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import app.opendocument.droid.R

// https://github.com/aosp-mirror/platform_frameworks_base/blob/a8b1b1a2e62bcca18f52ed31549c93d43728d152/core/java/android/webkit/FindActionModeCallback.java
class FindActionModeCallback(context: Context) :
    ActionMode.Callback, TextWatcher, View.OnClickListener, WebView.FindListener {

    private val customView: View = LayoutInflater.from(context).inflate(R.layout.webview_find, null)
    private val editText: EditText = customView.findViewById(R.id.edit)
    private val matches: TextView
    private val input: InputMethodManager = context.getSystemService(InputMethodManager::class.java)

    private var webView: WebView? = null
    private var matchesFound = false
    private var numberOfMatches = 0
    private var actionMode: ActionMode? = null

    init {
        editText.setOnClickListener(this)
        setText("")
        matches = customView.findViewById(R.id.matches)
    }

    /**
     * Place text in the text field so it can be searched for. Need to press the find next or find
     * previous button to find all of the matches.
     */
    fun setText(text: String) {
        editText.setText(text)
        val span: Spannable = editText.text
        val length = span.length
        // Ideally, we would like to set the selection to the whole field,
        // but this brings up the Text selection CAB, which dismisses this
        // one.
        Selection.setSelection(span, length, length)
        // Necessary each time we set the text, so that this will watch
        // changes to it.
        span.setSpan(this, 0, length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        matchesFound = false
    }

    /**
     * Set the WebView to search.
     *
     * @param webView an implementation of WebView
     */
    fun setWebView(webView: WebView) {
        this.webView = webView
        webView.setFindListener(this)
    }

    private fun requireWebView(caller: String): WebView =
        webView ?: throw AssertionError("No WebView for FindActionModeCallback::$caller")

    override fun onFindResultReceived(
        activeMatchOrdinal: Int,
        numberOfMatches: Int,
        isDoneCounting: Boolean,
    ) {
        if (isDoneCounting) {
            updateMatchCount(activeMatchOrdinal, numberOfMatches, numberOfMatches == 0)
        }
    }

    /**
     * Move the highlight to the next match.
     *
     * @param next If `true`, find the next match further down in the document. If `false`, find the
     *   previous match, up in the document.
     */
    private fun findNext(next: Boolean) {
        val webView = requireWebView("findNext")
        if (!matchesFound) {
            findAll()
            return
        }
        if (0 == numberOfMatches) {
            // There are no matches, so moving to the next match will not do
            // anything.
            return
        }
        webView.findNext(next)
    }

    /** Highlight all the instances of the string from [editText] in the WebView. */
    fun findAll() {
        val webView = requireWebView("findAll")
        val find: CharSequence = editText.text
        if (0 == find.length) {
            webView.clearMatches()
            matches.visibility = View.GONE
            matchesFound = false
        } else {
            matchesFound = true
            matches.visibility = View.INVISIBLE
            numberOfMatches = 0
            webView.findAllAsync(find.toString())
        }
    }

    fun showSoftInput() {
        if (editText.requestFocus()) {
            input.showSoftInput(editText, 0)
        }
    }

    private fun updateMatchCount(matchIndex: Int, matchCount: Int, isEmptyFind: Boolean) {
        if (isEmptyFind) {
            numberOfMatches = 0
            matches.visibility = View.GONE

            return
        }

        numberOfMatches = matchCount
        matches.text = "${matchIndex + 1}/$matchCount"
        matches.visibility = View.VISIBLE
    }

    // OnClickListener implementation

    override fun onClick(v: View) {
        findNext(true)
    }

    // ActionMode.Callback implementation

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        // the AOSP original bailed out here on !mode.isUiFocusable(), which appcompat marks
        // @RestrictTo. we never start this from a dialog.

        mode.customView = customView
        mode.menuInflater.inflate(R.menu.webview_find, menu)
        actionMode = mode
        val edit: Editable = editText.text
        Selection.setSelection(edit, edit.length)
        matches.visibility = View.GONE
        matchesFound = false
        matches.text = "0"
        editText.requestFocus()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null

        // tolerant, unlike the paths that need a page to search: the mode is started before
        // setWebView and dismissing it must not throw out of the framework's own callback
        webView?.let {
            it.setFindListener(null)
            input.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        showSoftInput()

        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val webView = requireWebView("onActionItemClicked")
        input.hideSoftInputFromWindow(webView.windowToken, 0)

        when (item.itemId) {
            R.id.find_prev -> findNext(false)
            R.id.find_next -> findNext(true)
            else -> return false
        }
        return true
    }

    // TextWatcher implementation

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        // Does nothing.  Needed to implement TextWatcher.
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        findAll()
    }

    override fun afterTextChanged(s: Editable?) {
        // Does nothing.  Needed to implement TextWatcher.
    }
}

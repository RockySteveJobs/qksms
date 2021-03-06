/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.moez.QKSMS.feature.qkreply

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import android.os.Build
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import com.moez.QKSMS.R
import com.moez.QKSMS.common.androidxcompat.scope
import com.moez.QKSMS.common.base.QkThemedActivity
import com.moez.QKSMS.common.util.extensions.autoScrollToStart
import com.moez.QKSMS.common.util.extensions.resolveThemeColor
import com.moez.QKSMS.common.util.extensions.setBackgroundTint
import com.moez.QKSMS.common.util.extensions.setTint
import com.moez.QKSMS.common.util.extensions.setVisible
import com.moez.QKSMS.feature.compose.MessagesAdapter
import com.uber.autodispose.kotlin.autoDisposable
import dagger.android.AndroidInjection
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.android.synthetic.main.qkreply_activity.*
import javax.inject.Inject

class QkReplyActivity : QkThemedActivity(), QkReplyView {

    @Inject lateinit var adapter: MessagesAdapter
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

    override val menuItemIntent: Subject<Int> = PublishSubject.create()
    override val textChangedIntent by lazy { message.textChanges() }
    override val changeSimIntent by lazy { sim.clicks() }
    override val sendIntent by lazy { send.clicks() }

    private val viewModel by lazy { ViewModelProviders.of(this, viewModelFactory)[QkReplyViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        setFinishOnTouchOutside(prefs.qkreplyTapDismiss.get())
        setContentView(R.layout.qkreply_activity)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        viewModel.bindView(this)

        theme
                .autoDisposable(scope())
                .subscribe { theme ->
                    send.setBackgroundTint(theme.theme)
                    send.setTint(theme.textPrimary)
                }

        toolbar.clipToOutline = true

        adapter.autoScrollToStart(messages)

        messages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messages.adapter = adapter

        // These theme attributes don't apply themselves on API 21
        if (Build.VERSION.SDK_INT <= 22) {
            toolbar.setBackgroundTint(resolveThemeColor(R.attr.colorPrimary))
            background.setBackgroundTint(resolveThemeColor(R.attr.composeBackground))
            composeBackground.setBackgroundTint(resolveThemeColor(R.attr.composeBackground))
            composeBackgroundGradient.setBackgroundTint(resolveThemeColor(R.attr.composeBackground))
        }
    }

    override fun render(state: QkReplyState) {
        if (state.hasError) {
            finish()
        }

        threadId.onNext(state.data?.first?.id ?: 0)

        title = state.title

        toolbar.menu.findItem(R.id.expand)?.isVisible = !state.expanded
        toolbar.menu.findItem(R.id.collapse)?.isVisible = state.expanded

        adapter.data = state.data

        counter.text = state.remaining
        counter.setVisible(counter.text.isNotBlank())

        sim.setVisible(state.subscription != null)
        sim.contentDescription = getString(R.string.compose_sim_cd, state.subscription?.displayName)
        simIndex.text = "${state.subscription?.simSlotIndex?.plus(1)}"

        send.isEnabled = state.canSend
        send.imageAlpha = if (state.canSend) 255 else 128
    }

    override fun setDraft(draft: String) {
        message.setText(draft)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.qkreply, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        menuItemIntent.onNext(item.itemId)
        return true
    }

    override fun getActivityThemeRes(night: Boolean, black: Boolean) = when {
        night && black -> R.style.AppThemeBlackDialog
        night && !black -> R.style.AppThemeDarkDialog
        else -> R.style.AppThemeLightDialog
    }

}
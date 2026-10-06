package com.yakitori.browser.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.yakitori.browser.R
import com.yakitori.browser.browser.BrowserTab
import com.yakitori.browser.browser.Space
import com.yakitori.browser.browser.SpaceState
import com.yakitori.browser.data.Prefs
import com.yakitori.browser.data.Profile
import com.yakitori.browser.data.Store
import com.yakitori.browser.engine.ProfileEngine
import com.yakitori.browser.engine.ProfileOps
import com.yakitori.browser.engine.UrlUtil
import java.net.URISyntaxException

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var prefs: Prefs

    private lateinit var browserUi: View
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var profileBar: RecyclerView
    private lateinit var profileAccent: View
    private lateinit var toolbar: View
    private lateinit var bottomBar: View
    private lateinit var addressBar: EditText
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var reloadButton: ImageButton
    private lateinit var newTabButton: ImageButton
    private lateinit var menuButton: ImageButton
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var webContainer: FrameLayout
    private lateinit var tabCount: TextView
    private lateinit var profileAdapter: ProfileBarAdapter

    private var defaultBg = 0
    private var defaultFg = 0
    private var defaultField = 0

    /** 空間（プロファイル/シークレット）ごとのタブ状態。WebView はタブごとに保持する。 */
    private val spaces = LinkedHashMap<Space, SpaceState>()
    private var profiles: List<Profile> = emptyList()
    private var currentSpace: Space? = null
    private var currentTab: BrowserTab? = null
    private var lastNormalProfileId = -1L
    private var incognitoEngineName: String? = null
    private var nextIncognitoTabId = -1L

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingDownload: (() -> Unit)? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
            fileCallback = null
        }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val download = pendingDownload
            pendingDownload = null
            if (granted) download?.invoke() else toast(R.string.download_permission_denied)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = Store.get(this)
        prefs = Prefs(this)

        bindViews()
        setupToolbar()
        setupBottomBar()
        setupProfileBar()
        setupBackHandling()

        profiles = loadProfiles()
        val initial = profiles.firstOrNull { it.id == prefs.lastProfileId } ?: profiles.first()
        lastNormalProfileId = if (ProfileEngine.isSupported) initial.id else profiles.first().id
        switchSpace(Space.Normal(lastNormalProfileId))

        if (!ProfileEngine.isSupported && !unsupportedWarningShown) {
            unsupportedWarningShown = true
            showUnsupportedDialog()
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        currentTab?.webView?.onResume()
        reconcile()
    }

    override fun onPause() {
        currentTab?.webView?.onPause()
        (currentSpace as? Space.Normal)?.let { ProfileEngine.flush(ProfileEngine.nameFor(it.profileId)) }
        super.onPause()
    }

    override fun onDestroy() {
        if (customView != null) hideCustomView()
        val incognito = incognitoEngineName
        spaces.values.forEach { state -> state.tabs.forEach { destroyWebView(it) } }
        spaces.clear()
        currentTab = null
        incognitoEngineName = null
        // 仕様8: セッション終了時にシークレット用データを破棄
        if (incognito != null) ProfileEngine.scheduleDeletion(this, incognito)
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // View setup
    // ------------------------------------------------------------------

    private fun bindViews() {
        browserUi = findViewById(R.id.browserUi)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        profileBar = findViewById(R.id.profileBar)
        profileAccent = findViewById(R.id.profileAccent)
        toolbar = findViewById(R.id.toolbar)
        bottomBar = findViewById(R.id.bottomBar)
        addressBar = findViewById(R.id.addressBar)
        backButton = findViewById(R.id.backButton)
        forwardButton = findViewById(R.id.forwardButton)
        reloadButton = findViewById(R.id.reloadButton)
        newTabButton = findViewById(R.id.newTabButton)
        menuButton = findViewById(R.id.menuButton)
        progressBar = findViewById(R.id.progressBar)
        webContainer = findViewById(R.id.webContainer)
        tabCount = findViewById(R.id.tabCount)

        defaultBg = MaterialColors.getColor(browserUi, com.google.android.material.R.attr.colorSurface)
        defaultFg = MaterialColors.getColor(browserUi, com.google.android.material.R.attr.colorOnSurface)
        defaultField = MaterialColors.getColor(browserUi, com.google.android.material.R.attr.colorSurfaceContainerHighest)
    }

    private fun setupToolbar() {
        backButton.setOnClickListener { currentTab?.webView?.let { if (it.canGoBack()) it.goBack() } }
        forwardButton.setOnClickListener { currentTab?.webView?.let { if (it.canGoForward()) it.goForward() } }
        reloadButton.setOnClickListener {
            val tab = currentTab ?: return@setOnClickListener
            val webView = tab.webView ?: return@setOnClickListener
            if (tab.loading) webView.stopLoading() else webView.reload()
        }
        addressBar.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                navigate(addressBar.text.toString())
                true
            } else {
                false
            }
        }
        addressBar.setOnFocusChangeListener { _, hasFocus ->
            val url = currentTab?.url.orEmpty()
            if (hasFocus) {
                addressBar.setText(if (url == "about:blank") "" else url)
                addressBar.post { addressBar.selectAll() }
            } else {
                addressBar.setText(UrlUtil.display(url))
            }
        }
    }

    private fun setupBottomBar() {
        findViewById<View>(R.id.tabsButton).setOnClickListener { showTabsSheet() }
        newTabButton.setOnClickListener { openNewTab() }
        menuButton.setOnClickListener { showMainMenu(it) }
    }

    private fun setupProfileBar() {
        profileAdapter = ProfileBarAdapter(
            onSelect = { switchSpace(it) },
            onLongPress = { view, space -> showProfileMenu(view, space) },
            onAdd = { createProfile() },
        )
        profileBar.adapter = profileAdapter
        profileBar.itemAnimator = null
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val tab = currentTab
                when {
                    customView != null -> hideCustomView()
                    addressBar.hasFocus() -> clearAddressFocus()
                    tab?.webView?.canGoBack() == true -> tab?.webView?.goBack()
                    tab != null && tab.opener != null -> closeTab(tab)
                    else -> moveTaskToBack(true)
                }
            }
        })
    }

    // ------------------------------------------------------------------
    // Profiles / spaces
    // ------------------------------------------------------------------

    private fun loadProfiles(): List<Profile> =
        store.profiles().ifEmpty {
            store.createProfile(getString(R.string.default_profile_name), ProfileIcons.ALL.first())
            store.profiles()
        }

    /** WebView がマルチプロファイル非対応の場合は先頭プロファイルのみ利用可能にする（データ混在防止）。 */
    private fun isSpaceAllowed(space: Space): Boolean =
        ProfileEngine.isSupported || (space is Space.Normal && space.profileId == profiles.firstOrNull()?.id)

    private fun stateFor(space: Space): SpaceState = spaces.getOrPut(space) {
        SpaceState().also { state ->
            if (space is Space.Normal) {
                store.tabs(space.profileId).forEach {
                    state.tabs += BrowserTab(it.id, space, it.url.ifBlank { "about:blank" }, it.title)
                }
                val activeId = store.activeTabId(space.profileId)
                state.active = state.tabs.firstOrNull { it.id == activeId } ?: state.tabs.lastOrNull()
            }
        }
    }

    /** 仕様5.2: ワンタップでプロファイルを切り替え、そのプロファイルで最後に表示していたタブを復元する。 */
    private fun switchSpace(space: Space) {
        if (!isSpaceAllowed(space)) {
            toast(R.string.multi_profile_unsupported_short)
            return
        }
        if (space == currentSpace && currentTab != null) return
        if (space is Space.Incognito && incognitoEngineName == null) {
            incognitoEngineName = ProfileEngine.newIncognitoName()
        }
        clearAddressFocus()
        currentSpace = space
        if (space is Space.Normal) {
            lastNormalProfileId = space.profileId
            prefs.lastProfileId = space.profileId
        }
        val state = stateFor(space)
        val tab = state.active ?: state.tabs.lastOrNull() ?: createTab(space, prefs.newTabUrl)
        showTab(tab)
        updateSpaceChrome()
    }

    /** プロファイルの追加・編集・削除（設定画面からの変更を含む）を画面状態へ反映する。 */
    private fun reconcile() {
        profiles = loadProfiles()
        val ids = profiles.map { it.id }.toSet()
        spaces.keys.filterIsInstance<Space.Normal>().filter { it.profileId !in ids }.forEach { destroySpace(it) }
        if (!ProfileEngine.isSupported || lastNormalProfileId !in ids) lastNormalProfileId = profiles.first().id
        val current = currentSpace
        if (current == null || (current is Space.Normal && current.profileId !in ids) || !isSpaceAllowed(current)) {
            currentSpace = null
            currentTab = null
            switchSpace(Space.Normal(lastNormalProfileId))
        } else {
            updateSpaceChrome()
        }
        // プロファイル削除後、WebView が破棄されていればここでディレクトリごと削除される
        ProfileEngine.processPendingDeletions(this)
    }

    private fun destroySpace(space: Space) {
        spaces.remove(space)?.tabs?.forEach {
            if (it === currentTab) currentTab = null
            destroyWebView(it)
        }
    }

    private fun exitIncognito() {
        val state = spaces.remove(Space.Incognito)
        state?.tabs?.forEach {
            if (it === currentTab) currentTab = null
            destroyWebView(it)
        }
        val name = incognitoEngineName
        incognitoEngineName = null
        if (currentSpace == Space.Incognito) {
            currentSpace = null
            switchSpace(Space.Normal(lastNormalProfileId))
        } else {
            updateSpaceChrome()
        }
        if (name != null) ProfileEngine.scheduleDeletion(this, name)
        toast(R.string.incognito_closed)
    }

    private fun openIncognitoTab() {
        val existed = spaces.containsKey(Space.Incognito)
        switchSpace(Space.Incognito)
        if (existed && currentSpace == Space.Incognito) openNewTab()
    }

    private fun profileOf(space: Space?): Profile? =
        (space as? Space.Normal)?.let { normal -> profiles.firstOrNull { it.id == normal.profileId } }

    private fun spaceLabel(space: Space): String = when (space) {
        is Space.Normal -> profileOf(space)?.let { "${it.icon} ${it.name}" }.orEmpty()
        Space.Incognito -> "${ProfileIcons.INCOGNITO_ICON} ${getString(R.string.incognito)}"
    }

    private fun accentOf(space: Space?): Int = when (space) {
        Space.Incognito -> ProfileIcons.INCOGNITO_ACCENT
        else -> ProfileIcons.accent(profileOf(space)?.icon)
    }

    private fun createProfile() {
        ProfileDialogs.showEditor(this, null, profiles.map { it.icon }) { name, icon ->
            val profile = store.createProfile(name, icon)
            reconcile()
            switchSpace(Space.Normal(profile.id))
        }
    }

    private fun showProfileMenu(anchor: View, space: Space) {
        val popup = PopupMenu(this, anchor)
        when (space) {
            is Space.Normal -> {
                popup.menu.add(0, MENU_PROFILE_EDIT, 0, R.string.edit)
                popup.menu.add(0, MENU_PROFILE_CLEAR, 1, R.string.clear_data_title)
                popup.menu.add(0, MENU_PROFILE_DELETE, 2, R.string.delete)
            }
            Space.Incognito -> {
                if (!spaces.containsKey(Space.Incognito)) return
                popup.menu.add(0, MENU_EXIT_INCOGNITO, 0, R.string.menu_exit_incognito)
            }
        }
        popup.setOnMenuItemClickListener { item ->
            val profile = profileOf(space)
            when (item.itemId) {
                MENU_PROFILE_EDIT -> profile?.let { editProfile(it) }
                MENU_PROFILE_CLEAR -> profile?.let { ClearDataDialog.show(this, it) }
                MENU_PROFILE_DELETE -> profile?.let { deleteProfile(it) }
                MENU_EXIT_INCOGNITO -> exitIncognito()
            }
            true
        }
        popup.show()
    }

    private fun editProfile(profile: Profile) {
        ProfileDialogs.showEditor(this, profile, profiles.map { it.icon }) { name, icon ->
            store.updateProfile(profile.id, name, icon)
            reconcile()
        }
    }

    private fun deleteProfile(profile: Profile) {
        if (profiles.size <= 1) {
            toast(R.string.profile_delete_last)
            return
        }
        ProfileDialogs.confirmDelete(this, profile) {
            ProfileOps.delete(this, profile.id)
            reconcile()
        }
    }

    private fun showUnsupportedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.multi_profile_unsupported_title)
            .setMessage(R.string.multi_profile_unsupported_message)
            .setPositiveButton(R.string.update_webview) { _, _ ->
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.webview"))
                try {
                    startActivity(market)
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.no_app_for_link)
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Tabs
    // ------------------------------------------------------------------

    private fun createTab(space: Space, url: String): BrowserTab {
        val state = stateFor(space)
        val id = when (space) {
            is Space.Normal -> store.insertTab(space.profileId, url, "")
            Space.Incognito -> nextIncognitoTabId--
        }
        return BrowserTab(id, space, url, "").also { state.tabs += it }
    }

    private fun openNewTab(url: String = prefs.newTabUrl) {
        val space = currentSpace ?: return
        showTab(createTab(space, url))
        if (url == "about:blank") {
            addressBar.requestFocus()
            getSystemService(InputMethodManager::class.java)?.showSoftInput(addressBar, 0)
        }
    }

    private fun showTab(tab: BrowserTab) {
        val state = stateFor(tab.space)
        state.active = tab
        (tab.space as? Space.Normal)?.let { store.setActiveTabId(it.profileId, tab.id) }

        val previous = currentTab
        if (previous !== tab) {
            previous?.webView?.let {
                it.onPause()
                webContainer.removeView(it)
            }
        }
        val webView = tab.webView ?: createWebView(tab).also { created ->
            tab.webView = created
            if (tab.url.isNotBlank()) created.loadUrl(tab.url)
        }
        if (webView.parent !== webContainer) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webContainer.addView(
                webView,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
        webView.onResume()
        currentTab = tab
        refreshTabUi()
    }

    private fun closeTab(tab: BrowserTab) {
        val state = spaces[tab.space] ?: return
        val index = state.tabs.indexOf(tab)
        if (index < 0) return
        state.tabs.removeAt(index)
        destroyWebView(tab)
        (tab.space as? Space.Normal)?.let { store.deleteTab(tab.id) }

        if (state.active === tab) {
            state.active = tab.opener?.takeIf { it in state.tabs }
                ?: state.tabs.getOrNull(index)
                ?: state.tabs.getOrNull(index - 1)
        }
        if (currentTab === tab) {
            currentTab = null
            val next = state.active
            when {
                next != null -> showTab(next)
                tab.space == Space.Incognito -> exitIncognito()
                else -> showTab(createTab(tab.space, prefs.newTabUrl))
            }
        }
        refreshTabUi()
    }

    private fun closeAllTabs(space: Space) {
        if (space == Space.Incognito) {
            exitIncognito()
            return
        }
        val state = spaces[space] ?: return
        state.tabs.toList().forEach {
            if (it === currentTab) currentTab = null
            destroyWebView(it)
            store.deleteTab(it.id)
        }
        state.tabs.clear()
        state.active = null
        if (currentSpace == space) showTab(createTab(space, prefs.newTabUrl))
    }

    private fun destroyWebView(tab: BrowserTab) {
        val webView = tab.webView ?: return
        tab.webView = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
    }

    private fun persistTab(tab: BrowserTab) {
        val space = tab.space as? Space.Normal ?: return
        if (tab.url.isBlank()) return
        store.updateTab(tab.id, tab.url, tab.title)
        if (tab.url.startsWith("http")) store.addHistory(space.profileId, tab.url, tab.title)
    }

    private fun showTabsSheet() {
        val space = currentSpace ?: return
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_tabs, null)
        val title = view.findViewById<TextView>(R.id.sheetTitle)
        val list = view.findViewById<RecyclerView>(R.id.tabList)

        lateinit var adapter: TabListAdapter
        fun refresh() {
            val state = spaces[space]
            if (state == null || currentSpace != space) {
                dialog.dismiss()
                return
            }
            title.text = getString(R.string.tabs_title, spaceLabel(space), state.tabs.size)
            adapter.submit(state.tabs.toList(), state.active, accentOf(space))
        }
        adapter = TabListAdapter(
            onSelect = { tab ->
                dialog.dismiss()
                showTab(tab)
            },
            onClose = { tab ->
                closeTab(tab)
                refresh()
            },
        )
        list.adapter = adapter
        view.findViewById<MaterialButton>(R.id.sheetNewTab).setOnClickListener {
            dialog.dismiss()
            openNewTab()
        }
        view.findViewById<MaterialButton>(R.id.closeAllButton).setOnClickListener {
            dialog.dismiss()
            closeAllTabs(space)
        }
        refresh()
        dialog.setContentView(view)
        dialog.show()
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    private fun navigate(input: String) {
        val url = UrlUtil.resolve(input, prefs.searchTemplate)
        clearAddressFocus()
        if (url.isNotEmpty()) loadInCurrentTab(url)
    }

    private fun loadInCurrentTab(url: String) {
        val tab = currentTab ?: return
        tab.url = url
        tab.webView?.loadUrl(url)
        refreshTabUi()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val url = intent.dataString ?: return
        if (!URLUtil.isNetworkUrl(url)) return
        val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        if (profileId > 0 && profiles.any { it.id == profileId }) switchSpace(Space.Normal(profileId))
        if (intent.getBooleanExtra(EXTRA_IN_CURRENT_TAB, false)) loadInCurrentTab(url) else openNewTab(url)
        setIntent(Intent(this, MainActivity::class.java))
    }

    private fun clearAddressFocus() {
        if (!addressBar.hasFocus()) return
        addressBar.clearFocus()
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(addressBar.windowToken, 0)
    }

    private fun showMainMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val menu = popup.menu
        val incognito = currentSpace == Space.Incognito
        menu.add(0, MENU_NEW_TAB, 0, R.string.new_tab)
        if (incognito) {
            menu.add(0, MENU_EXIT_INCOGNITO, 1, R.string.menu_exit_incognito)
        } else {
            menu.add(0, MENU_NEW_INCOGNITO, 1, R.string.menu_new_incognito_tab)
        }
        menu.add(0, MENU_HOME, 2, R.string.home)
        menu.add(0, MENU_SHARE, 3, R.string.share)
        if (!incognito) {
            menu.add(0, MENU_HISTORY, 4, R.string.history)
            menu.add(0, MENU_CLEAR_DATA, 5, R.string.clear_data_title)
        }
        menu.add(0, MENU_PROFILES, 6, R.string.profiles_manage)
        menu.add(0, MENU_SETTINGS, 7, R.string.settings)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_NEW_TAB -> openNewTab()
                MENU_NEW_INCOGNITO -> openIncognitoTab()
                MENU_EXIT_INCOGNITO -> exitIncognito()
                MENU_HOME -> loadInCurrentTab(prefs.homepage)
                MENU_SHARE -> shareCurrentPage()
                MENU_HISTORY -> startActivity(
                    Intent(this, HistoryActivity::class.java)
                        .putExtra(HistoryActivity.EXTRA_PROFILE_ID, lastNormalProfileId),
                )
                MENU_CLEAR_DATA -> profileOf(currentSpace)?.let { ClearDataDialog.show(this, it) }
                MENU_PROFILES -> startActivity(Intent(this, ProfilesActivity::class.java))
                MENU_SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        popup.show()
    }

    private fun shareCurrentPage() {
        val url = currentTab?.url?.takeIf { URLUtil.isNetworkUrl(it) } ?: return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        startActivity(Intent.createChooser(send, getString(R.string.share)))
    }

    // ------------------------------------------------------------------
    // UI state
    // ------------------------------------------------------------------

    private fun refreshTabUi() {
        val tab = currentTab
        val webView = tab?.webView
        if (!addressBar.hasFocus()) addressBar.setText(UrlUtil.display(tab?.url.orEmpty()))
        val canBack = webView?.canGoBack() == true
        val canForward = webView?.canGoForward() == true
        backButton.isEnabled = canBack
        backButton.alpha = if (canBack) 1f else 0.35f
        forwardButton.isEnabled = canForward
        forwardButton.alpha = if (canForward) 1f else 0.35f
        updateProgress(tab)
        currentSpace?.let { tabCount.text = (spaces[it]?.tabs?.size ?: 0).coerceAtMost(99).toString() }
    }

    private fun updateProgress(tab: BrowserTab?) {
        if (tab != null && tab.loading && tab.progress < 100) {
            progressBar.visibility = View.VISIBLE
            progressBar.setProgressCompat(tab.progress, true)
        } else {
            progressBar.visibility = View.INVISIBLE
        }
        reloadButton.setImageResource(if (tab?.loading == true) R.drawable.ic_close else R.drawable.ic_refresh)
        reloadButton.contentDescription = getString(if (tab?.loading == true) R.string.stop else R.string.reload)
    }

    /** 現在のプロファイル／シークレットを一目で分かるように配色を切り替える（仕様8・15.3）。 */
    private fun updateSpaceChrome() {
        val space = currentSpace ?: return
        val incognito = space == Space.Incognito
        val accent = accentOf(space)
        val bg = if (incognito) ProfileIcons.INCOGNITO_BG else defaultBg
        val fg = if (incognito) ProfileIcons.INCOGNITO_FG else defaultFg
        val field = if (incognito) ProfileIcons.INCOGNITO_FIELD else defaultField

        profileAccent.setBackgroundColor(accent)
        progressBar.setIndicatorColor(accent)
        listOf(profileBar, toolbar, bottomBar).forEach { it.setBackgroundColor(bg) }
        val tint = ColorStateList.valueOf(fg)
        listOf(backButton, forwardButton, reloadButton, newTabButton, menuButton).forEach {
            ImageViewCompat.setImageTintList(it, tint)
        }
        addressBar.setTextColor(fg)
        addressBar.setHintTextColor(ColorUtils.setAlphaComponent(fg, 0x99))
        ViewCompat.setBackgroundTintList(addressBar, ColorStateList.valueOf(field))
        addressBar.hint = getString(if (incognito) R.string.address_hint_incognito else R.string.address_hint)
        tabCount.setTextColor(fg)
        tabCount.background = GradientDrawable().apply {
            cornerRadius = 6.dp().toFloat()
            setStroke(2.dp(), fg)
        }

        window.statusBarColor = bg
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            ColorUtils.calculateLuminance(bg) > 0.5
        if (incognito && prefs.incognitoBlockScreenshots) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        profileAdapter.submit(profiles, space, spaces.containsKey(Space.Incognito), fg)
        val position = profileAdapter.positionOf(space)
        if (position >= 0) profileBar.smoothScrollToPosition(position)
        refreshTabUi()
    }

    // ------------------------------------------------------------------
    // WebView
    // ------------------------------------------------------------------

    private fun engineNameFor(space: Space): String = when (space) {
        is Space.Normal -> ProfileEngine.nameFor(space.profileId)
        Space.Incognito -> incognitoEngineName ?: ProfileEngine.newIncognitoName().also { incognitoEngineName = it }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: BrowserTab): WebView {
        val webView = WebView(this)
        val engineName = engineNameFor(tab.space)
        // プロファイルの割り当ては WebView を使用する前に行う必要がある
        ProfileEngine.bind(webView, engineName)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // 埋め込み WebView 扱いによるログイン拒否（Google 等）を避けるため、UA から "wv" 表記を除く
            userAgentString = userAgentString
                .replace("; wv)", ")")
                .replace(Regex("Version/\\d+(\\.\\d+)* "), "")
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webView.settings, emptySet())
        }
        ProfileEngine.cookieManager(engineName).setAcceptThirdPartyCookies(webView, true)
        // 仕様12: Android Autofill（Google Password Manager 等）に対応
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        webView.webViewClient = TabWebViewClient(tab)
        webView.webChromeClient = TabChromeClient(tab)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(tab, url, userAgent, contentDisposition, mimeType)
        }
        return webView
    }

    private fun openExternal(view: WebView, url: String) {
        val intent = try {
            if (url.startsWith("intent:", ignoreCase = true)) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
        } catch (e: URISyntaxException) {
            return
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null && URLUtil.isNetworkUrl(fallback)) view.loadUrl(fallback) else toast(R.string.no_app_for_link)
        }
    }

    private fun startDownload(tab: BrowserTab, url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        if (!URLUtil.isNetworkUrl(url)) {
            toast(R.string.download_unsupported)
            return
        }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val cookies = runCatching { ProfileEngine.cookieManager(engineNameFor(tab.space)).getCookie(url) }.getOrNull()
        val download = {
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .addRequestHeader("User-Agent", userAgent)
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            if (!cookies.isNullOrEmpty()) request.addRequestHeader("Cookie", cookies)
            try {
                getSystemService(DownloadManager::class.java).enqueue(request)
                toast(R.string.download_started)
            } catch (e: Exception) {
                toast(R.string.download_failed)
            }
        }
        val withPermission = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                pendingDownload = download
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                download()
            }
        }
        if (prefs.confirmDownload) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.download_confirm_title)
                .setMessage(fileName)
                .setPositiveButton(R.string.download) { _, _ -> withPermission() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            withPermission()
        }
    }

    private fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        fullscreenContainer.isVisible = true
        browserUi.isVisible = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun hideCustomView() {
        if (customView == null) return
        fullscreenContainer.removeAllViews()
        fullscreenContainer.isVisible = false
        browserUi.isVisible = true
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
    }

    private inner class TabWebViewClient(private val tab: BrowserTab) : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            return when (uri.scheme?.lowercase()) {
                null, "http", "https", "about", "data", "blob", "javascript" -> false
                else -> {
                    view?.let { openExternal(it, uri.toString()) }
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (url != null) tab.url = url
            tab.loading = true
            tab.progress = 0
            if (tab === currentTab) refreshTabUi()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (url != null) tab.url = url
            view?.title?.takeIf { it.isNotBlank() }?.let { tab.title = it }
            tab.loading = false
            tab.progress = 100
            // 仕様8: シークレットでは履歴・タブを保存しない（persistTab は通常プロファイルのみ）
            persistTab(tab)
            if (tab === currentTab) refreshTabUi()
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            if (url != null) tab.url = url
            if (tab === currentTab) refreshTabUi()
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            // レンダラが落ちてもアプリ全体は落とさず、該当タブの WebView を作り直す
            if (tab.webView === view) {
                val wasCurrent = tab === currentTab
                destroyWebView(tab)
                if (wasCurrent) {
                    currentTab = null
                    showTab(tab)
                }
            }
            return true
        }
    }

    private inner class TabChromeClient(private val tab: BrowserTab) : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            tab.progress = newProgress
            if (tab === currentTab) updateProgress(tab)
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (title.isNullOrBlank()) return
            tab.title = title
            if (!tab.loading) persistTab(tab)
        }

        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
            // ユーザー操作を伴わないポップアップはブロック
            if (!isUserGesture || tab.space != currentSpace) return false
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val child = createTab(tab.space, "")
            child.opener = tab
            showTab(child)
            transport.webView = child.webView
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            if (tab.webView === window) closeTab(tab)
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            val intent = fileChooserParams?.createIntent() ?: return false
            try {
                fileChooserLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                fileCallback?.onReceiveValue(null)
                fileCallback = null
            }
            return true
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            if (view != null && callback != null) showCustomView(view, callback)
        }

        override fun onHideCustomView() {
            hideCustomView()
        }

        override fun onPermissionRequest(request: PermissionRequest?) {
            // カメラ・マイク等のサイト権限は MVP では許可しない
            request?.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            callback?.invoke(origin, false, false)
        }
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "com.yakitori.browser.extra.PROFILE_ID"
        const val EXTRA_IN_CURRENT_TAB = "com.yakitori.browser.extra.IN_CURRENT_TAB"

        private const val MENU_NEW_TAB = 1
        private const val MENU_NEW_INCOGNITO = 2
        private const val MENU_EXIT_INCOGNITO = 3
        private const val MENU_HOME = 4
        private const val MENU_SHARE = 5
        private const val MENU_HISTORY = 6
        private const val MENU_CLEAR_DATA = 7
        private const val MENU_PROFILES = 8
        private const val MENU_SETTINGS = 9
        private const val MENU_PROFILE_EDIT = 20
        private const val MENU_PROFILE_CLEAR = 21
        private const val MENU_PROFILE_DELETE = 22

        private var unsupportedWarningShown = false
    }
}

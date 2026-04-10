package com.neonide.studio.app

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.PopupMenu
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import com.neonide.studio.R
import com.neonide.studio.shared.termux.TermuxConstants
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import com.neonide.studio.app.bottomsheet.EditorBottomSheetTabAdapter
import com.neonide.studio.app.lsp.LspClient
import com.neonide.studio.app.buildoutput.BuildOutputBuffer
import com.neonide.studio.app.editor.SoraLanguageProvider
import com.neonide.studio.app.bottomsheet.model.NavigationItem
import com.neonide.studio.view.treeview.model.TreeNode
import com.neonide.studio.view.treeview.view.AndroidTreeView
import com.neonide.studio.app.utils.DisplayNameUtils
import com.neonide.studio.app.utils.SafeDirLister
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.CreateContextMenuEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.SideIconClickEvent
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.completion.snippetUpComparator
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon
import io.github.rosemoe.sora.langs.monarch.MonarchColorScheme
import com.itsaky.androidide.treesitter.TreeSitter
import com.itsaky.androidide.treesitter.java.TSLanguageJava
import com.itsaky.androidide.treesitter.kotlin.TSLanguageKotlin
import com.itsaky.androidide.treesitter.xml.TSLanguageXml
import io.github.rosemoe.sora.editor.ts.TsLanguage
import io.github.rosemoe.sora.editor.ts.TsLanguageSpec
import io.github.rosemoe.sora.langs.monarch.MonarchLanguage
import io.github.rosemoe.sora.langs.monarch.registry.MonarchGrammarRegistry
import io.github.rosemoe.sora.langs.monarch.registry.ThemeRegistry as MonarchThemeRegistry
import io.github.rosemoe.sora.langs.monarch.registry.dsl.monarchLanguages
import io.github.rosemoe.sora.langs.monarch.registry.model.ThemeSource
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.SelectionMovement
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow
import io.github.rosemoe.sora.widget.component.Magnifier
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019
import io.github.rosemoe.sora.widget.style.LineInfoPanelPosition
import io.github.rosemoe.sora.widget.style.LineInfoPanelPositionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.neonide.studio.shared.logger.IDEFileLogger
import com.neonide.studio.shared.shell.command.ExecutionCommand
import com.neonide.studio.app.TermuxService
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Termux editor activity with sora-editor demo feature set + file tree drawer.
 */
class SoraEditorActivityK : AppCompatActivity() {

    private val uiScope = MainScope()

    private var gradleJob: Job? = null
    private var gradleHandle: com.neonide.studio.app.gradle.GradleRunner.Handle? = null
    @Volatile private var gradleRunning: Boolean = false

    private val lspController by lazy { com.neonide.studio.app.lsp.EditorLspControllerFactory.createOrNoop(this) }
    private var currentFile: File? = null
    private var projectRoot: File? = null

    private val bottomSheetVm: BottomSheetViewModel by viewModels()
    private val editorVm: EditorViewModel by viewModels()

    fun getProjectRootDir(): File? = projectRoot

    companion object {
        const val EXTRA_PROJECT_DIR = "extra_project_dir"

        private const val CRASH_LOG_FILE = "crash-journal.log"
        private const val GRADLE_LOG_FILE = "gradle-build.log"
        private const val REQUEST_WRITE_EXTERNAL_STORAGE = 1

        private const val MENU_ID_DEFINITION = 1001
        private const val MENU_ID_REFERENCES = 1002
        private const val MENU_ID_HOVER = 1003

        private const val TAB_INDEX_REFERENCES = 5

        // Same symbols as sora-editor demo
        private val SYMBOLS = arrayOf(
            "->", "{", "}", "(", ")",
            ",", ".", ";", "\"", "?",
            "+", "-", "*", "/", "<",
            ">", "[", "]", ":"
        )

        private val SYMBOL_INSERT_TEXT = arrayOf(
            "\t", "{}", "}", "(", ")",
            ",", ".", ";", "\"", "?",
            "+", "-", "*", "/", "<",
            ">", "[", "]", ":"
        )
    }

    private lateinit var editor: CodeEditor
    private lateinit var drawerLayout: DrawerLayout

    /**
     * Used to prevent gesture conflicts: when the left drawer (file tree) is open,
     * the bottom sheet should not react to vertical drags coming from drawer interactions.
     */
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var bottomSheetView: View? = null
    private var isBottomSheetTouchBlocked: Boolean = false
    private val blockAllTouchesListener = View.OnTouchListener { _, _ -> true }

    private fun setBottomSheetTouchBlocked(blocked: Boolean) {
        if (blocked == isBottomSheetTouchBlocked) return
        isBottomSheetTouchBlocked = blocked
        bottomSheetView?.setOnTouchListener(if (blocked) blockAllTouchesListener else null)
    }
    private var treeView: AndroidTreeView? = null

    // --- File tree auto-refresh ---
    private var fileTreeRootNode: TreeNode? = null
    private var fileTreeRootDir: File? = null
    private val fileTreeRefreshIntervalMs: Long = 1500L
    private val fileTreeDirLastModified: MutableMap<String, Long> = mutableMapOf()
    private val fileTreeDirSnapshot: MutableMap<String, String> = mutableMapOf()
    private var isRebuildingFileTreeForScale = false
    private val fileTreeRefreshRunnable: Runnable = Runnable {
        try {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) return@Runnable
            autoRefreshFileTreeOnce()
        } finally {
            // Reschedule while drawer is open
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.postDelayed(fileTreeRefreshRunnable, fileTreeRefreshIntervalMs)
            }
        }
    }

    // --- XML diagnostics (tree-sitter based) ---
    private val xmlDiagnosticsRunnable: Runnable = Runnable {
        val f = currentFile
        if (f == null || !f.extension.equals("xml", ignoreCase = true)) return@Runnable

        // If LSP is connected for XML, prefer its diagnostics.
        val lspEditor = lspController.currentEditor()
        val lspConnected = (lspEditor != null && lspEditor.isConnected)

        // 1) Syntax diagnostics (tree-sitter) when LSP isn't available
        if (!lspConnected) {
            runCatching {
                val diags = com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer.computeXmlDiagnostics(editor.text)
                editor.setDiagnostics(diags)
            }
        }

        // 2) Inline color highlights (#RRGGBB/#AARRGGBB, etc.)
        runCatching {
            val version = editor.text.documentVersion
            val highlights = com.neonide.studio.app.editor.xml.inline.XmlColorHighlighter.computeHighlights(editor.text)
            // Discard if document changed while we were computing
            if (version == editor.text.documentVersion) {
                editor.highlightTexts = highlights
            }
        }
    }

    private lateinit var languageProvider: SoraLanguageProvider

    private lateinit var searchMenu: PopupMenu
    private var searchOptions: EditorSearcher.SearchOptions =
        EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true, RegexBackrefGrammar.DEFAULT)

    private var undoItem: MenuItem? = null
    private var redoItem: MenuItem? = null

    private val loadTMTLauncher = registerForActivityResult(GetContent()) { result: Uri? ->
        try {
            if (result == null) return@registerForActivityResult

            ensureTextmateTheme()

            ThemeRegistry.getInstance().loadTheme(
                IThemeSource.fromInputStream(
                    contentResolver.openInputStream(result),
                    result.path,
                    null
                )
            )

            // Re-apply to refresh editor colors
            val cs = editor.colorScheme
            editor.colorScheme = cs

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val loadTMLLauncher = registerForActivityResult(GetContent()) { result: Uri? ->
        try {
            if (result == null) return@registerForActivityResult

            val editorLanguage = editor.editorLanguage

            val grammarSource = IGrammarSource.fromInputStream(
                contentResolver.openInputStream(result),
                result.path,
                null
            )

            val language = if (editorLanguage is TextMateLanguage) {
                editorLanguage.updateLanguage(
                    DefaultGrammarDefinition.withGrammarSource(grammarSource)
                )
                editorLanguage
            } else {
                TextMateLanguage.create(
                    DefaultGrammarDefinition.withGrammarSource(grammarSource),
                    true
                )
            }

            editor.setEditorLanguage(language)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sora_editor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_EXTERNAL_STORAGE)
            }
        }

        drawerLayout = findViewById(R.id.drawer_layout)

        // Prevent touches from going through the drawer and interacting with the editor.
        // Also disable bottom-sheet dragging while the drawer is open to avoid gesture conflicts
        // (vertical swipes in the file tree moving the bottom sheet).
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // While sliding/open, don't let the bottom sheet start dragging and don't allow
                // any touch interaction on its content (e.g. build output pinch/scroll).
                val opening = slideOffset > 0f
                bottomSheetBehavior?.isDraggable = !opening
                setBottomSheetTouchBlocked(opening)
            }

            override fun onDrawerOpened(drawerView: View) {
                // Disable editor interaction while drawer is open
                if (this@SoraEditorActivityK::editor.isInitialized) editor.isEnabled = false
                bottomSheetBehavior?.isDraggable = false
                setBottomSheetTouchBlocked(true)

                // Prevent horizontal swipes inside the drawer content from closing it.
                // We'll still allow closing by tapping outside via a custom touch handler below.
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN, GravityCompat.START)

                // Close drawer when user taps outside (right side / scrim area).
                // This keeps horizontal panning inside the file tree working without accidental close.
                val drawer = findViewById<View>(R.id.file_tree_drawer)
                drawerLayout.setOnTouchListener { _, ev ->
                    if (!drawerLayout.isDrawerOpen(GravityCompat.START)) return@setOnTouchListener false
                    if (ev.action == MotionEvent.ACTION_DOWN) {
                        val drawerRight = drawer.right
                        if (ev.x > drawerRight) {
                            drawerLayout.closeDrawer(GravityCompat.START)
                            return@setOnTouchListener true
                        }
                    }
                    false
                }

                // Auto-refresh while the drawer is open.
                drawerLayout.removeCallbacks(fileTreeRefreshRunnable)
                drawerLayout.post(fileTreeRefreshRunnable)
            }

            override fun onDrawerClosed(drawerView: View) {
                if (this@SoraEditorActivityK::editor.isInitialized) editor.isEnabled = true
                bottomSheetBehavior?.isDraggable = true
                setBottomSheetTouchBlocked(false)

                // Re-enable normal drawer gestures.
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)

                // Remove outside-tap handler.
                drawerLayout.setOnTouchListener(null)

                // Stop auto-refresh when not visible.
                drawerLayout.removeCallbacks(fileTreeRefreshRunnable)
            }
        })

        val tb: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(tb)
        supportActionBar?.title = "Editor"

        // Drawer (file tree) toolbar
        runCatching {
            val ftb = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.file_tree_toolbar)
            ftb.setNavigationOnClickListener { drawerLayout.closeDrawer(GravityCompat.START) }
        }

        setupAcsBottomSheet()

        // Hamburger icon
        tb.setNavigationIcon(R.drawable.ic_menu)
        tb.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        languageProvider = SoraLanguageProvider(this)
        editor = findViewById(R.id.editor)

        // Typeface
        runCatching {
            editor.typefaceText = Typeface.createFromAsset(assets, "JetBrainsMono-Regular.ttf")
        }

        // Configure symbol input
        findViewById<io.github.rosemoe.sora.widget.SymbolInputView>(R.id.symbol_input).apply {
            bindEditor(editor)
            addSymbols(SYMBOLS, SYMBOL_INSERT_TEXT)
        }

        // Configure search options popup
        searchMenu = PopupMenu(this, findViewById(R.id.search_options)).apply {
            menuInflater.inflate(R.menu.menu_sora_search_options, menu)
            setOnMenuItemClickListener { item ->
                item.isChecked = !item.isChecked
                // Regex and whole word are mutually exclusive
                if (item.isChecked) {
                    when (item.itemId) {
                        R.id.sora_search_option_regex -> menu.findItem(R.id.sora_search_option_whole_word)?.isChecked = false
                        R.id.sora_search_option_whole_word -> menu.findItem(R.id.sora_search_option_regex)?.isChecked = false
                    }
                }
                computeSearchOptions()
                tryCommitSearch()
                true
            }
        }

        // Search text change listener
        findViewById<android.widget.EditText>(R.id.search_editor).addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { tryCommitSearch() }
        })

        // Buttons
        findViewById<View>(R.id.btn_goto_prev).setOnClickListener { gotoPrev() }
        findViewById<View>(R.id.btn_goto_next).setOnClickListener { gotoNext() }
        findViewById<View>(R.id.btn_replace).setOnClickListener { replaceCurrent() }
        findViewById<View>(R.id.btn_replace_all).setOnClickListener { replaceAll() }
        findViewById<View>(R.id.search_options).setOnClickListener { searchMenu.show() }

        // Base editor config
        // Set a default empty language. The actual language will be set when a file is opened.
        editor.setEditorLanguage(EmptyLanguage())
        editor.props.stickyScroll = true
        editor.nonPrintablePaintingFlags =
            CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                CodeEditor.FLAG_DRAW_LINE_SEPARATOR or
                CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION or
                CodeEditor.FLAG_DRAW_SOFT_WRAP

        // Events for position display + undo/redo
        editor.subscribeAlways(SelectionChangeEvent::class.java) { updatePositionText() }
        editor.subscribeAlways(PublishSearchResultEvent::class.java) { updatePositionText() }
        editor.subscribeAlways(ContentChangeEvent::class.java) { ev ->
            editor.postDelayed({ updateBtnState() }, 50)

            // --- Android XML editing enhancements (ACS-like) ---
            val f = currentFile
            if (f != null && f.extension.equals("xml", ignoreCase = true)) {
                // 1) Advanced edit: typing '/' inside XML auto-completes tags (e.g., '</' -> '</TagName>')
                runCatching {
                    com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer.applyAdvancedSlashEditIfNeeded(f, editor, ev)
                }

                // 2) Tree-sitter XML diagnostics (syntax errors) -> squiggles
                // Debounce: avoid reparsing on every keystroke burst
                editor.removeCallbacks(xmlDiagnosticsRunnable)
                editor.postDelayed(xmlDiagnosticsRunnable, 180)
            }
        }
        editor.subscribeAlways(CreateContextMenuEvent::class.java) { onContextMenuCreated(it) }

        // Touch devices typically show EditorTextActionWindow (copy/cut/paste) instead of Android ContextMenu.
        // Provide JavaDoc/hover on long-press by intercepting the long-press and requesting LSP hover.
        editor.subscribeAlways(io.github.rosemoe.sora.event.LongPressEvent::class.java) { e ->
            val file = currentFile
            if (file != null && file.extension.lowercase() == "java") {
                val lspEditor = lspController.currentEditor()
                // Only show when LSP is connected/available
                if (lspEditor != null && lspEditor.isConnected) {
                    // Anchor hover window position at the pressed location
                    editor.setSelection(e.line, e.column)
                    handleShowHover(e.line, e.column)
                    // Do NOT intercept: keep default long-press selection/tool actions working
                }
            }
        }

        editor.subscribeAlways(SideIconClickEvent::class.java) {
            editor.setSelection(it.clickedIcon.line, 0)
            editor.getComponent(EditorDiagnosticTooltipWindow::class.java).show()
        }

        // Allow launching the editor with a specific project directory
        projectRoot = intent.getStringExtra(EXTRA_PROJECT_DIR)?.let { File(it) }

        // Load themes/grammars
        setupTextmate()
        setupMonarch()
        ensureTextmateTheme()

        // Load sample (no LSP) - use Java sample to demonstrate Tree-sitter integration
        openAssetsFile("samples/sample.java")

        // Prefill IDE logs tab with crash log (if any)
        runCatching {
            val f = File(filesDir, CRASH_LOG_FILE)
            if (f.exists()) bottomSheetVm.setIdeLogs(f.readText())
        }

        setupFileTree(projectRoot)

        updatePositionText()
        updateBtnState()
        switchThemeIfRequired()

        // LSP diagnostics UI is handled by editor-lsp (see PublishDiagnosticsEvent)
        // and shown via CodeEditor diagnostics tooltip.

        // LSP tree-sitter libs are heavy; do NOT load tree-sitter native libs automatically.
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        // If bottom sheet is expanded, collapse it first (ACS-like behavior)
        runCatching {
            val sheet = findViewById<View>(R.id.acs_bottom_sheet)
            val behavior = BottomSheetBehavior.from(sheet)
            if (behavior.state == BottomSheetBehavior.STATE_EXPANDED || behavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                return
            }
        }

        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop file tree auto-refresh
        runCatching { drawerLayout.removeCallbacks(fileTreeRefreshRunnable) }
        // Stop pending XML diagnostics update
        runCatching { editor.removeCallbacks(xmlDiagnosticsRunnable) }

        runCatching { gradleHandle?.cancel() }
        runCatching { gradleJob?.cancel() }
        runCatching { uiScope.cancel() }
        runCatching { lspController.dispose() }
        editor.release()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        switchThemeIfRequired()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sora_main, menu)
        undoItem = menu.findItem(R.id.sora_text_undo)
        redoItem = menu.findItem(R.id.sora_text_redo)
        updateBtnState()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sora_quick_run -> {
                onQuickRunOrCancel()
                return true
            }
            R.id.sora_sync_project -> {
                onSyncProject()
                return true
            }
            R.id.sora_text_undo -> editor.undo()
            R.id.sora_text_redo -> editor.redo()
            R.id.sora_open_terminal -> {
                runCatching {
                    startActivity(Intent(this, TermuxActivity::class.java))
                }
            }

            R.id.sora_goto_end -> editor.setSelection(editor.text.lineCount - 1, editor.text.getColumnCount(editor.text.lineCount - 1))
            R.id.sora_move_up -> editor.moveSelection(SelectionMovement.UP)
            R.id.sora_move_down -> editor.moveSelection(SelectionMovement.DOWN)
            R.id.sora_move_left -> editor.moveSelection(SelectionMovement.LEFT)
            R.id.sora_move_right -> editor.moveSelection(SelectionMovement.RIGHT)
            R.id.sora_home -> editor.moveSelection(SelectionMovement.LINE_START)
            R.id.sora_end -> editor.moveSelection(SelectionMovement.LINE_END)

            R.id.sora_magnifier -> {
                item.isChecked = !item.isChecked
                editor.getComponent(Magnifier::class.java).isEnabled = item.isChecked
            }

            R.id.sora_text_wordwrap -> {
                item.isChecked = !item.isChecked
                editor.isWordwrap = item.isChecked
            }

            R.id.sora_editor_line_number -> {
                editor.isLineNumberEnabled = !editor.isLineNumberEnabled
                item.isChecked = editor.isLineNumberEnabled
            }

            R.id.sora_pin_line_number -> {
                editor.setPinLineNumber(!editor.isLineNumberPinned)
                item.isChecked = editor.isLineNumberPinned
            }

            R.id.sora_use_icu -> {
                item.isChecked = !item.isChecked
                editor.props.useICULibToSelectWords = item.isChecked
            }

            R.id.sora_completion_anim -> {
                item.isChecked = !item.isChecked
                editor.getComponent(EditorAutoCompletion::class.java).setEnabledAnimation(item.isChecked)
            }

            R.id.sora_soft_kbd_enabled -> {
                editor.isSoftKeyboardEnabled = !editor.isSoftKeyboardEnabled
                item.isChecked = editor.isSoftKeyboardEnabled
            }

            R.id.sora_disable_soft_kbd_on_hard_kbd -> {
                editor.isDisableSoftKbdIfHardKbdAvailable = !editor.isDisableSoftKbdIfHardKbdAvailable
                item.isChecked = editor.isDisableSoftKbdIfHardKbdAvailable
            }

            R.id.sora_ln_panel_fixed -> chooseLineNumberPanelPosition()

            R.id.sora_ln_panel_follow -> chooseLineNumberPanelFollow()

            R.id.sora_code_format -> editor.formatCodeAsync()

            R.id.sora_switch_language -> chooseLanguage()

            R.id.sora_search_panel_st -> toggleSearchPanel(item)

            R.id.sora_search_am -> {
                findViewById<android.widget.EditText>(R.id.replace_editor).setText("")
                findViewById<android.widget.EditText>(R.id.search_editor).setText("")
                editor.searcher.stopSearch()
                editor.beginSearchMode()
            }

            R.id.sora_switch_colors -> chooseTheme()
            R.id.sora_switch_typeface -> chooseTypeface()

            R.id.sora_save_file -> saveCurrentFile()
            R.id.sora_open_build_log -> openBuildLog()
            R.id.sora_open_logs -> openLogs()
            R.id.sora_clear_logs -> clearLogs()
            R.id.sora_open_ide_file_log -> openIdeFileLog()

            R.id.sora_load_test_file -> openAssetsFile("samples/big_sample.txt")

            R.id.sora_start_java_lsp -> {
                runCatching {
                    startService(Intent(this, com.neonide.studio.app.lsp.server.JavaLanguageServerService::class.java))
                    android.widget.Toast.makeText(this, "Java LSP server service started", android.widget.Toast.LENGTH_SHORT).show()
                }.onFailure {
                    android.widget.Toast.makeText(this, "Failed to start Java LSP: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }

        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupAcsBottomSheet() {
        val sheet = findViewById<View>(R.id.acs_bottom_sheet)
        bottomSheetView = sheet
        val behavior = BottomSheetBehavior.from(sheet)
        bottomSheetBehavior = behavior
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        behavior.isHideable = false

        // Hide the status label by default (we only show it while building)
        runCatching {
            val status = sheet.findViewById<android.widget.TextView>(R.id.acs_bottom_sheet_status)
            status.visibility = View.GONE
        }

        val tabs = sheet.findViewById<TabLayout>(R.id.acs_bottom_sheet_tabs)
        val pager = sheet.findViewById<ViewPager2>(R.id.acs_bottom_sheet_pager)

        val adapter = EditorBottomSheetTabAdapter(this)
        pager.adapter = adapter
        // Match AndroidCodeStudio behavior: don't allow horizontal swipe to change tabs.
        // Otherwise, horizontal scrolling in build output/log views accidentally switches tabs.
        pager.isUserInputEnabled = false

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = getString(adapter.getTitleRes(position))
        }.attach()

        // Load current log files into tabs on startup.
        // Default behavior (closer to ACS): start with empty build output for each IDE session.
        // Users can still open the saved build log from menu.
        BuildOutputBuffer.clear()
        runCatching {
            val buildLog = File(filesDir, GRADLE_LOG_FILE)
            // Don't auto-load old log into the live buffer.
            // (It makes output feel "stale" and prevents seeing fresh output clearly.)
            if (!buildLog.exists()) return@runCatching
        }
        runCatching {
            val crash = File(filesDir, CRASH_LOG_FILE)
            if (crash.exists()) bottomSheetVm.setIdeLogs(crash.readText())
        }

        // Basic App Logs snapshot from logcat
        refreshAppLogs()
    }

    private fun refreshAppLogs() {
        // Best-effort: read a small logcat snapshot. Streaming logcat continuously is expensive.
        uiScope.launch(Dispatchers.IO) {
            val lines = runCatching {
                val p = ProcessBuilder("logcat", "-d", "-t", "200")
                    .redirectErrorStream(true)
                    .start()
                p.inputStream.bufferedReader().readText()
            }.getOrDefault("")

            bottomSheetVm.setAppLogs(lines)
        }
    }

    private fun updateBtnState() {
        undoItem?.isEnabled = editor.canUndo()
        redoItem?.isEnabled = editor.canRedo()

        // Update quick run icon/title based on gradle state
        // (menu item exists only after onCreateOptionsMenu)
        runCatching {
            val m = findViewById<MaterialToolbar>(R.id.toolbar).menu
            val quick = m.findItem(R.id.sora_quick_run)
            if (quick != null) {
                if (gradleRunning) {
                    quick.title = getString(R.string.acs_cancel_build)
                    quick.setIcon(R.drawable.ic_stop_daemons)
                } else {
                    quick.title = getString(R.string.acs_quick_run)
                    quick.setIcon(R.drawable.ic_run_outline)
                }
            }
        }
    }

    private fun onSyncProject() {
        val root = projectRoot
        if (root == null || !root.exists()) {
            android.widget.Toast.makeText(this, getString(R.string.acs_project_dir_missing), android.widget.Toast.LENGTH_LONG).show()
            return
        }

        // Cancel if already running
        if (gradleRunning) {
            android.widget.Toast.makeText(this, getString(R.string.acs_cancel_build), android.widget.Toast.LENGTH_SHORT).show()
            gradleHandle?.cancel()
            return
        }

        // Ensure wrapper is present (repair missing wrapper jar if possible)
        val wrapperStatus = com.neonide.studio.app.gradle.GradleProjectActions.ensureWrapperPresent(this, root)
        com.neonide.studio.app.gradle.GradleProjectActions.wrapperStatusMessage(this, wrapperStatus)?.let { msg ->
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        }
        if (wrapperStatus == com.neonide.studio.app.gradle.GradleProjectActions.WrapperStatus.MissingScriptOrProps ||
            wrapperStatus == com.neonide.studio.app.gradle.GradleProjectActions.WrapperStatus.RepairFailed
        ) {
            return
        }

        // Run "sync" plan
        android.widget.Toast.makeText(this, getString(R.string.acs_sync_started), android.widget.Toast.LENGTH_SHORT).show()
        val plan = com.neonide.studio.app.gradle.GradleProjectActions.createSyncPlan()
        runGradle(
            projectDir = root,
            args = plan.args,
            actionLabel = getString(R.string.acs_sync_project),
            kind = GradleActionKind.SYNC,
            installApkOnSuccess = false,
        )
    }

    private fun onQuickRunOrCancel() {
        val root = projectRoot
        if (root == null || !root.exists()) {
            android.widget.Toast.makeText(this, getString(R.string.acs_project_dir_missing), android.widget.Toast.LENGTH_LONG).show()
            return
        }

        if (gradleRunning) {
            gradleHandle?.cancel()
            return
        }

        // Ensure wrapper is present (repair missing wrapper jar if possible)
        val wrapperStatus = com.neonide.studio.app.gradle.GradleProjectActions.ensureWrapperPresent(this, root)
        com.neonide.studio.app.gradle.GradleProjectActions.wrapperStatusMessage(this, wrapperStatus)?.let { msg ->
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        }
        if (wrapperStatus == com.neonide.studio.app.gradle.GradleProjectActions.WrapperStatus.MissingScriptOrProps ||
            wrapperStatus == com.neonide.studio.app.gradle.GradleProjectActions.WrapperStatus.RepairFailed
        ) {
            return
        }

        android.widget.Toast.makeText(this, getString(R.string.acs_build_started), android.widget.Toast.LENGTH_SHORT).show()

        val plan = com.neonide.studio.app.gradle.GradleProjectActions.createQuickRunPlan(root)
        runGradle(
            projectDir = root,
            args = plan.args,
            actionLabel = getString(R.string.acs_quick_run),
            kind = GradleActionKind.BUILD,
            installApkOnSuccess = true,
        )
    }

    private enum class GradleActionKind { BUILD, SYNC }

    private fun runGradle(
        projectDir: File,
        args: List<String>,
        actionLabel: String,
        kind: GradleActionKind,
        installApkOnSuccess: Boolean,
    ) {
        gradleRunning = true
        invalidateOptionsMenu()
        updateBtnState()

        // Expand bottom sheet and select Build Output tab, ACS-like.
        runCatching {
            val sheet = findViewById<View>(R.id.acs_bottom_sheet)
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED

            val pager = sheet.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.acs_bottom_sheet_pager)
            pager.setCurrentItem(0, false)

            val status = sheet.findViewById<android.widget.TextView>(R.id.acs_bottom_sheet_status)
            status.text = "$actionLabel: ${getString(R.string.acs_status_building)}"
            status.visibility = View.VISIBLE
        }

        // Store output so user can inspect build/sync logs later
        val logFile = File(filesDir, GRADLE_LOG_FILE)
        runCatching { logFile.writeText("") }

        // IMPORTANT: clear live buffer so the Build Output tab shows only the new run.
        BuildOutputBuffer.clear()
        // Also clear diagnostics on new run.
        bottomSheetVm.setDiagnostics(emptyList())

        gradleJob?.cancel()
        gradleJob = uiScope.launch(Dispatchers.Main) {
            val handle = withContext(Dispatchers.IO) {
                try {
                    // Make Gradle execution environment match what users typically set in Termux.
                    // This avoids "SDK location not found" when running from the IDE UI.
                    val baseEnv = com.neonide.studio.app.gradle.GradleProjectActions.getGradleEnvironment(this@SoraEditorActivityK)
                    val sdk = com.neonide.studio.app.gradle.AndroidSdkUtils.configureForProject(
                        context = this@SoraEditorActivityK,
                        projectDir = projectDir,
                        baseEnv = baseEnv,
                    )

                    if (sdk == null) {
                        val msg = getString(R.string.acs_android_sdk_missing)
                        BuildOutputBuffer.appendLine(msg)
                        bottomSheetVm.setDiagnostics(listOf(msg))
                    }

                    val envOverrides = sdk?.env ?: emptyMap()

                    com.neonide.studio.app.gradle.GradleRunner.start(
                        context = this@SoraEditorActivityK,
                        projectDir = projectDir,
                        args = args,
                        envOverrides = envOverrides,
                    ) { line ->
                        runCatching { logFile.appendText(line + "\n") }
                        BuildOutputBuffer.appendLine(line)
                    }
                } catch (t: Throwable) {
                    // Don't crash the UI thread
                    runCatching { logFile.appendText("ERROR: ${t.message}\n") }
                    BuildOutputBuffer.appendLine("ERROR: ${t.message}")
                    null
                }
            }

            if (handle == null) {
                gradleRunning = false
                gradleHandle = null
                updateBtnState()
                android.widget.Toast.makeText(
                    this@SoraEditorActivityK,
                    "Gradle start failed. See build log.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                return@launch
            }

            gradleHandle = handle

            val result = withContext(Dispatchers.IO) { handle.waitFor() }

            gradleRunning = false
            gradleHandle = null
            updateBtnState()

            if (result.wasCancelled) {
                // Keep status visible with a cancelled state.
                runCatching {
                    val sheet = findViewById<View>(R.id.acs_bottom_sheet)
                    val status = sheet.findViewById<android.widget.TextView>(R.id.acs_bottom_sheet_status)
                    status.text = "$actionLabel: ${getString(R.string.acs_status_cancelled)}"
                    status.visibility = View.VISIBLE
                }
                android.widget.Toast.makeText(this@SoraEditorActivityK, getString(R.string.acs_cancel_build), android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (!result.isSuccessful) {
                // Basic diagnostics: extract common Gradle/AGP errors from build output snapshot.
                runCatching {
                    val diags = com.neonide.studio.app.gradle.GradleDiagnostics.extract(BuildOutputBuffer.getSnapshot())
                    bottomSheetVm.setDiagnostics(diags)
                }

                // Update status label
                runCatching {
                    val sheet = findViewById<View>(R.id.acs_bottom_sheet)
                    val status = sheet.findViewById<android.widget.TextView>(R.id.acs_bottom_sheet_status)
                    status.text = "$actionLabel: ${getString(R.string.acs_status_failed)}"
                    status.visibility = View.VISIBLE
                }

                val toastRes = if (kind == GradleActionKind.SYNC) R.string.acs_sync_failed else R.string.acs_build_failed
                android.widget.Toast.makeText(this@SoraEditorActivityK, getString(toastRes), android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }

            // Successful.
            runCatching {
                val sheet = findViewById<View>(R.id.acs_bottom_sheet)
                val status = sheet.findViewById<android.widget.TextView>(R.id.acs_bottom_sheet_status)
                status.text = "$actionLabel: ${getString(R.string.acs_status_success)}"
                status.visibility = View.VISIBLE
            }

            val toastRes = if (kind == GradleActionKind.SYNC) R.string.acs_sync_finished else R.string.acs_build_finished
            android.widget.Toast.makeText(this@SoraEditorActivityK, getString(toastRes), android.widget.Toast.LENGTH_SHORT).show()

            if (installApkOnSuccess) {
                // Try standard AGP output path first: app/build/outputs/apk/debug/app-debug.apk
                val debugApk = File(projectDir, "app/build/outputs/apk/debug/app-debug.apk")
                val apk = if (debugApk.exists()) {
                    debugApk
                } else {
                    // Fallback: search recursively in app/build/outputs/apk/
                    val apkDir = File(projectDir, "app/build/outputs/apk")
                    val apks = apkDir.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
                    // Prefer one with 'debug' in name/path, otherwise take first found
                    apks.firstOrNull { it.path.contains("debug", ignoreCase = true) } ?: apks.firstOrNull()
                }

                if (apk != null) {
                    runCatching { com.neonide.studio.app.gradle.ApkInstallUtils.installApk(this@SoraEditorActivityK, apk) }
                } else {
                    android.widget.Toast.makeText(this@SoraEditorActivityK, "APK not found", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePositionText() {
        val cursor = editor.cursor
        var text = "${cursor.leftLine + 1}:${cursor.leftColumn};${cursor.left} "

        text += if (cursor.isSelected) {
            "(${cursor.right - cursor.left} chars)"
        } else {
            val content = editor.text
            if (content.getColumnCount(cursor.leftLine) == cursor.leftColumn) {
                val sep = content.getLine(cursor.leftLine).lineSeparator
                "(<${if (sep == LineSeparator.NONE) "EOF" else sep.name}>)"
            } else {
                // best effort
                "(${content.getLine(cursor.leftLine).toString().getOrNull(cursor.leftColumn) ?: ' '})"
            }
        }

        val searcher = editor.searcher
        if (searcher.hasQuery()) {
            val idx = searcher.currentMatchedPositionIndex
            val count = searcher.matchedPositionCount
            val matchText = when (count) {
                0 -> "no match"
                1 -> "1 match"
                else -> "$count matches"
            }
            text += if (idx == -1) {
                "($matchText)"
            } else {
                "(${idx + 1} of $matchText)"
            }
        }

        findViewById<android.widget.TextView>(R.id.position_display).text = text
    }

    private fun computeSearchOptions() {
        val caseInsensitive = !searchMenu.menu.findItem(R.id.sora_search_option_match_case).isChecked
        var type = EditorSearcher.SearchOptions.TYPE_NORMAL
        val regex = searchMenu.menu.findItem(R.id.sora_search_option_regex).isChecked
        if (regex) type = EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
        val wholeWord = searchMenu.menu.findItem(R.id.sora_search_option_whole_word).isChecked
        if (wholeWord) type = EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
        searchOptions = EditorSearcher.SearchOptions(type, caseInsensitive, RegexBackrefGrammar.DEFAULT)
    }

    private fun tryCommitSearch() {
        val query = findViewById<android.widget.EditText>(R.id.search_editor).text
        if (!query.isNullOrEmpty()) {
            runCatching {
                editor.searcher.search(query.toString(), searchOptions)
            }
        } else {
            editor.searcher.stopSearch()
        }
    }

    private fun gotoNext() {
        runCatching { editor.searcher.gotoNext() }
    }

    private fun gotoPrev() {
        runCatching { editor.searcher.gotoPrevious() }
    }

    private fun replaceCurrent() {
        val replacement = findViewById<android.widget.EditText>(R.id.replace_editor).text.toString()
        runCatching { editor.searcher.replaceCurrentMatch(replacement) }
    }

    private fun replaceAll() {
        val replacement = findViewById<android.widget.EditText>(R.id.replace_editor).text.toString()
        runCatching { editor.searcher.replaceAll(replacement) }
    }

    private fun toggleSearchPanel(item: MenuItem) {
        val panel = findViewById<View>(R.id.search_panel)
        if (panel.visibility == View.GONE) {
            findViewById<android.widget.EditText>(R.id.replace_editor).setText("")
            findViewById<android.widget.EditText>(R.id.search_editor).setText("")
            editor.searcher.stopSearch()
            panel.visibility = View.VISIBLE
            item.isChecked = true
        } else {
            panel.visibility = View.GONE
            editor.searcher.stopSearch()
            item.isChecked = false
        }
    }

    private fun openAssetsFile(path: String) {
        // Assets are not part of a real project; disable LSP.
        currentFile = null
        runCatching { lspController.detach() }

        // Set language based on asset extension
        val languageForEditor = languageProvider.getLanguage(File(path))
        editor.setEditorLanguage(languageForEditor)

        uiScope.launch(Dispatchers.Main) {
            val text = withContext(Dispatchers.IO) {
                ContentIO.createFrom(assets.open(path))
            }
            editor.setText(text, null)
            updatePositionText()
            updateBtnState()
        }
    }

    private fun openBuildLog() {
        val logFile = File(filesDir, GRADLE_LOG_FILE)
        if (!logFile.exists()) {
            android.widget.Toast.makeText(this, getString(R.string.sora_not_supported), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { logFile.readText() }
            .onSuccess {
                editor.setText(it)
                bottomSheetVm.setBuildOutput(it)
            }
    }

    private fun openLogs() {
        runCatching { openFileInput(CRASH_LOG_FILE).reader().readText() }
            .onSuccess {
                editor.setText(it)
                bottomSheetVm.setIdeLogs(it)
            }
    }

    private fun clearLogs() {
        runCatching { openFileOutput(CRASH_LOG_FILE, MODE_PRIVATE).use { } }
        bottomSheetVm.setIdeLogs("")
    }

    private fun openIdeFileLog() {
        val logFile = IDEFileLogger.getLogFile()
        if (logFile == null || !logFile.exists()) {
            android.widget.Toast.makeText(this, "IDE file log not found. Enable it in IDE Configurations first.", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        runCatching { logFile.readText() }
            .onSuccess {
                editor.setText(it)
                bottomSheetVm.setIdeLogs(it)
            }
            .onFailure {
                android.widget.Toast.makeText(this, "Failed to read IDE log: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
    }

    private fun saveCurrentFile() {
        val f = currentFile
        if (f == null) {
            android.widget.Toast.makeText(this, getString(R.string.sora_not_supported), android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val text = editor.text.toString()
        val ok = runCatching {
            f.parentFile?.mkdirs()
            f.writeText(text)
            true
        }.getOrDefault(false)

        if (ok) {
            android.widget.Toast.makeText(this, getString(R.string.acs_saved), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(this, getString(R.string.acs_save_failed), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun setupTextmate() {
        loadDefaultTextMateThemes()
        loadDefaultTextMateLanguages()
    }

    private fun loadDefaultTextMateThemes() {
        val themes = arrayOf("darcula", "ayu-dark", "quietlight", "solarized_dark")
        val themeRegistry = ThemeRegistry.getInstance()
        themes.forEach { name ->
            val path = "textmate/$name.json"
            themeRegistry.loadTheme(
                ThemeModel(
                    IThemeSource.fromInputStream(
                        FileProviderRegistry.getInstance().tryGetInputStream(path),
                        path,
                        null
                    ),
                    name
                ).apply { if (name != "quietlight") isDark = true }
            )
        }
        themeRegistry.setTheme("quietlight")
    }

    private fun loadDefaultTextMateLanguages() {
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }

    private fun ensureTextmateTheme() {
        val cs = editor.colorScheme
        if (cs !is TextMateColorScheme) {
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        }
    }

    private fun setupMonarch() {
        // Themes
        val themes = arrayOf("darcula", "ayu-dark", "quietlight", "solarized_dark")
        themes.forEach { name ->
            val path = "textmate/$name.json"
            MonarchThemeRegistry.loadTheme(
                io.github.rosemoe.sora.langs.monarch.registry.model.ThemeModel(
                    ThemeSource(path, name)
                ).apply { if (name != "quietlight") isDark = true },
                false
            )
        }
        MonarchThemeRegistry.setTheme("quietlight")

        // Grammars
        MonarchGrammarRegistry.INSTANCE.loadGrammars(
            monarchLanguages {
                // Use monarch-language-pack definitions
                language("java") {
                    monarchLanguage = io.github.dingyi222666.monarch.languages.JavaLanguage
                    defaultScopeName()
                    languageConfiguration = "textmate/java/language-configuration.json"
                }
                language("kotlin") {
                    monarchLanguage = io.github.dingyi222666.monarch.languages.KotlinLanguage
                    defaultScopeName()
                    languageConfiguration = "textmate/kotlin/language-configuration.json"
                }
                language("python") {
                    monarchLanguage = io.github.dingyi222666.monarch.languages.PythonLanguage
                    defaultScopeName()
                    languageConfiguration = "textmate/python/language-configuration.json"
                }
                language("typescript") {
                    monarchLanguage = io.github.dingyi222666.monarch.languages.TypescriptLanguage
                    defaultScopeName()
                    // No bundled language-configuration for TypeScript in assets; keep null
                }
            }
        )
    }

    private fun ensureMonarchTheme() {
        if (editor.colorScheme !is MonarchColorScheme) {
            editor.colorScheme = MonarchColorScheme.create(MonarchThemeRegistry.currentTheme)
            switchThemeIfRequired()
        }
    }

    private fun switchThemeIfRequired() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        when (val scheme = editor.colorScheme) {
            is TextMateColorScheme -> ThemeRegistry.getInstance().setTheme(if (night) "darcula" else "quietlight")
            is MonarchColorScheme -> MonarchThemeRegistry.setTheme(if (night) "darcula" else "quietlight")
            else -> editor.colorScheme = if (night) SchemeDarcula() else EditorColorScheme()
        }
        editor.invalidate()
    }

    private fun chooseTypeface() {
        val fonts = arrayOf("JetBrains Mono", "Ubuntu", "Roboto")
        val assetsPaths = arrayOf("JetBrainsMono-Regular.ttf", "Ubuntu-Regular.ttf", "Roboto-Regular.ttf")
        AlertDialog.Builder(this)
            .setTitle(android.R.string.dialog_alert_title)
            .setSingleChoiceItems(fonts, -1) { dialog, which ->
                if (which in assetsPaths.indices) {
                    runCatching {
                        editor.typefaceText = Typeface.createFromAsset(assets, assetsPaths[which])
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseLineNumberPanelPosition() {
        val items = arrayOf(
            getString(R.string.sora_top),
            getString(R.string.sora_bottom),
            getString(R.string.sora_left),
            getString(R.string.sora_right),
            getString(R.string.sora_center),
            getString(R.string.sora_top_left),
            getString(R.string.sora_top_right),
            getString(R.string.sora_bottom_left),
            getString(R.string.sora_bottom_right)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.sora_fixed)
            .setSingleChoiceItems(items, -1) { dialog, which ->
                editor.lnPanelPositionMode = LineInfoPanelPositionMode.FIXED
                editor.lnPanelPosition = when (which) {
                    0 -> LineInfoPanelPosition.TOP
                    1 -> LineInfoPanelPosition.BOTTOM
                    2 -> LineInfoPanelPosition.LEFT
                    3 -> LineInfoPanelPosition.RIGHT
                    4 -> LineInfoPanelPosition.CENTER
                    5 -> LineInfoPanelPosition.TOP or LineInfoPanelPosition.LEFT
                    6 -> LineInfoPanelPosition.TOP or LineInfoPanelPosition.RIGHT
                    7 -> LineInfoPanelPosition.BOTTOM or LineInfoPanelPosition.LEFT
                    8 -> LineInfoPanelPosition.BOTTOM or LineInfoPanelPosition.RIGHT
                    else -> LineInfoPanelPosition.CENTER
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseLineNumberPanelFollow() {
        val items = arrayOf(getString(R.string.sora_top), getString(R.string.sora_center), getString(R.string.sora_bottom))
        AlertDialog.Builder(this)
            .setTitle(R.string.sora_follow_scrollbar)
            .setSingleChoiceItems(items, -1) { dialog, which ->
                editor.lnPanelPositionMode = LineInfoPanelPositionMode.FOLLOW
                editor.lnPanelPosition = when (which) {
                    0 -> LineInfoPanelPosition.TOP
                    1 -> LineInfoPanelPosition.CENTER
                    2 -> LineInfoPanelPosition.BOTTOM
                    else -> LineInfoPanelPosition.CENTER
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseLanguage() {
        val languageOptions = arrayOf(
            "Java",
            "TextMate Java",
            "TextMate Kotlin",
            "TextMate Python",
            "TextMate Html",
            "TextMate JavaScript",
            "TextMate MarkDown",
            "TM Language from file",
            "Tree-sitter Java",
            "Monarch Java",
            "Monarch Kotlin",
            "Monarch Python",
            "Monarch TypeScript",
            "Text"
        )

        val tmLanguages = mapOf(
            "TextMate Java" to Pair("source.java", "source.java"),
            "TextMate Kotlin" to Pair("source.kotlin", "source.kotlin"),
            "TextMate Python" to Pair("source.python", "source.python"),
            "TextMate Html" to Pair("text.html.basic", "text.html.basic"),
            "TextMate JavaScript" to Pair("source.js", "source.js"),
            "TextMate MarkDown" to Pair("text.html.markdown", "text.html.markdown")
        )

        val monarchLanguages = mapOf(
            "Monarch Java" to "source.java",
            "Monarch Kotlin" to "source.kotlin",
            "Monarch Python" to "source.python",
            "Monarch TypeScript" to "source.typescript"
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.sora_switch_language)
            .setSingleChoiceItems(languageOptions, -1) { dialog, which ->
                when (val selected = languageOptions[which]) {
                    in tmLanguages -> {
                        val info = tmLanguages[selected]!!
                        try {
                            ensureTextmateTheme()
                            val editorLanguage = editor.editorLanguage
                            val language = if (editorLanguage is TextMateLanguage) {
                                editorLanguage.updateLanguage(info.first)
                                editorLanguage
                            } else {
                                TextMateLanguage.create(info.second, true)
                            }
                            editor.setEditorLanguage(language)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    in monarchLanguages -> {
                        val info = monarchLanguages[selected]!!
                        try {
                            ensureMonarchTheme()
                            val editorLanguage = editor.editorLanguage
                            val language = if (editorLanguage is MonarchLanguage) {
                                editorLanguage.updateLanguage(info)
                                editorLanguage
                            } else {
                                MonarchLanguage.create(info, true)
                            }
                            editor.setEditorLanguage(language)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    else -> {
                        when (selected) {
                            "Java" -> editor.setEditorLanguage(JavaLanguage())
                            "Text" -> editor.setEditorLanguage(EmptyLanguage())
                            "TM Language from file" -> loadTMLLauncher.launch("*/*")
                            "Tree-sitter Java" -> {
                                // Use existing provider (keeps completion wrapping consistent)
                                editor.setEditorLanguage(languageProvider.getLanguage(File("dummy.java")))
                            }
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }


    private fun chooseTheme() {
        val themes = arrayOf(
            "Default",
            "GitHub",
            "Eclipse",
            "Darcula",
            "VS2019",
            "NotepadXX",
            "QuietLight for TM(VSCode)",
            "Darcula for TM",
            "Ayu Dark for VSCode",
            "Solarized(Dark) for TM(VSCode)",
            "TM theme from file"
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.sora_color_scheme)
            .setSingleChoiceItems(themes, -1) { dialog, which ->
                when (which) {
                    0 -> editor.colorScheme = EditorColorScheme()
                    1 -> editor.colorScheme = SchemeGitHub()
                    2 -> editor.colorScheme = SchemeEclipse()
                    3 -> editor.colorScheme = SchemeDarcula()
                    4 -> editor.colorScheme = SchemeVS2019()
                    5 -> editor.colorScheme = SchemeNotepadXX()

                    6 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("quietlight")
                    }

                    7 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("darcula")
                    }

                    8 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("ayu-dark")
                    }

                    9 -> {
                        ensureTextmateTheme()
                        ThemeRegistry.getInstance().setTheme("solarized_dark")
                    }

                    10 -> {
                        // Load any TextMate/VSCode theme JSON file from storage
                        loadTMTLauncher.launch("*/*")
                    }
                }

                // Re-apply to refresh editor colors
                val cs = editor.colorScheme
                editor.colorScheme = cs

                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // --- File tree ---
    private fun setupFileTree(rootDir: File? = null) {
        val container = findViewById<View>(R.id.file_tree_container)
        if (container !is android.view.ViewGroup) return

        val root = TreeNode.root()

        var startDir: File = rootDir?.takeIf { it.exists() && it.isDirectory } ?: TermuxConstants.TERMUX_HOME_DIR
        if (!startDir.exists() || !startDir.isDirectory) {
            startDir = filesDir
        }

        val startNode = buildDirNode(startDir)
        val rootItem = startNode.value as? FileTreeNodeViewHolder.FileItem

        // Add root node now; children will be loaded once the view is created (see below).
        root.addChild(startNode)

        // Keep references for auto-refresh
        fileTreeRootNode = startNode
        fileTreeRootDir = startDir
        fileTreeDirLastModified.clear()
        fileTreeDirSnapshot.clear()

        treeView = AndroidTreeView(this, root).apply {
            setUse2dScroll(true)
            setDefaultViewHolder(FileTreeNodeViewHolder::class.java)
            setUseAutoToggle(false)
            setDefaultNodeClickListener { node, value ->
                val item = value as? FileTreeNodeViewHolder.FileItem ?: return@setDefaultNodeClickListener
                if (item.isDirectory) {
                    val dir = File(item.path)
                    if (!item.childrenLoaded) {
                        loadDirectoryChildren(node, dir) {
                            item.childrenLoaded = true
                            // Expand asynchronously to avoid UI freezes on large directories
                            treeView?.expandNodeAsync(node)
                        }
                    } else {
                        // Already loaded, just expand/collapse
                        if (node.isExpanded) {
                            treeView?.collapseNode(node)
                        } else {
                            treeView?.expandNodeAsync(node)
                        }
                    }
                } else {
                    if (item.name.endsWith(".apk", ignoreCase = true)) {
                        com.neonide.studio.app.gradle.ApkInstallUtils.installApk(this@SoraEditorActivityK, File(item.path))
                    } else {
                        openFileInEditor(File(item.path), DisplayNameUtils.safeForUi(item.name))
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                }
            }
            setDefaultNodeLongClickListener { node, value ->
                val item = value as? FileTreeNodeViewHolder.FileItem ?: return@setDefaultNodeLongClickListener false
                val f = File(item.path)
                if (!f.exists()) return@setDefaultNodeLongClickListener false

                val options = mutableListOf<String>()

                if (item.isDirectory) {
                    options.add("Refresh")
                    options.add("New file")
                    options.add("New folder")
                    options.add("Rename")
                    options.add("Delete")
                    options.add("Copy path")
                    options.add("Open in terminal here")
                } else {
                    options.add("Open")
                    options.add("Rename")
                    options.add("Delete")
                    options.add("Copy path")
                    options.add("Open in terminal here")
                    if (f.extension.equals("java", ignoreCase = true)) {
                        options.add("Start Java LSP Server")
                    }
                }

                AlertDialog.Builder(this@SoraEditorActivityK)
                    .setTitle(DisplayNameUtils.safeForUi(item.name))
                    .setItems(options.toTypedArray()) { dialog, which ->
                        when (val choice = options[which]) {
                            "Open" -> {
                                openFileInEditor(f, DisplayNameUtils.safeForUi(item.name))
                                drawerLayout.closeDrawer(GravityCompat.START)
                            }
                            "Start Java LSP Server" -> {
                                runCatching {
                                    startService(Intent(this@SoraEditorActivityK, com.neonide.studio.app.lsp.server.JavaLanguageServerService::class.java))
                                    android.widget.Toast.makeText(this@SoraEditorActivityK, "Java LSP server service started", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            "Refresh" -> {
                                refreshDirectoryNode(node, f)
                            }
                            "New file" -> {
                                promptCreateInDirectory(node, f, isDir = false)
                            }
                            "New folder" -> {
                                promptCreateInDirectory(node, f, isDir = true)
                            }
                            "Rename" -> {
                                promptRenamePath(node, f)
                            }
                            "Delete" -> {
                                promptDeletePath(node, f)
                            }
                            "Copy path" -> {
                                copyPathToClipboard(f.absolutePath)
                            }
                            "Open in terminal here" -> {
                                openTerminalHere(if (f.isDirectory) f else f.parentFile)
                            }
                            else -> {
                                android.util.Log.w("ACS/FileTree", "Unknown choice: $choice")
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()

                true
            }
        }

        container.removeAllViews()
        val treeRootView = treeView!!.view.apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Hook pinch scale gesture to file-tree UI scaling (icons/text/indent), not canvas scaling.
        // Apply scale to currently visible rows in-place for smooth zoom.
        (treeRootView as? com.neonide.studio.view.treeview.view.TwoDScrollView)?.setOnScaleChangedListener(object : com.neonide.studio.view.treeview.view.TwoDScrollView.OnScaleChangedListener {
            private var pendingScale = 1.0f
            private val treeItems by lazy { treeRootView.findViewById<android.view.ViewGroup>(com.neonide.studio.R.id.tree_items) }

            override fun onScaleBegin() {
                pendingScale = 1.0f
            }

            override fun onScale(scaleFactor: Float) {
                // Visual-only scaling during gesture for performance
                pendingScale *= scaleFactor
                treeItems?.scaleX = pendingScale
                treeItems?.scaleY = pendingScale
                treeItems?.pivotX = 0f
                treeItems?.pivotY = 0f
            }

            override fun onScaleEnd() {
                val current = com.neonide.studio.app.FileTreeNodeViewHolder.UI_SCALE
                var next = current * pendingScale
                
                if (next < 0.75f) next = 0.75f
                if (next > 1.75f) next = 1.75f

                // Reset visual scale immediately to allow layout update
                treeItems?.scaleX = 1.0f
                treeItems?.scaleY = 1.0f

                if (kotlin.math.abs(next - current) < 0.05f) return

                com.neonide.studio.app.FileTreeNodeViewHolder.UI_SCALE = next

                val ctx = this@SoraEditorActivityK
                
                // Update views in chunks to prevent freeze
                uiScope.launch(Dispatchers.Main) {
                    val stack = ArrayDeque<TreeNode>()
                    fileTreeRootNode?.let { stack.add(it) }
                    
                    var count = 0
                    while (stack.isNotEmpty()) {
                        val node = stack.removeLast()
                        
                        // Update
                        val vh = node.viewHolder
                        val cached = vh?.cachedView
                        val wrapper = cached as? com.neonide.studio.view.treeview.view.TreeNodeWrapperView
                        val row = wrapper?.nodeContainer?.getChildAt(0)
                        if (row != null) {
                            com.neonide.studio.app.FileTreeNodeViewHolder.applyScaleToRowView(ctx, row, node.level)
                        }

                        // Add children to stack
                        if (node.isExpanded) {
                            val children = node.children
                            for (i in children.indices.reversed()) {
                                stack.add(children[i])
                            }
                        }

                        // Yield every 20 items to keep UI responsive
                        count++
                        if (count % 20 == 0) kotlinx.coroutines.yield()
                    }
                    
                    treeItems?.requestLayout()
                    treeItems?.invalidate()
                }
            }
        })

        container.addView(treeRootView)

        // Initial expansion (load root directory once, then expand)
        loadDirectoryChildren(startNode, startDir) {
            rootItem?.childrenLoaded = true
            treeView?.expandNodeAsync(startNode)
        }
    }

    private fun buildDirNode(dir: File): TreeNode {
        val item = FileTreeNodeViewHolder.FileItem(
            if (dir.name.isEmpty()) dir.absolutePath else dir.name,
            dir.absolutePath,
            true
        ).apply { childrenLoaded = false }

        return TreeNode(item).setViewHolder(FileTreeNodeViewHolder(this))
    }

    private fun autoRefreshFileTreeOnce() {
        val rootNode = fileTreeRootNode ?: return

        // Refresh only directories that have been loaded already.
        // This keeps the operation cheap even for large projects.
        fun visit(node: TreeNode) {
            val item = node.value as? FileTreeNodeViewHolder.FileItem
            if (item != null && item.isDirectory) {
                // If children were loaded, rebuild them only if directory content changed.
                if (item.childrenLoaded) {
                    val dir = File(item.path)

                    // Quick check: if lastModified unchanged, skip.
                    val last = runCatching { dir.lastModified() }.getOrDefault(0L)
                    val prevLast = fileTreeDirLastModified[item.path]
                    if (prevLast != null && prevLast == last) {
                        // no-op
                    } else {
                        // Take a cheap snapshot of the directory listing to confirm real changes.
                        // This avoids unnecessary refreshes that can cause UI flicker.
                        val snapshot = runCatching {
                            SafeDirLister.listFiles(dir)
                                .take(800)
                                .joinToString("\n") { f ->
                                    (if (f.isDirectory) "D:" else "F:") + f.name
                                }
                        }.getOrDefault("")

                        val prevSnap = fileTreeDirSnapshot[item.path]
                        if (prevSnap == null || prevSnap != snapshot) {
                            fileTreeDirLastModified[item.path] = last
                            fileTreeDirSnapshot[item.path] = snapshot
                            runCatching { refreshDirectoryNode(node, dir) }
                        } else {
                            // lastModified changed but listing is effectively the same
                            fileTreeDirLastModified[item.path] = last
                        }
                    }
                }

                // Only traverse deeper into expanded nodes.
                if (node.isExpanded) {
                    node.children.forEach { visit(it) }
                }
            }
        }

        visit(rootNode)
    }

    private fun refreshDirectoryNode(dirNode: TreeNode, dir: File) {
        // Preserve expansion state *within this subtree* before clearing children.
        val expandedDirPaths = mutableSetOf<String>()
        collectExpandedDirPaths(dirNode, expandedDirPaths)
        val wasExpanded = dirNode.isExpanded

        // Clear children immediately
        dirNode.clearChildren()

        // Load asynchronously
        loadDirectoryChildren(dirNode, dir) {
            // Mark as loaded
            (dirNode.value as? FileTreeNodeViewHolder.FileItem)?.childrenLoaded = true

            // If it was expanded, re-expand to show new children using async batching
            if (wasExpanded) {
                treeView?.expandNodeAsync(dirNode)
            }

            // Restore expanded directories under this node (best-effort).
            restoreExpandedDirPaths(dirNode, expandedDirPaths)
        }
    }

    /** Save the expanded/collapsed state for the whole tree view. */
    private fun saveFileTreeState(): String? = treeView?.saveState

    /** Restore expanded/collapsed state for the whole tree view (best-effort). */
    private fun restoreFileTreeState(state: String?) {
        if (state.isNullOrBlank()) return
        runCatching { treeView?.restoreState(state) }
    }

    /** Collect absolute paths of expanded directories in [node]'s subtree. */
    private fun collectExpandedDirPaths(node: TreeNode, out: MutableSet<String>) {
        for (c in node.children) {
            val item = c.value as? FileTreeNodeViewHolder.FileItem ?: continue
            if (item.isDirectory && c.isExpanded) {
                out.add(item.path)
                collectExpandedDirPaths(c, out)
            }
        }
    }

    /** Restore expanded directories by re-loading and expanding them as needed (best-effort). */
    private fun restoreExpandedDirPaths(node: TreeNode, expandedPaths: Set<String>) {
        for (c in node.children) {
            val item = c.value as? FileTreeNodeViewHolder.FileItem ?: continue
            if (!item.isDirectory) continue
            if (!expandedPaths.contains(item.path)) continue

            val dir = File(item.path)
            if (!dir.exists() || !dir.isDirectory) continue

            if (!item.childrenLoaded) {
                loadDirectoryChildren(c, dir) {
                    item.childrenLoaded = true
                    treeView?.expandNodeAsync(c)
                    restoreExpandedDirPaths(c, expandedPaths)
                }
            } else {
                treeView?.expandNodeAsync(c)
                restoreExpandedDirPaths(c, expandedPaths)
            }
        }
    }

    private fun copyPathToClipboard(path: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cm == null) {
            android.widget.Toast.makeText(this, "Clipboard not available", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        cm.setPrimaryClip(ClipData.newPlainText("path", path))
        android.widget.Toast.makeText(this, "Path copied", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun promptRenamePath(node: TreeNode, f: File) {
        val parent = f.parentFile
        if (parent == null) {
            android.widget.Toast.makeText(this, "Cannot rename this item", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val input = android.widget.EditText(this).apply {
            setText(f.name)
            setSelection(text?.length ?: 0)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isBlank()) {
                    android.widget.Toast.makeText(this, "Name required", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val target = File(parent, newName)
                if (target.exists()) {
                    android.widget.Toast.makeText(this, "Target already exists", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val ok = runCatching { f.renameTo(target) }.getOrDefault(false)
                if (!ok) {
                    android.widget.Toast.makeText(this, "Rename failed", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    // If current file was renamed, update selection/highlight.
                    if (currentFile?.absolutePath == f.absolutePath) {
                        currentFile = target
                        com.neonide.studio.app.FileTreeNodeViewHolder.SELECTED_FILE_PATH = target.absolutePath
                        refreshFileTreeSelectionHighlight()
                        supportActionBar?.title = target.name
                    }
                }

                // Refresh parent directory listing
                node.parent?.let { parentNode ->
                    refreshDirectoryNode(parentNode, parent)
                }

                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptDeletePath(node: TreeNode, f: File) {
        val parent = f.parentFile

        AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Delete ${DisplayNameUtils.safeForUi(f.name)}?")
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val ok = runCatching {
                    if (f.isDirectory) f.deleteRecursively() else f.delete()
                }.getOrDefault(false)

                if (!ok) {
                    android.widget.Toast.makeText(this, "Delete failed", android.widget.Toast.LENGTH_SHORT).show()
                }

                // If deleted current file, clear highlight.
                if (currentFile?.absolutePath == f.absolutePath) {
                    currentFile = null
                    com.neonide.studio.app.FileTreeNodeViewHolder.SELECTED_FILE_PATH = null
                    refreshFileTreeSelectionHighlight()
                }

                // Refresh parent directory listing
                if (parent != null) {
                    node.parent?.let { parentNode ->
                        refreshDirectoryNode(parentNode, parent)
                    }
                }

                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openTerminalHere(dir: File?) {
        val d = dir?.takeIf { it.exists() && it.isDirectory }
        if (d == null) {
            android.widget.Toast.makeText(this, "Directory not found", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val bashPath = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash"
        val executableUri = Uri.Builder()
            .scheme(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE)
            .path(bashPath)
            .build()

        val execIntent = Intent(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, executableUri)
        execIntent.setClass(this, TermuxService::class.java)
        execIntent.putExtra(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_WORKDIR, d.absolutePath)
        execIntent.putExtra(
            TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_RUNNER,
            ExecutionCommand.Runner.TERMINAL_SESSION.getName()
        )
        // Use a new session and bring Termux UI to foreground
        execIntent.putExtra(
            TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_SESSION_ACTION,
            TermuxConstants.TERMUX_APP.TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY.toString()
        )
        execIntent.putExtra(
            TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE,
            ExecutionCommand.ShellCreateMode.ALWAYS.getMode()
        )
        execIntent.putExtra(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_SHELL_NAME, "file-tree")
        execIntent.putExtra(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Terminal")
        execIntent.putExtra(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_COMMAND_DESCRIPTION, d.absolutePath)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(execIntent)
        } else {
            startService(execIntent)
        }

        runCatching { startActivity(Intent(this, TermuxActivity::class.java)) }
    }

    /** Update backgrounds for currently visible nodes to reflect SELECTED_FILE_PATH. */
    private fun refreshFileTreeSelectionHighlight() {
        val root = fileTreeRootNode ?: return
        val selected = com.neonide.studio.app.FileTreeNodeViewHolder.SELECTED_FILE_PATH

        val stack = ArrayDeque<TreeNode>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val item = node.value as? FileTreeNodeViewHolder.FileItem

            val vh = node.viewHolder
            val cached = vh?.cachedView
            val wrapper = cached as? com.neonide.studio.view.treeview.view.TreeNodeWrapperView
            val row = wrapper?.nodeContainer?.getChildAt(0)

            if (row != null && item != null && !item.isDirectory) {
                val isSel = selected != null && selected == item.path
                row.setBackgroundResource(if (isSel) R.drawable.file_tree_item_bg_selected else R.drawable.file_tree_item_bg)
            }

            if (node.isExpanded) {
                val children = node.children
                for (i in children.indices.reversed()) {
                    stack.add(children[i])
                }
            }
        }
    }

    private fun promptCreateInDirectory(dirNode: TreeNode, dir: File, isDir: Boolean) {
        val input = android.widget.EditText(this).apply {
            hint = if (isDir) "Folder name" else "File name"
        }
        AlertDialog.Builder(this)
            .setTitle(if (isDir) "New folder" else "New file")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    android.widget.Toast.makeText(this, "Name required", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val target = File(dir, name)
                val ok = runCatching {
                    if (isDir) target.mkdirs() else target.createNewFile()
                }.getOrDefault(false)

                if (!ok) {
                    android.widget.Toast.makeText(this, "Failed to create", android.widget.Toast.LENGTH_SHORT).show()
                }

                refreshDirectoryNode(dirNode, dir)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadDirectoryChildren(dirNode: TreeNode, dir: File, onLoaded: (() -> Unit)? = null) {
        // Clear any existing children to avoid accidental duplication.
        // (Children are lazily loaded; this makes refresh+rebuild safe.)
        dirNode.clearChildren()

        uiScope.launch(Dispatchers.IO) {
            val lastModified = runCatching { dir.lastModified() }.getOrDefault(0L)
            val files = SafeDirLister.listFiles(dir)
                .sortedWith { a, b ->
                    when {
                        a.isDirectory && !b.isDirectory -> -1
                        !a.isDirectory && b.isDirectory -> 1
                        else -> a.name.compareTo(b.name, ignoreCase = true)
                    }
                }

            // Generate snapshot for auto-refresh logic
            val snapshot = files.take(800).joinToString("\n") { f ->
                (if (f.isDirectory) "D:" else "F:") + f.name
            }

            val limit = 500
            val childrenToAdd = files.take(limit).map { f ->
                val isDir = f.isDirectory
                val item = FileTreeNodeViewHolder.FileItem(f.name, f.absolutePath, isDir)
                if (isDir) item.childrenLoaded = false
                item
            }
            
            withContext(Dispatchers.Main) {
                // Update cache to prevent immediate auto-refresh
                fileTreeDirLastModified[dir.absolutePath] = lastModified
                fileTreeDirSnapshot[dir.absolutePath] = snapshot

                for (item in childrenToAdd) {
                     val node = TreeNode(item).setViewHolder(FileTreeNodeViewHolder(this@SoraEditorActivityK))
                     dirNode.addChild(node)
                }
                onLoaded?.invoke()
            }
        }
    }

    fun navigateTo(uri: String, line: Int, column: Int) {
        val file = if (uri.startsWith("file://")) {
            File(java.net.URI.create(uri))
        } else {
            File(uri)
        }

        if (!file.exists()) {
            android.widget.Toast.makeText(this, "File does not exist: ${file.absolutePath}", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        openFileInEditor(file, file.name)
        editor.post {
            editor.setSelection(line, column)
            editor.ensurePositionVisible(line, column)
        }
    }

    private fun onContextMenuCreated(event: CreateContextMenuEvent) {
        // For editor-lsp we don't gate on a separate status; menu actions will no-op if not connected.

        event.menu.add(0, MENU_ID_DEFINITION, 0, getString(R.string.acs_menu_go_to_definition))
            .setOnMenuItemClickListener {
                handleGoToDefinition(event.position.line, event.position.column)
                true
            }
        event.menu.add(0, MENU_ID_REFERENCES, 0, getString(R.string.acs_menu_find_references))
            .setOnMenuItemClickListener {
                handleFindReferences(event.position.line, event.position.column)
                true
            }

        event.menu.add(0, MENU_ID_HOVER, 0, "Show documentation")
            .setOnMenuItemClickListener {
                handleShowHover(event.position.line, event.position.column)
                true
            }
    }

    private fun handleGoToDefinition(line: Int, column: Int) {
        val f = currentFile ?: return
        // Use editor-lsp aggregated request manager
        val lspEditor = lspController.currentEditor() ?: return
        val rm = lspEditor.requestManager ?: return
        val params = org.eclipse.lsp4j.DefinitionParams().apply {
            textDocument = org.eclipse.lsp4j.TextDocumentIdentifier(f.toURI().toString())
            position = org.eclipse.lsp4j.Position(line, column)
        }
        rm.definition(params)?.thenAccept { result ->
            uiScope.launch(Dispatchers.Main) {
                val locations = if (result.isLeft) {
                    result.left
                } else {
                    result.right.map { org.eclipse.lsp4j.Location(it.targetUri, it.targetRange) }
                }

                if (locations.isEmpty()) {
                    android.widget.Toast.makeText(this@SoraEditorActivityK, "No definition found", android.widget.Toast.LENGTH_SHORT).show()
                } else if (locations.size == 1) {
                    val loc = locations[0]
                    navigateTo(loc.uri, loc.range.start.line, loc.range.start.character)
                } else {
                    val items = locations.map { loc ->
                        NavigationItem(loc.uri, loc.range.start.line, loc.range.start.character, "${File(java.net.URI.create(loc.uri)).name}:${loc.range.start.line + 1}")
                    }
                    bottomSheetVm.setNavigationResults(items)
                    showNavigationTab()
                }
            }
        }
    }

    private fun handleShowHover(line: Int, column: Int) {
        val f = currentFile ?: return
        val lspEditor = lspController.currentEditor() ?: return
        val rm = lspEditor.requestManager ?: return

        android.util.Log.d(
            "SoraEditorHover",
            "Request hover for ${f.name} at ${line + 1}:${column + 1} (connected=${lspEditor.isConnected})"
        )

        val params = org.eclipse.lsp4j.HoverParams().apply {
            textDocument = org.eclipse.lsp4j.TextDocumentIdentifier(f.toURI().toString())
            position = org.eclipse.lsp4j.Position(line, column)
        }

        // Make hover appear immediately when explicitly requested
        lspEditor.hoverWindow?.HOVER_TOOLTIP_SHOW_TIMEOUT = 0L

        rm.hover(params)?.thenAccept { hover ->
            android.util.Log.d(
                "SoraEditorHover",
                "Hover response: ${if (hover == null) "<null>" else "hasContents=" + (hover.contents != null) + ", range=" + (hover.range)}"
            )
            // Use editor-lsp built-in hover window rendering
            lspEditor.showHover(hover)
        }
    }

    private fun handleFindReferences(line: Int, column: Int) {
        val f = currentFile ?: return
        val lspEditor = lspController.currentEditor() ?: return
        val rm = lspEditor.requestManager ?: return
        val params = org.eclipse.lsp4j.ReferenceParams().apply {
            textDocument = org.eclipse.lsp4j.TextDocumentIdentifier(f.toURI().toString())
            position = org.eclipse.lsp4j.Position(line, column)
            context = org.eclipse.lsp4j.ReferenceContext(true)
        }
        rm.references(params)?.thenAccept { locationsNullable ->
            val locations = locationsNullable?.filterNotNull() ?: emptyList()
            uiScope.launch(Dispatchers.Main) {
                if (locations.isEmpty()) {
                    android.widget.Toast.makeText(this@SoraEditorActivityK, "No references found", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val items = locations.map { loc ->
                        val fileName = File(java.net.URI.create(loc.uri)).name
                        val lineContent = if (loc.uri == f.toURI().toString()) {
                            editor.text.getLineString(loc.range.start.line).trim()
                        } else {
                            ""
                        }
                        NavigationItem(loc.uri, loc.range.start.line, loc.range.start.character, "$fileName:${loc.range.start.line + 1} $lineContent")
                    }
                    bottomSheetVm.setNavigationResults(items)
                    showNavigationTab()
                }
            }
        }
    }

    private fun showNavigationTab() {
        val sheet = findViewById<View>(R.id.acs_bottom_sheet)
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        
        val viewPager = findViewById<ViewPager2>(R.id.acs_bottom_sheet_pager)
        viewPager.currentItem = TAB_INDEX_REFERENCES
    }

    private fun openFileInEditor(file: File, title: String) {
        currentFile = file

        // Update file tree highlight
        runCatching {
            com.neonide.studio.app.FileTreeNodeViewHolder.SELECTED_FILE_PATH = file.absolutePath
            refreshFileTreeSelectionHighlight()
        }

        // Load file content
        editor.setText(readFileText(file.absolutePath))
        supportActionBar?.title = title

        val ext = file.extension.lowercase()

        val languageForEditor = languageProvider.getLanguage(file)

        editor.setEditorLanguage(languageForEditor)

        // Attach LSP by default for java/kotlin/xml
        if (ext == "java" || ext == "kt" || ext == "kts" || ext == "xml") {
            runCatching {
                // For Java: attach with TsLanguage (or TextMate fallback)
                lspController.attach(editor, file, languageForEditor, projectRoot)
            }
        } else {
            runCatching { lspController.detach() }
        }

        // XML: load framework attribute index for ACS-like android:* completions
        if (ext == "xml") {
            // Ensure index is loaded asynchronously (heavy I/O)
            uiScope.launch(Dispatchers.IO) {
                val ok = com.neonide.studio.app.editor.xml.framework.AndroidFrameworkAttrIndex.ensureLoaded(this@SoraEditorActivityK)
                if (ok) {
                    // Inject provider into XML enhancer (raw names without "android:")
                    // Snapshot to avoid allocating a new List on every completion request
                    val snapshot = com.neonide.studio.app.editor.xml.framework.AndroidFrameworkAttrIndex.allAttrs().toList()
                    com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer.setAndroidFrameworkAttrsProvider {
                        snapshot
                    }
                }
            }
        } else {
            // Not XML: clear provider to avoid unnecessary memory use
            com.neonide.studio.app.editor.xml.AndroidXmlLanguageEnhancer.setAndroidFrameworkAttrsProvider(null)
        }

        updatePositionText()
        updateBtnState()
    }

    private fun readFileText(absolutePath: String): String {
        return try {
            BufferedReader(InputStreamReader(FileInputStream(absolutePath), StandardCharsets.UTF_8)).use { br ->
                buildString {
                    var line: String?
                    while (true) {
                        line = br.readLine() ?: break
                        append(line).append('\n')
                    }
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
}

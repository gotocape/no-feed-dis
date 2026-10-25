package com.haijiao.nofeeddis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.haijiao.nofeeddis.databinding.ActivityMainBinding

/**
 * 首页。整页就三样东西：输入框、平台、搜索按钮。
 *
 * 这里没有 RecyclerView、没有 WebView、没有图片加载、没有任何「加载更多」的东西。
 * 页面上的每一个像素都是本地渲染出来的，不存在自动拉取内容的可能。
 *
 * 平台列表不是写死的：来自 [PlatformRepository.visible]，
 * 用户在设置里隐藏的、自建的平台都会实时体现在这里。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var repo: PlatformRepository

    private var selected: Platform? = null
    private val platformByChipId = LinkedHashMap<Int, Platform>()
    private var keyboardShownOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        repo = PlatformRepository(prefs)
        binding.root.applySystemBarPadding(horizontalDp = 20, verticalDp = 16)

        wireActions()

        // Chip 的构建、选中恢复、按钮状态统一放到 onResume，
        // 因为 onResume 必然紧跟在 onCreate 之后，也覆盖了「从设置页返回」的场景。
        // 在这里再写一遍只会重建两次。

        // 先拿到输入焦点，但键盘等窗口真正获得焦点后再弹（见 onWindowFocusChanged）
        binding.keywordInput.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        // 从设置页回来时，平台的可见性/名称/规则可能都变了，整块重建
        val previousId = selected?.id
        buildChips()
        restoreSelection(preferredId = previousId)
        updateSearchButton()
    }

    /**
     * 冷启动时自动弹键盘，让用户打开就能直接打字。
     *
     * 为什么不用 onCreate 里的 postDelayed：那时窗口还没拿到焦点，
     * showSoftInput 在部分设备上会静默失败。
     * 用一次性标志保证只弹一次 —— 从目标 App 搜完返回时不再弹，否则一回来就被键盘挡住。
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !keyboardShownOnce) {
            keyboardShownOnce = true
            binding.keywordInput.post {
                binding.keywordInput.requestFocus()
                getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(binding.keywordInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun buildChips() {
        binding.platformGroup.removeAllViews()
        platformByChipId.clear()

        val visiblePlatforms = repo.visible()

        visiblePlatforms.forEach { platform ->
            val chip = layoutInflater
                .inflate(R.layout.item_chip, binding.platformGroup, false) as Chip
            chip.id = View.generateViewId()
            chip.text = platform.name
            platformByChipId[chip.id] = platform
            binding.platformGroup.addView(chip)
        }

        // 全部平台都被隐藏（或都被删掉）时的空态
        val empty = visiblePlatforms.isEmpty()
        binding.platformGroup.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptyHint.visibility = if (empty) View.VISIBLE else View.GONE

        binding.platformGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selected = checkedIds.firstOrNull()?.let(platformByChipId::get)
            updateSearchButton()
        }
    }

    /**
     * 恢复选中的平台，三层回退：
     * 1. 刚在这一页选过的那一个（从设置页返回时保持原样）
     * 2. 上次真正跳转成功的那个
     * 3. 第一个可见平台
     *
     * 第 2 步必须再过一次「是否可见 / 是否还存在」的检查 ——
     * 否则用户隐藏或删掉了某个平台后，它会指向一个已经不在 Chip 组里的 id。
     */
    private fun restoreSelection(preferredId: String? = null) {
        val remembered = when {
            preferredId != null -> preferredId
            prefs.rememberLast -> prefs.lastPlatformId
            else -> null
        }

        val target = platformByChipId.values.firstOrNull { it.id == remembered }
            ?: platformByChipId.values.firstOrNull()

        selected = target
        platformByChipId.entries
            .firstOrNull { it.value.id == target?.id }
            ?.let { binding.platformGroup.check(it.key) }
    }

    private fun wireActions() {
        binding.keywordInput.doAfterTextChanged { updateSearchButton() }

        binding.keywordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.searchButton.setOnClickListener { performSearch() }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun updateSearchButton() {
        val hasKeyword = !binding.keywordInput.text.isNullOrBlank()
        binding.searchButton.isEnabled = hasKeyword && selected != null
    }

    private fun performSearch() {
        val platform = selected ?: return
        val keyword = binding.keywordInput.text?.toString().orEmpty()

        when (val result = JumpEngine.jump(this, platform, repo.urisOf(platform), keyword)) {
            is JumpEngine.Result.Success -> {
                if (prefs.rememberLast) {
                    prefs.lastPlatformId = platform.id
                }
                if (result.truncated) {
                    toast(getString(R.string.error_truncated, JumpEngine.MAX_KEYWORD_CODE_POINTS))
                }
            }

            JumpEngine.Result.EmptyKeyword -> toast(R.string.error_no_keyword)

            JumpEngine.Result.AllFailed -> showJumpFailedDialog(platform)
        }
    }

    /**
     * 跳转失败只有两种可能：没装 App，或 URI 规则失效。
     * 系统抛的异常分不出这两者，所以文案得把两种都说清楚。
     */
    private fun showJumpFailedDialog(platform: Platform) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.jump_failed_title)
            .setMessage(getString(R.string.jump_failed_message, platform.name))
            .setPositiveButton(R.string.jump_failed_goto_settings) { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton(R.string.jump_failed_ok, null)
            .show()
    }
}

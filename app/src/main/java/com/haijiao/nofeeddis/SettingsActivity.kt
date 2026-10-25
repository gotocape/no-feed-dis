package com.haijiao.nofeeddis

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.haijiao.nofeeddis.databinding.ActivitySettingsBinding

/**
 * 设置页。三件事：
 * 1. 每个平台一个「显示在首页」开关
 * 2. 点平台改跳转规则，规则可以逐条测试
 * 3. 新建自定义平台，让别人也能自己加
 *
 * 为什么必须能逐条测试：Android 只在「App 被唤起」时返回成功，
 * 不告诉你落在首页还是搜索结果页。哪条 URI 真管用，只能人眼确认一次。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private lateinit var repo: PlatformRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        repo = PlatformRepository(prefs)
        binding.root.applySystemBarPadding(horizontalDp = 20, verticalDp = 8)

        binding.backButton.setOnClickListener { finish() }

        binding.rememberSwitch.isChecked = prefs.rememberLast
        binding.rememberSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.rememberLast = isChecked
        }

        binding.newPlatformButton.setOnClickListener { showPlatformDialog(null) }

        renderPlatforms()
    }

    // ---------------------------------------------------------------- 列表

    private fun renderPlatforms() {
        binding.platformContainer.removeAllViews()

        repo.all().forEach { platform ->
            val row = layoutInflater.inflate(
                R.layout.item_platform_row,
                binding.platformContainer,
                false
            )

            row.findViewById<TextView>(R.id.nameView).text = platform.name
            row.findViewById<TextView>(R.id.summaryView).text = buildSummary(platform)

            val visibleSwitch = row.findViewById<MaterialSwitch>(R.id.visibleSwitch)
            // 先赋值再挂监听，否则会被 setChecked 触发一次误写
            visibleSwitch.isChecked = repo.isVisible(platform)
            visibleSwitch.setOnCheckedChangeListener { _, checked ->
                repo.setVisible(platform, checked)
            }

            row.setOnClickListener { showPlatformDialog(platform) }
            binding.platformContainer.addView(row)
        }
    }

    private fun buildSummary(platform: Platform): String = buildString {
        val uris = repo.urisOf(platform)
        append(uris.firstOrNull().orEmpty())
        if (uris.size > 1) {
            append("  ·  ")
            append(getString(R.string.row_more_rules, uris.size))
        }
        if (platform.custom) {
            append("  ·  ")
            append(getString(R.string.row_custom_badge))
        } else if (repo.isCustomized(platform)) {
            append("  ·  ")
            append(getString(R.string.row_customized))
        }
    }

    // ---------------------------------------------------------------- 编辑对话框

    /** [existing] 为 null 表示新建。 */
    private fun showPlatformDialog(existing: Platform?) {
        val isNew = existing == null
        val isCustom = existing?.custom == true
        // 名称/包名只对「新建」和「自定义」开放，内置平台的包名写死在代码里
        val metaEditable = isNew || isCustom

        val nameInput = AppCompatEditText(this).apply {
            hint = getString(R.string.field_name_hint)
            setText(existing?.name.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            textSize = 16f
        }

        val pkgInput = AppCompatEditText(this).apply {
            hint = getString(R.string.field_pkg_hint)
            setText(existing?.pkg.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            typeface = Typeface.MONOSPACE
            textSize = 12f
        }

        val rowContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun addUriRow(text: String) {
            val row = layoutInflater.inflate(R.layout.item_uri_row, rowContainer, false)
            row.findViewById<AppCompatEditText>(R.id.uriInput).setText(text)

            row.findViewById<TextView>(R.id.testButton).setOnClickListener {
                val template = readUri(row)
                when {
                    template.isEmpty() -> toast(R.string.error_uri_empty)
                    !JumpEngine.hasPlaceholder(template) -> toast(R.string.error_uri_no_placeholder)
                    else -> testOne(buildPreviewPlatform(existing, nameInput, pkgInput), template)
                }
            }

            row.findViewById<TextView>(R.id.removeButton).setOnClickListener {
                if (rowContainer.childCount <= 1) {
                    toast(R.string.error_rule_empty)
                } else {
                    rowContainer.removeView(row)
                }
            }

            rowContainer.addView(row)
        }

        val initialUris = existing?.let { repo.urisOf(it) } ?: listOf("")
        initialUris.forEach { addUriRow(it) }

        val addRowButton = TextView(this).apply {
            text = getString(R.string.uri_add_row)
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
            setPadding(0, dp(14), 0, dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { addUriRow("") }
        }

        val deleteButton = TextView(this).apply {
            text = getString(R.string.action_delete_platform)
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.error))
            setPadding(0, dp(18), 0, dp(8))
            isClickable = true
            isFocusable = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
            if (metaEditable) {
                addView(sectionLabel(R.string.field_name))
                addView(nameInput)
                addView(sectionLabel(R.string.field_pkg))
                addView(pkgInput)
            }
            addView(sectionLabel(R.string.field_uris))
            addView(rowContainer)
            addView(addRowButton)
            if (isCustom) addView(deleteButton)
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(
                if (isNew) {
                    getString(R.string.dialog_new_platform_title)
                } else {
                    getString(R.string.dialog_edit_platform_title, existing!!.name)
                }
            )
            .setView(content)
            .setPositiveButton(R.string.dialog_save, null)
            .setNegativeButton(android.R.string.cancel, null)

        if (!isNew && !isCustom) {
            builder.setNeutralButton(R.string.dialog_reset, null)
        }

        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val pkg = pkgInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                val uris = collectUris(rowContainer)

                if (metaEditable) {
                    if (name.isEmpty()) {
                        toast(R.string.error_name_empty)
                        return@setOnClickListener
                    }
                    if (name.length > 8) {
                        toast(R.string.error_name_too_long)
                        return@setOnClickListener
                    }
                }
                if (uris.isEmpty()) {
                    toast(R.string.error_rule_empty)
                    return@setOnClickListener
                }
                if (uris.any { !JumpEngine.hasPlaceholder(it) }) {
                    toast(R.string.error_uri_no_placeholder)
                    return@setOnClickListener
                }

                when {
                    isNew -> {
                        val created = repo.createCustom(name, pkg, uris)
                        repo.setVisible(created, true)
                        toast(getString(R.string.msg_platform_created, name))
                    }

                    isCustom -> {
                        repo.updateCustom(existing!!, name = name, pkg = pkg, uris = uris)
                        toast(R.string.msg_saved)
                    }

                    else -> {
                        repo.saveUris(existing!!, uris)
                        toast(R.string.msg_saved)
                    }
                }

                renderPlatforms()
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                repo.resetUris(existing ?: return@setOnClickListener)
                renderPlatforms()
                toast(R.string.msg_reset)
                dialog.dismiss()
            }

            if (isCustom) {
                deleteButton.setOnClickListener {
                    confirmDelete(existing!!, dialog)
                }
            }
        }

        dialog.show()
    }

    private fun confirmDelete(platform: Platform, parent: AlertDialog) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.confirm_delete_title, platform.name))
            .setMessage(R.string.confirm_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                repo.deleteCustom(platform)
                renderPlatforms()
                toast(R.string.msg_platform_deleted)
                parent.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 测试用的临时平台对象。
     * 新建时平台还没落库，但测试只需要 name / pkg，所以现拼一个就够了 ——
     * 这样用户在保存前就能验证规则，不用先存一次再改。
     */
    private fun buildPreviewPlatform(
        existing: Platform?,
        nameInput: AppCompatEditText,
        pkgInput: AppCompatEditText
    ): Platform {
        val name = nameInput.text?.toString()?.trim().orEmpty()
        val pkg = pkgInput.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return Platform(
            id = existing?.id ?: "preview",
            name = name.ifBlank { existing?.name ?: "自定义" },
            pkg = pkg ?: existing?.pkg,
            defaultUris = existing?.defaultUris.orEmpty(),
            altPkgs = existing?.altPkgs.orEmpty(),
            custom = true
        )
    }

    private fun testOne(platform: Platform, template: String) {
        // 用「测试」两个字当关键词，比空白更容易看出有没有真的搜过去
        when (JumpEngine.jump(this, platform, listOf(template), TEST_KEYWORD)) {
            is JumpEngine.Result.Success ->
                toast(getString(R.string.msg_test_ok, platform.name))

            JumpEngine.Result.EmptyKeyword -> toast(R.string.error_no_keyword)

            JumpEngine.Result.AllFailed -> toast(R.string.msg_test_fail)
        }
    }

    private fun sectionLabel(@StringRes textRes: Int): TextView = TextView(this).apply {
        setText(textRes)
        textSize = 12f
        setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
        setPadding(0, dp(14), 0, dp(2))
    }

    private fun collectUris(rowContainer: LinearLayout): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until rowContainer.childCount) {
            val text = readUri(rowContainer.getChildAt(i))
            if (text.isNotEmpty()) result += text
        }
        return result
    }

    /** 读一行输入。URI 本身不能含换行，输入框允许折行只是为了好看。 */
    private fun readUri(row: View): String =
        row.findViewById<AppCompatEditText>(R.id.uriInput)
            .text?.toString().orEmpty()
            .replace("\r", "")
            .replace("\n", "")
            .trim()

    companion object {
        private const val TEST_KEYWORD = "测试"
    }
}

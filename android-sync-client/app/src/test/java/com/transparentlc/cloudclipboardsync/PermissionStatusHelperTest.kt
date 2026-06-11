package com.transparentlc.cloudclipboardsync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStatusHelperTest {
    private val target = "com.transparentlc.cloudclipboardsync/com.transparentlc.cloudclipboardsync.ClipboardAccessAccessibilityService"
    private val imeTarget = "com.transparentlc.cloudclipboardsync/com.transparentlc.cloudclipboardsync.ClipboardInputMethodService"

    @Test
    fun accessibilityServiceMatchesExactTargetInColonSeparatedSetting() {
        val enabledServices = listOf(
            "com.wangc.bill/com.google.android.accessibility.selecttospeak.SelectToSpeakService",
            target,
            "li.songe.gkd/com.google.android.accessibility.selecttospeak.SelectToSpeakService",
        ).joinToString(":")

        assertTrue(PermissionStatusHelper.isAccessibilityServiceEnabledInSetting(enabledServices, target))
    }

    @Test
    fun accessibilityServiceMatchIgnoresCaseAndWhitespace() {
        val enabledServices = "  ${target.uppercase()}  "

        assertTrue(PermissionStatusHelper.isAccessibilityServiceEnabledInSetting(enabledServices, target))
    }

    @Test
    fun accessibilityServiceDoesNotMatchOtherServices() {
        val enabledServices = "com.transparentlc.cloudclipboardsync/com.transparentlc.cloudclipboardsync.OtherService:" +
            "li.songe.gkd/com.google.android.accessibility.selecttospeak.SelectToSpeakService"

        assertFalse(PermissionStatusHelper.isAccessibilityServiceEnabledInSetting(enabledServices, target))
    }

    @Test
    fun accessibilityServiceDoesNotMatchBlankSettingOrTarget() {
        assertFalse(PermissionStatusHelper.isAccessibilityServiceEnabledInSetting(null, target))
        assertFalse(PermissionStatusHelper.isAccessibilityServiceEnabledInSetting("", target))
        assertFalse(PermissionStatusHelper.isAccessibilityServiceEnabledInSetting(target, " "))
    }

    @Test
    fun accessibilityServiceMatchesManagerServiceList() {
        val services = listOf(
            "com.example.other/com.example.other.OtherAccessibilityService",
            target,
        )

        assertTrue(PermissionStatusHelper.isAccessibilityServiceEnabledInServiceList(services, target))
    }

    @Test
    fun accessibilityServiceManagerListDoesNotMatchSimilarPackage() {
        val services = listOf(
            "com.transparentlc.cloudclipboardsync/com.transparentlc.cloudclipboardsync.OtherService",
            "com.transparentlc.cloudclipboardsync.debug/com.transparentlc.cloudclipboardsync.ClipboardAccessAccessibilityService",
        )

        assertFalse(PermissionStatusHelper.isAccessibilityServiceEnabledInServiceList(services, target))
    }

    @Test
    fun accessibilityStateLabelDescribesSource() {
        assertEquals(
            "已开启（系统设置 + 服务已生效）",
            PermissionStatusHelper.accessibilityStateLabel(enabledInSetting = true, enabledInManager = true),
        )
        assertEquals("已开启（系统服务枚举）", PermissionStatusHelper.accessibilityStateLabel(enabledInSetting = false, enabledInManager = true))
        assertEquals("待系统重新绑定（设置已勾选）", PermissionStatusHelper.accessibilityStateLabel(enabledInSetting = true, enabledInManager = false))
        assertEquals("未开启", PermissionStatusHelper.accessibilityStateLabel(enabledInSetting = false, enabledInManager = false))
    }

    @Test
    fun inputMethodMatchesEnabledList() {
        val methods = listOf(
            "com.example.keyboard/com.example.keyboard.OtherIme",
            imeTarget,
        )

        assertTrue(PermissionStatusHelper.isInputMethodEnabled(methods, imeTarget))
    }

    @Test
    fun inputMethodDoesNotMatchOtherEntries() {
        val methods = listOf(
            "com.transparentlc.cloudclipboardsync/com.transparentlc.cloudclipboardsync.OtherIme",
            "com.transparentlc.cloudclipboardsync.debug/com.transparentlc.cloudclipboardsync.ClipboardInputMethodService",
        )

        assertFalse(PermissionStatusHelper.isInputMethodEnabled(methods, imeTarget))
    }

    @Test
    fun inputMethodDoesNotMatchBlankTarget() {
        assertFalse(PermissionStatusHelper.isInputMethodEnabled(listOf(imeTarget), " "))
    }

    @Test
    fun inputMethodStateLabelDescribesSelection() {
        assertEquals(
            "已启用并设为当前输入法",
            PermissionStatusHelper.inputMethodStateLabel(enabled = true, selected = true),
        )
        assertEquals(
            "已启用，尚未切换为当前输入法",
            PermissionStatusHelper.inputMethodStateLabel(enabled = true, selected = false),
        )
        assertEquals(
            "未启用",
            PermissionStatusHelper.inputMethodStateLabel(enabled = false, selected = false),
        )
    }

    @Test
    fun clipboardAppOpLabelDescribesMode() {
        assertEquals("允许", PermissionStatusHelper.clipboardAppOpLabel("allow"))
        assertEquals("仅前台允许", PermissionStatusHelper.clipboardAppOpLabel("foreground"))
        assertEquals("系统默认", PermissionStatusHelper.clipboardAppOpLabel("default"))
    }

    @Test
    fun clipboardReadRestrictionLabelHighlightsForegroundLimit() {
        assertEquals(
            "系统当前仍只允许前台读取剪贴板，后台复制不能视为已打通。",
            PermissionStatusHelper.clipboardReadRestrictionLabel("foreground"),
        )
        assertEquals(
            "系统当前没有额外把读剪贴板限制在前台。",
            PermissionStatusHelper.clipboardReadRestrictionLabel("allow"),
        )
    }
}

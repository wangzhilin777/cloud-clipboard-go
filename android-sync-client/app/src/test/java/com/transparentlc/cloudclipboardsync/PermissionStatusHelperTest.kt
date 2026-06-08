package com.transparentlc.cloudclipboardsync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStatusHelperTest {
    private val target = "com.transparentlc.cloudclipboardsync/com.transparentlc.cloudclipboardsync.ClipboardAccessAccessibilityService"

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
        assertEquals("已开启（系统设置）", PermissionStatusHelper.accessibilityStateLabel(enabledInSetting = true, enabledInManager = false))
        assertEquals("已开启（系统服务枚举）", PermissionStatusHelper.accessibilityStateLabel(enabledInSetting = false, enabledInManager = true))
        assertEquals("未开启", PermissionStatusHelper.accessibilityStateLabel(enabledInSetting = false, enabledInManager = false))
    }
}

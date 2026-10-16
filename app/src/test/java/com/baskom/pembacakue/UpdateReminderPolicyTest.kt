package com.baskom.pembacakue

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReminderPolicyTest {

    private val now = 1_800_000_000_000L
    private val snooze = UpdateReminderPolicy.SNOOZE_MS

    @Test
    fun `belum pernah menunda langsung diingatkan`() {
        assertTrue(UpdateReminderPolicy.shouldRemind(72, 0, 0L, now))
    }

    @Test
    fun `versi yang sama tidak diingatkan selama jeda`() {
        assertFalse(UpdateReminderPolicy.shouldRemind(72, 72, now - 1_000L, now))
        assertFalse(UpdateReminderPolicy.shouldRemind(72, 72, now - snooze + 1, now))
    }

    @Test
    fun `versi yang sama diingatkan lagi setelah jeda lewat`() {
        assertTrue(UpdateReminderPolicy.shouldRemind(72, 72, now - snooze, now))
    }

    @Test
    fun `versi lebih baru dari yang ditunda langsung diingatkan`() {
        assertTrue(UpdateReminderPolicy.shouldRemind(73, 72, now - 1_000L, now))
    }

    @Test
    fun `jam HP dimundurkan tidak membuat pengingat diam selamanya`() {
        assertTrue(UpdateReminderPolicy.shouldRemind(72, 72, now + 60_000L, now))
    }
}

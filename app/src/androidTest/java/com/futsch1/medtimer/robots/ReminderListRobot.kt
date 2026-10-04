package com.futsch1.medtimer.robots

import android.os.SystemClock
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import com.adevinta.android.barista.assertion.BaristaListAssertions.assertListItemCount
import com.adevinta.android.barista.interaction.BaristaListInteractions.clickListItemChild
import org.hamcrest.Matcher
import org.hamcrest.Matchers

/** The reminders listed on a medicine, addressed by their position in the list. */
class ReminderListRobot(
    private val composeUi: ComposeUi,
    private val settings: ReminderSettingsRobot
) {

    fun assertCount(expected: Int) = assertListItemCount(REMINDER_LIST, expected)

    /** Position-independent: reminder order follows the reminder times, which move with the clock. */
    fun assertContains(text: String) {
        onView(
            Matchers.allOf(
                ViewMatchers.withSubstring(text),
                ViewMatchers.isDescendantOfA(ViewMatchers.withId(REMINDER_LIST)),
            )
            // Effective visibility rather than isDisplayed: a card further down the list is bound but
            // off-screen, and this asserts the reminder exists rather than where it sits.
        ).check(ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
    }

    fun assertContainsTime(text: String) {
        onView(
            Matchers.allOf(
                ViewMatchers.withId(REMINDER_TIME),
                ViewMatchers.withSubstring(text),
                ViewMatchers.isDescendantOfA(ViewMatchers.withId(REMINDER_LIST)),
            )
        ).check(ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)))
    }

    /** Opens the advanced settings of the reminder at [position], runs [block] and comes back out. */
    fun inSettingsOf(position: Int, block: ReminderSettingsRobot.() -> Unit) {
        openSettings(position)
        settings.block()
        pressBack()
    }

    /** Opening and closing the settings is what redraws a row whose reminder type changed. */
    fun reopenSettings(position: Int) = inSettingsOf(position) {}

    /** Adds a reminder linked to the one at [position]; the settings screen closes itself afterwards. */
    fun addLinkedReminder(position: Int, amount: String? = null, hours: Int = 0, minutes: Int) {
        openSettings(position)
        settings.addLinkedReminder(amount, hours, minutes)
    }

    fun delete(position: Int) {
        openSettings(position)
        // Deleting closes the settings screen itself, so there is nothing left to navigate back from.
        settings.delete()
    }

    fun duplicate(position: Int) {
        openSettings(position)
        settings.duplicate()
    }

    /**
     * Focuses the amount of the reminder at [position], as tapping it does, and checks that it ends up
     * above the on-screen keyboard rather than underneath it.
     */
    fun assertAmountStaysAboveKeyboard(position: Int) {
        onView(ViewMatchers.withId(REMINDER_LIST)).perform(focusAmountAt(position))

        val deadline = SystemClock.uptimeMillis() + KEYBOARD_TIMEOUT_MS
        var placement = AmountPlacement(keyboardTop = null, amountBottom = 0)
        while (SystemClock.uptimeMillis() < deadline) {
            onView(ViewMatchers.withId(REMINDER_LIST)).perform(measureAmountAt(position) { placement = it })
            if (placement.isAboveKeyboard) return
            SystemClock.sleep(POLL_MS)
        }
        throw AssertionError(
            if (placement.keyboardTop == null) "The on-screen keyboard did not show"
            else "Amount field ends at y=${placement.amountBottom}, below the keyboard top at y=${placement.keyboardTop}"
        )
    }

    private data class AmountPlacement(val keyboardTop: Int?, val amountBottom: Int) {
        val isAboveKeyboard get() = keyboardTop != null && amountBottom <= keyboardTop
    }

    private fun focusAmountAt(position: Int) = onAmountAt(position, "focus the amount at $position") { amount ->
        amount.requestFocus()
        amount.context.getSystemService(InputMethodManager::class.java).showSoftInput(amount, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun measureAmountAt(position: Int, report: (AmountPlacement) -> Unit) =
        onAmountAt(position, "measure the amount at $position") { amount ->
            val insets = ViewCompat.getRootWindowInsets(amount)
            val keyboardHeight = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            val location = IntArray(2)
            amount.getLocationInWindow(location)
            report(
                AmountPlacement(
                    keyboardTop = (amount.rootView.height - keyboardHeight).takeIf { keyboardHeight > 0 },
                    amountBottom = location[1] + amount.height,
                )
            )
        }

    private fun onAmountAt(position: Int, description: String, block: (View) -> Unit) = object : ViewAction {
        override fun getConstraints(): Matcher<View> = ViewMatchers.isAssignableFrom(RecyclerView::class.java)
        override fun getDescription() = description
        override fun perform(uiController: UiController, view: View) {
            val itemView = (view as RecyclerView).findViewHolderForAdapterPosition(position)?.itemView
                ?: throw AssertionError("No reminder at position $position")
            block(itemView.findViewById(EDIT_AMOUNT))
            uiController.loopMainThreadUntilIdle()
        }
    }

    private fun openSettings(position: Int) {
        clickListItemChild(REMINDER_LIST, position, com.futsch1.medtimer.feature.ui.R.id.openAdvancedSettings)
        composeUi.settle()
    }

    private companion object {
        val REMINDER_LIST = com.futsch1.medtimer.feature.ui.R.id.reminderList
        val REMINDER_TIME = com.futsch1.medtimer.feature.ui.R.id.editReminderTime
        val EDIT_AMOUNT = com.futsch1.medtimer.feature.ui.R.id.editAmount
        const val KEYBOARD_TIMEOUT_MS = 30_000L
        const val POLL_MS = 500L
    }
}

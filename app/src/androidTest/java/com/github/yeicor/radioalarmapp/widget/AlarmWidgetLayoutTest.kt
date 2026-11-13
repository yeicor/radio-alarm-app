package com.github.yeicor.radioalarmapp.widget

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.yeicor.radioalarmapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmWidgetLayoutTest {
    @Test
    fun widgetInflatesWithIconTextAndThreeIconButtons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val remoteViews = AlarmWidgetProvider.buildViews(context)
        val parent = FrameLayout(context)
        val view = remoteViews.apply(context, parent)
        assertNotNull(view)

        val icon = view.findViewById<ImageView>(R.id.widget_icon)
        val text = view.findViewById<TextView>(R.id.widget_alarm_text)
        assertNotNull(icon)
        assertNotNull(text)
        assertTrue("Next alarm text should not be blank", text.text.isNotBlank())

        val buttons = view.findViewById<LinearLayout>(R.id.widget_buttons)
        val minus = view.findViewById<ImageView>(R.id.widget_minus)
        val middle = view.findViewById<ImageView>(R.id.widget_middle)
        val plus = view.findViewById<ImageView>(R.id.widget_plus)
        assertNotNull(buttons)
        assertNotNull(minus)
        assertNotNull(middle)
        assertNotNull(plus)
        assertEquals(context.getString(R.string.widget_action_earlier), minus.contentDescription)
        assertEquals(context.getString(R.string.widget_action_later), plus.contentDescription)
        assertTrue(
            middle.contentDescription == context.getString(R.string.widget_action_skip) ||
                middle.contentDescription == context.getString(R.string.widget_action_undo),
        )

        if (text.text == context.getString(R.string.widget_no_alarm)) {
            assertEquals(View.GONE, buttons.visibility)
        } else {
            assertEquals(View.VISIBLE, buttons.visibility)
        }
    }
}

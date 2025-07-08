@file:Suppress("NOTHING_TO_INLINE")

package com.tribalfs.milko.ui.util

import android.content.Context
import android.widget.Toast
import dev.oneuiproject.oneui.widget.SemToast

inline fun Context.semToast(msg: String, duration: Int = Toast.LENGTH_SHORT) {
    SemToast.makeText(this@semToast, msg, duration).  show()
}
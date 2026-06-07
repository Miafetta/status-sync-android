package com.miafetta.statussync

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

object AppToast {
    fun show(context: Context, @StringRes messageRes: Int, duration: Int = Toast.LENGTH_SHORT) {
        show(context, context.getString(messageRes), duration)
    }

    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context.applicationContext, message, duration).show()
    }
}

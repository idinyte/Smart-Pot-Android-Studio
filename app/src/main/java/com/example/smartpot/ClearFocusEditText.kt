package com.example.smartpot

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText

class ClearFocusEditText: TextInputEditText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if(keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP) {
            clearFocus()
        }

        return super.onKeyPreIme(keyCode, event)
    }
}
package com.core.adjust.ui.filter.child.filter

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager

class CustomScrollLinearLayoutManager(context: Context) : LinearLayoutManager(context, HORIZONTAL, false) {

    // Something is happening here
    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }
}
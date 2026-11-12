package com.black.core.util

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope

/**
 * Created by jinhyuk.lee on 2023/07/07
 **/
object FragmentExtension {
    val Fragment.viewLifecycleScope get() = viewLifecycleOwner.lifecycleScope
}

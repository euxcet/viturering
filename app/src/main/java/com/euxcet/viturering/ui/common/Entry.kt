package com.euxcet.viturering.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import com.euxcet.viturering.ui.theme.RingTheme

@ExperimentalComposeUiApi
@ExperimentalFoundationApi
@Composable
fun Entry() {
    RingTheme {
        AppScaffold()
    }
}
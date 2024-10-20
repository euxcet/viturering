package com.euxcet.viturering.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeUtils {
    companion object {

        fun formatCurrentTime(): String {
            return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        }
    }
}
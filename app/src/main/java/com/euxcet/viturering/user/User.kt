package com.euxcet.viturering.user

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.euxcet.viturering.user.bean.TaskBean
import com.euxcet.viturering.user.bean.UserBean
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class User @Inject constructor(
    @ApplicationContext val context: Context,
) {
    lateinit var bean: UserBean
    var currentTask: TaskBean? = null
    var currentTaskImage: Int? = null
    init {
        try {
            val gson = Gson()
            val config = context.assets.open("tasks.json")
            bean = gson.fromJson(config.reader(), UserBean::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateTask()
    }

    fun updateTask() {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val current = timeFormat.parse(timeFormat.format(Date()))!! // Keep time only

        bean.tasks.forEach { task ->
            val start = timeFormat.parse(task.startTime)
            val end = timeFormat.parse(task.endTime)
            if (current.after(start) && current.before(end)) {
                if (task != currentTask) {
                    val resourceId = context.resources.getIdentifier(
                        task.taskIMG,
                        "drawable",
                        context.packageName
                    )
                    currentTaskImage = resourceId
                }
                currentTask = task
            }
        }
    }
}
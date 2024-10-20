package com.euxcet.viturering.user

import android.content.Context
import android.util.Log
import com.euxcet.viturering.user.bean.UserBean
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class User @Inject constructor(
    @ApplicationContext val context: Context,
) {
    lateinit var bean: UserBean
    init {
        try {
            val gson = Gson()
            val config = context.assets.open("tasks.json")
            bean = gson.fromJson(config.reader(), UserBean::class.java)
            Log.e("Test", bean.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
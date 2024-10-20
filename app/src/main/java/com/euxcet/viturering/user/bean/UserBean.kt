package com.euxcet.viturering.user.bean

data class UserBean (
    val userID: String,
    val currentDay: Int,
    val date: String,
    val tasks: List<TaskBean>,
)

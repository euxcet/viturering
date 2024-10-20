package com.euxcet.viturering.user.bean

data class TaskTime (
    val time: String,
)

data class TaskBean (
    val taskName: String,
    val taskID: String,
    val interval: Int,
    val startTime: String,
    val endTime: String,
    val notifTime: List<TaskTime>,
    val suggestTime: Double,
    val cutoffTime: Double,
    val taskIMG: String
)

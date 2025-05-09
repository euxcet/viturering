package com.euxcet.viturering.pages.home

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import com.euxcet.viturering.R
import com.euxcet.viturering.pages.game.GameActivity
import com.euxcet.viturering.pages.mic.MICActivity
import com.euxcet.viturering.pages.model.Car3DActivity
import com.euxcet.viturering.pages.video.VideoActivity
import com.euxcet.viturering.pages.writing.HandWritingActivity

class HomeViewModel(application: Application): AndroidViewModel(application) {

    private val cardInfoList: MutableList<MutableList<CardInfo>> = mutableListOf()

    fun getCardInfoList(pageNo: Int): List<CardInfo> {
        if (pageNo >= cardInfoList.size) {
            return emptyList()
        }
        return cardInfoList[pageNo].toList()
    }

    fun initCardInfoList() {

        // game
        val gameCardInfo = CardInfo(
            key = "game",
            description = "Play the game",
            backgroundRes = R.drawable.ic_game,
            hCells = 2,
            vCells = 2,
            hPosition = 0,
            vPosition = 0
        )
        // video
        val videoCardInfo = CardInfo(
            key = "video",
            description = "Watch the video",
            backgroundRes = R.drawable.ic_movie,
            hCells = 4,
            vCells = 4,
            hPosition = 2,
            vPosition = 0
        )
        // 3d model
        val modelCardInfo = CardInfo(
            key = "models",
            description = "View the 3D model",
            backgroundRes = R.drawable.ic_models,
            hCells = 2,
            vCells = 2,
            hPosition = 0,
            vPosition = 2
        )
        cardInfoList.add(mutableListOf(gameCardInfo, videoCardInfo, modelCardInfo))
        // fake pages
        val fake1CardInfo = CardInfo(
            key = "fake1",
            description = "地图",
            backgroundRes = R.drawable.ic_map,
            hCells = 2,
            vCells = 2,
            hPosition = 0,
            vPosition = 0
        )

        val fake2CardInfo = CardInfo(
            key = "fake2",
            description = "会议",
            backgroundRes = R.drawable.ic_meeting,
            hCells = 4,
            vCells = 4,
            hPosition = 2,
            vPosition = 0
        )

        val fake3CardInfo = CardInfo(
            key = "fake3",
            description = "音乐",
            backgroundRes = R.drawable.ic_music,
            hCells = 2,
            vCells = 2,
            hPosition = 0,
            vPosition = 2
        )

        cardInfoList.add(mutableListOf(fake1CardInfo, fake2CardInfo, fake3CardInfo))
    }

    fun openCard(context: Context, key: String) {
        // open card
        when (key) {
            "game" -> {
//                val intent = Intent(context, GameActivity::class.java)
//                context.startActivity(intent)
                val intent = Intent(context, com.ellison.flappybird.MainActivity::class.java)
                context.startActivity(intent)
            }
            "models" -> {
                val intent = Intent(context, Car3DActivity::class.java)
                context.startActivity(intent)
            }
            "video" -> {
                val intent = Intent(context, VideoActivity::class.java)
                context.startActivity(intent)
            }
            "fake1" -> {
                val intent = Intent(context, HandWritingActivity::class.java)
                context.startActivity(intent)
            }
            "fake3" -> {
                val intent = Intent(context, MICActivity::class.java)
                context.startActivity(intent)
            }
            else -> {
            }
        }
    }
}
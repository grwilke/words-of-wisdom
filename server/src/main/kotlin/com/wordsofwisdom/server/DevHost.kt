package com.wordsofwisdom.server

import com.wordsofwisdom.core.LessonBank
import com.wordsofwisdom.core.WisdomGameEngine

fun main() {
    val engine = WisdomGameEngine(LessonBank.loadDefault())
    val server = PartyLanServer(engine)
    val port = server.start(PartyLanServer.DEFAULT_PORT)
    println("Words of Wisdom DevHost listening on http://127.0.0.1:$port (room ${engine.roomCode})")
    Thread.currentThread().join()
}

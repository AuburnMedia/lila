package lila.official

import lila.common.LateMultiThrottler
import lila.core.socket.{ protocol as P, * }
import lila.room.RoomSocket.{ Protocol as RP, * }

final private class OfficialSocket(
    socketKit: SocketKit,
    chat: lila.core.chat.ChatApi
)(using Executor, akka.actor.ActorSystem, Scheduler):

  private val reloadThrottler = LateMultiThrottler(executionTimeout = none, logger = logger)

  def reload(id: OfficialTournamentId): Unit =
    reloadThrottler ! LateMultiThrottler.work(
      id = id,
      run = fuccess:
        send.exec(RP.Out.tellRoom(id.value.into(RoomId), makeMessage("reload")))
      ,
      delay = 1.seconds.some
    )

  lazy val rooms = makeRoomMap(send)

  subscribeChat(rooms, _.official)

  private lazy val handler: SocketHandler =
    roomHandler(
      rooms,
      chat,
      logger,
      roomId => _.Official(OfficialTournamentId(roomId.value)).some,
      localTimeout = None, // No team-based moderation for now
      chatBusChan = _.official
    )

  private lazy val send = socketKit.send("official-out")

  socketKit
    .subscribe("official-in", RP.In.reader)(handler.orElse(socketKit.baseHandler))
    .andDo(send.exec(P.Out.boot))

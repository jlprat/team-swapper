package io.github.jlprat.teamswapper.behaviors

import scala.concurrent.duration._

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.RequestChange
import io.github.jlprat.teamswapper.domain.GeneralProtocol.Error
import io.github.jlprat.teamswapper.domain.GeneralProtocol.OK
import io.github.jlprat.teamswapper.domain.GeneralProtocol.Response
import io.github.jlprat.teamswapper.domain.Move
import io.github.jlprat.teamswapper.domain.Team
import io.github.jlprat.teamswapper.domain.TeamMember
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.FindSwaps

object TeamHandlerBehavior {

  sealed trait Command
  final case class CreateTeam(name: String, size: Int, replyTo: ActorRef[Response]) extends Command
  final case class AddMember(team: Team, teamMember: TeamMember, replyTo: ActorRef[Response])
      extends Command
  final case class RemoveMember(team: Team, teamMember: TeamMember, replyTo: ActorRef[Response])
      extends Command
  final case class SwapRequest(
      teamMember: TeamMember,
      from: Team,
      to: Team,
      replyTo: ActorRef[Response]
  )                                                                  extends Command
  final case class CalculateSwaps(replyTo: ActorRef[Set[Seq[Move]]]) extends Command
  final case class Swap(moves: Seq[Move])                            extends Command
  final case object Timeout                                          extends Command

  def apply(teams: Map[String, ActorRef[TeamBehavior.Command]] = Map.empty): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      val swapWrapper: ActorRef[Seq[Move]] = ctx.messageAdapter(moves => Swap(moves))
      Behaviors.receive { (ctx, msg) =>
        msg match {
          case CreateTeam(name, size, replyTo) =>
            val team = ctx.spawn(TeamBehavior(size), name)
            replyTo.tell(OK)
            apply(teams + (name -> team))
          case AddMember(team, teamMember, replyTo) if teams.keySet.contains(team.name) =>
            val teamRef = teams(team.name)
            teamRef.tell(TeamBehavior.AddTeamMember(teamMember, replyTo))
            Behaviors.same
          case AddMember(team, _, replyTo) =>
            replyTo.tell(Error(s"Team ${team.name} doesn't exist"))
            Behaviors.same
          case RemoveMember(team, teamMember, replyTo) if teams.keySet.contains(team.name) =>
            val teamRef = teams(team.name)
            teamRef.tell(TeamBehavior.RemoveTeamMember(teamMember, replyTo))
            Behaviors.same
          case RemoveMember(team, _, replyTo) =>
            replyTo.tell(Error(s"Team ${team.name} doesn't exist"))
            Behaviors.same
          case SwapRequest(teamMember, from, to, replyTo)
              if teams.keySet.contains(from.name) && teams.keySet.contains(to.name) =>
            val fromRef = teams(from.name)
            val toRef   = teams(to.name)
            fromRef.tell(RequestChange(teamMember, toRef, replyTo))
            Behaviors.same
          case SwapRequest(_, from, to, replyTo) =>
            replyTo.tell(Error(s"Either ${from.name} or ${to.name} do not exist"))
            Behaviors.same
          case CalculateSwaps(replyTo) =>
            teams.values.foreach { team =>
              team.tell(FindSwaps(swapWrapper))
            }
            waitingForSwaps(teams, replyTo, Set.empty)
          case Swap(_) =>
            ctx.log.error("Received Swap messages on the wrong state, it was too late")
            Behaviors.same
          case Timeout => Behaviors.same
        }
      }
    }

  def waitingForSwaps(
      teams: Map[String, ActorRef[TeamBehavior.Command]] = Map.empty,
      replyTo: ActorRef[Set[Seq[Move]]],
      allMoves: Set[Seq[Move]] = Set.empty
  ): Behavior[Command] =
    Behaviors.withStash[Command](100) { buffer =>
      Behaviors.withTimers[Command] { timers =>
        timers.startSingleTimer(Timeout, 1.second)
        Behaviors.receiveMessage {
          case Timeout =>
            replyTo.tell(allMoves)
            buffer.unstashAll(apply(teams))
          case Swap(moves) =>
            waitingForSwaps(teams, replyTo, allMoves + moves)
          case msg =>
            buffer.stash(msg)
            Behaviors.same
        }
      }
    }
}

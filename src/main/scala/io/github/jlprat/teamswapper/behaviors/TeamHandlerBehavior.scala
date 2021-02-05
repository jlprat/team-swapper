package io.github.jlprat.teamswapper.behaviors

import scala.concurrent.duration._

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.FindSwaps
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.RequestChange
import io.github.jlprat.teamswapper.domain.GeneralProtocol.Error
import io.github.jlprat.teamswapper.domain.GeneralProtocol.OK
import io.github.jlprat.teamswapper.domain.GeneralProtocol.Response
import io.github.jlprat.teamswapper.domain.Move
import io.github.jlprat.teamswapper.domain.Team
import io.github.jlprat.teamswapper.domain.TeamMember

object TeamHandlerBehavior {

  sealed trait Command
  final case class CreateTeam(name: String, size: Int, replyTo: ActorRef[Response]) extends Command
  final case class AddMember(team: Team, teamMember: TeamMember, replyTo: ActorRef[Response])
      extends Command
  final case class RemoveMember(team: Team, teamMember: TeamMember, replyTo: ActorRef[Response])
      extends Command
  final case class SwapRequest(
      teamMember: TeamMember,
      to: Team,
      replyTo: ActorRef[Response]
  )                                                                  extends Command
  final case class CalculateSwaps(replyTo: ActorRef[Set[Seq[Move]]]) extends Command
  final case class Swap(moves: Seq[Move])                            extends Command
  final case object Timeout                                          extends Command

  final case class TeamInfo(ref: ActorRef[TeamBehavior.Command], freeSlots: Int)

  def apply(
      teams: Map[Team, TeamInfo],
      members: Map[TeamMember, Team]
  ): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      val swapWrapper: ActorRef[Seq[Move]] = ctx.messageAdapter(moves => Swap(moves))
      Behaviors.receive { (ctx, msg) =>
        msg match {
          case CreateTeam(name, size, replyTo) if !teams.contains(Team(name)) =>
            val team = ctx.spawn(TeamBehavior(), name)
            replyTo.tell(OK)
            apply(teams + (Team(name) -> TeamInfo(team, size)), members)
          case CreateTeam(name, _, replyTo) =>
            replyTo.tell(Error(s"Team $name already exists"))
            Behaviors.same

          case AddMember(team, teamMember, replyTo)
              if members.contains(teamMember) && members(teamMember) == team =>
            replyTo.tell(OK)
            Behaviors.same
          case AddMember(team, teamMember, replyTo)
              if teams.keySet.contains(team) && teams(team).freeSlots == 0 =>
            replyTo.tell(Error(s"Team ${team.name} is already full, can't add ${teamMember.name}"))
            Behaviors.same
          case AddMember(team, teamMember, replyTo) if teams.keySet.contains(team) =>
            val teamInfo = teams(team)
            replyTo.tell(OK)
            apply(
              teams + (team         -> teamInfo.copy(freeSlots = teamInfo.freeSlots - 1)),
              members + (teamMember -> team)
            )
          case AddMember(team, _, replyTo) =>
            replyTo.tell(Error(s"Team ${team.name} doesn't exist"))
            Behaviors.same

          case RemoveMember(team, teamMember, replyTo)
              if teams.keySet.contains(team) && members
                .contains(teamMember) && members(teamMember) == team =>
            val teamInfo = teams(team)
            replyTo.tell(OK)
            apply(
              teams + (team -> teamInfo.copy(freeSlots = teamInfo.freeSlots + 1)),
              members - teamMember
            )
          case RemoveMember(team, teamMember, replyTo)
              if teams.keySet.contains(team) && (!members
                .contains(teamMember) || members(teamMember) != team) =>
            replyTo.tell(
              Error(s"Can't remove ${teamMember.name} from team, because is not part of the team")
            )
            Behaviors.same
          case RemoveMember(team, _, replyTo) =>
            replyTo.tell(Error(s"Team ${team.name} doesn't exist"))
            Behaviors.same

          case SwapRequest(teamMember, to, replyTo)
              if members.contains(teamMember) && teams.keySet.contains(members(teamMember)) =>
            val fromTeamInfo = teams(members(teamMember))
            val toTeamInfo   = teams(to)
            fromTeamInfo.ref.tell(RequestChange(teamMember, toTeamInfo.ref, replyTo))
            Behaviors.same
          case SwapRequest(teamMember, to, replyTo) =>
            replyTo.tell(
              Error(
                s"Either team ${to.name} do not exist, or ${teamMember.name + " " + teamMember.surname} doesn't belong to any team"
              )
            )
            Behaviors.same
          case CalculateSwaps(replyTo) =>
            teams.values.foreach { team =>
              team.ref.tell(FindSwaps(swapWrapper))
            }
            waitingForSwaps(teams, members, replyTo, Set.empty)
          case Swap(_) =>
            ctx.log.error("Received Swap messages on the wrong state, it was too late")
            Behaviors.same
          case Timeout => Behaviors.same
        }
      }
    }

  def waitingForSwaps(
      teams: Map[Team, TeamInfo],
      members: Map[TeamMember, Team],
      replyTo: ActorRef[Set[Seq[Move]]],
      allMoves: Set[Seq[Move]] = Set.empty
  ): Behavior[Command] =
    Behaviors.withStash[Command](100) { buffer =>
      Behaviors.withTimers[Command] { timers =>
        timers.startSingleTimer(Timeout, 1.second)
        Behaviors.receiveMessage {
          case Timeout =>
            replyTo.tell(allMoves)
            buffer.unstashAll(apply(teams, members))
          case Swap(moves) =>
            waitingForSwaps(teams, members, replyTo, allMoves + moves)
          case msg =>
            buffer.stash(msg)
            Behaviors.same
        }
      }
    }
}

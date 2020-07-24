package io.github.jlprat.teamswapper.behaviors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import io.github.jlprat.teamswapper.behaviors.TeamRepo.TeamRepoActions
import io.github.jlprat.teamswapper.behaviors.TeamRepo.TeamRepoResponses
import io.github.jlprat.teamswapper.Team

import scala.concurrent.duration._
import akka.util.Timeout
import scala.util.Success

object TeamMembershipRepo {

  sealed trait TeamMembershipActions
  case class JoinTeam(team: Team, member: String, replyTo: ActorRef[TeamMembershipResponses])
      extends TeamMembershipActions
  case class LeaveTeam(team: Team, member: String, replyTo: ActorRef[TeamMembershipResponses])
      extends TeamMembershipActions

  case class IsMember(
      teamId: String,
      member: String,
      teamRepo: ActorRef[TeamRepoActions],
      replyTo: ActorRef[TeamMembershipResponses]
  ) extends TeamMembershipActions
  private[behaviors] case class InternalIsMember(
      team: Team,
      member: String,
      replyTo: ActorRef[TeamMembershipResponses]
  ) extends TeamMembershipActions

  case object Ignore extends TeamMembershipActions

  sealed trait TeamMembershipResponses
  case object Joined             extends TeamMembershipResponses
  case object TeamFull           extends TeamMembershipResponses
  case object Left               extends TeamMembershipResponses
  case class Member(is: Boolean) extends TeamMembershipResponses

  def apply(membership: Map[String, Set[String]] = Map.empty): Behavior[TeamMembershipActions] =
    Behaviors.receive {
      case (_, JoinTeam(team, member, replyTo)) =>
        val members = membership.withDefaultValue(Set.empty)(team.name)
        if (members.contains(member)) {
          replyTo ! Joined
          Behaviors.same
        } else if (members.size >= team.capacity) {
          replyTo ! TeamFull
          Behaviors.same
        } else {
          replyTo ! Joined
          apply(membership.updated(team.name, members + (member)))
        }
      case (_, LeaveTeam(team, member, replyTo)) if membership(team.name).contains(member) =>
        val members = membership(team.name).excl(member)
        replyTo ! Left
        apply(membership.updated(team.name, members))
      case (_, LeaveTeam(_, _, replyTo)) =>
        replyTo ! Member(false)
        Behaviors.same
      case (context, IsMember(teamId, member, teamRepo, replyTo)) =>
        implicit val timeout: Timeout = 1.seconds
        context.ask[TeamRepoActions, TeamRepoResponses](
          teamRepo,
          ref => TeamRepo.GetTeam(teamId, ref)
        ) {
          case Success(TeamRepo.Present(team)) => InternalIsMember(team, member, replyTo)
          case _ =>
            replyTo ! Member(false)
            Ignore
        }
        Behaviors.same
      case (_, InternalIsMember(team, member, replyTo))
          if membership.withDefaultValue(Set.empty)(team.name).contains(member) =>
        replyTo ! Member(true)
        Behaviors.same
      case (_, InternalIsMember(_, _, replyTo)) =>
        replyTo ! Member(false)
        Behaviors.same
      case (_, Ignore) => Behaviors.same
    }
}

package io.github.jlprat.teamswapper

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object TeamMembershipRepo {

  case class Member(id: String, name: String)

  sealed trait TeamMembershipActions
  case class JoinTeam(team: Team, member: Member, replyTo: ActorRef[TeamMembershipResponses]) extends TeamMembershipActions
  case class LeaveTeam(team: Team, member: Member, replyTo: ActorRef[TeamMembershipResponses]) extends TeamMembershipActions
  
  case class IsMember(team: Team, member: Member, replyTo: ActorRef[TeamMembershipResponses]) extends TeamMembershipActions

  sealed trait TeamMembershipResponses
  case object Joined extends TeamMembershipResponses
  case object TeamFull extends TeamMembershipResponses
  case object Left extends TeamMembershipResponses
  case object Member extends TeamMembershipResponses
  case object NotMember extends TeamMembershipResponses

  def apply(membership: Map[String, Set[Member]] = Map.empty): Behavior[TeamMembershipActions] = Behaviors.receiveMessage {
    case JoinTeam(team, member, replyTo) => 
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
    case LeaveTeam(team, member, replyTo) if membership(team.name).contains(member) =>
      val members = membership(team.name).excl(member)
      replyTo ! Left
      apply(membership.updated(team.name, members))
    case LeaveTeam(_, _, replyTo) =>
      replyTo ! NotMember
      Behaviors.same
    case IsMember(team, member, replyTo) if membership(team.name).contains(member) =>
      replyTo ! Member
      Behaviors.same
    case IsMember(_, _, replyTo) =>
      replyTo ! NotMember
      Behaviors.same
  }
}

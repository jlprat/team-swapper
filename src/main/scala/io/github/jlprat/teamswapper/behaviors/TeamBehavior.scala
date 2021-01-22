package io.github.jlprat.teamswapper.behaviors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import io.github.jlprat.teamswapper.domain.Move
import io.github.jlprat.teamswapper.domain.Team
import io.github.jlprat.teamswapper.domain.TeamMember
import io.github.jlprat.teamswapper.domain.GeneralProtocol._

//TODO think about splitting this in 2 actors, one for the team members and the other for swaps
object TeamBehavior {

  sealed trait Command
  final case class AddTeamMember(teamMember: TeamMember, replyTo: ActorRef[Response])
      extends Command
  final case class RemoveTeamMember(teamMember: TeamMember, replyTo: ActorRef[Response])
      extends Command
  final case class RequestChange(
      teamMember: TeamMember,
      to: ActorRef[Command],
      replyTo: ActorRef[Response]
  )                                                        extends Command
  final case class FindSwaps(replyTo: ActorRef[Seq[Move]]) extends Command
  private final case class Probe(
      originalTeam: ActorRef[Command],
      breadcrumbs: Seq[Move],
      replyTo: ActorRef[Seq[Move]]
  ) extends Command

  final case class Request(teamMember: TeamMember, to: ActorRef[Command])

  def apply(
      size: Int,
      teamMembers: Set[TeamMember] = Set.empty,
      openRequests: Set[Request] = Set.empty
  ): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case AddTeamMember(teamMember, replyTo) if size > teamMembers.size =>
          replyTo.tell(OK)
          apply(size, teamMembers + teamMember, openRequests)
        case AddTeamMember(teamMember, replyTo) =>
          ctx.log.error("Team is full!")
          replyTo.tell(Error(s"Team is already full, can't add ${teamMember.name}"))
          Behaviors.same
        case RemoveTeamMember(teamMember, replyTo) if teamMembers.contains(teamMember) =>
          replyTo.tell(OK)
          apply(size, teamMembers - teamMember, openRequests.filter(_.teamMember != teamMember))
        case RemoveTeamMember(teamMember, replyTo) =>
          replyTo.tell(
            Error(s"Can't remove ${teamMember.name} from team, because is not part of the team")
          )
          Behaviors.same
        case RequestChange(teamMember, to, replyTo) if teamMembers.contains(teamMember) =>
          replyTo.tell(OK)
          apply(size, teamMembers, openRequests + Request(teamMember, to))
        case RequestChange(teamMember, _, replyTo) =>
          replyTo.tell(
            Error(s"Can't register request change as ${teamMember.name} is not part of the team")
          )
          Behaviors.same
        case FindSwaps(replyTo) =>
          //The idea is to send probes to our contacts,
          //and if the Probe comes back to us, we found a potential swap
          openRequests.foreach { request =>
            request.to.tell(
              Probe(
                ctx.self,
                Seq(Move(request.teamMember, Team(ctx.self.path.name), Team(request.to.path.name))),
                replyTo
              )
            )
          }
          Behaviors.same
        case Probe(originalTeam, breadcrumbs, replyTo) if originalTeam == ctx.self =>
          //Swap found, we cycled to ourselves
          replyTo.tell(breadcrumbs)
          Behaviors.same
        case Probe(_, breadcrumbs, _) if breadcrumbs.exists(_.from.name == ctx.self.path.name) =>
          //We found an inner loop, this path will only loop infinitely
          ctx.log.info("Found an inner loop, breaking!")
          Behaviors.same
        case Probe(originalTeam, breadcrumbs, replyTo) =>
          //On the look for a loop
          openRequests.foreach { request =>
            val swap =
              Move(request.teamMember, Team(ctx.self.path.name), Team(request.to.path.name))
            request.to.tell(Probe(originalTeam, breadcrumbs :+ swap, replyTo))
          }
          Behaviors.same
      }
    }
}

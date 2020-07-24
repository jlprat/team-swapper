package io.github.jlprat.teamswapper.behaviors

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import io.github.jlprat.teamswapper.behaviors.TeamMembershipRepo.TeamMembershipActions
import io.github.jlprat.teamswapper.behaviors.TeamMembershipRepo.Member
import io.github.jlprat.teamswapper.behaviors.TeamRepo.TeamRepoActions
import io.github.jlprat.teamswapper.behaviors.TeamMembershipRepo.IsMember
import io.github.jlprat.teamswapper.behaviors.TeamRepo.ListTeams
import io.github.jlprat.teamswapper.behaviors.TeamRepo.Teams
import io.github.jlprat.teamswapper.Team

import scala.concurrent.duration._

object SwapRepo {

  case class SwapKey private (teamA: String, teamB: String) {
    def reverse: SwapKey = SwapKey(teamB, teamA)
  }

  case class Swap(key: SwapKey, memberIdPair: (String, String))

  sealed trait SwapRepoActions
  case class InternalSwapRequest(
      memberId: String,
      from: Team,
      to: Team,
      replyTo: ActorRef[SwapRepoResponses]
  ) extends SwapRepoActions
  case class SwapRequest(
      memberId: String,
      from: String,
      to: String,
      teamMembershipRepo: ActorRef[TeamMembershipActions],
      teamRepo: ActorRef[TeamRepoActions],
      replyTo: ActorRef[SwapRepoResponses]
  )                                                                          extends SwapRepoActions
  case class GetSwaps(replyTo: ActorRef[SwapRepoResponses])                  extends SwapRepoActions
  case class SwapDone(swap: Swap, replyTo: ActorRef[SwapRepoResponses])      extends SwapRepoActions
  private case class Fail(msg: String, replyTo: ActorRef[SwapRepoResponses]) extends SwapRepoActions

  sealed trait SwapRepoResponses
  case class Registered(swap: SwapKey) extends SwapRepoResponses
  case class Swaps(swaps: Set[Swap])   extends SwapRepoResponses
  case class Deleted(swap: Swap)       extends SwapRepoResponses
  case class Error(msg: String)        extends SwapRepoResponses

  // Any since no common type between TeamRepo and TeamMembershipRepo
  type Reply = Any

  def apply(
      swapAttempts: Map[SwapKey, Set[String]] = Map.empty,
      swaps: Set[Swap] = Set.empty
  ): Behavior[SwapRepoActions] =
    Behaviors.receive {

      case (context, SwapRequest(memberId, from, to, teamMembershipRepo, teamRepo, replyTo)) =>
        context.spawnAnonymous(
          Aggregator[Reply, SwapRepoActions](
            sendRequests = { sendTo =>
              teamMembershipRepo ! IsMember(from, memberId, teamRepo, sendTo)
              teamRepo ! ListTeams(sendTo)
            },
            expectedReplies = 2,
            context.self,
            aggregateReplies = replies => {
              //Aggregate responses to InternalSwapRequest

              val teams: Set[Team] = replies.collect {
                case Teams(set) => set
              }.head
              val isMember: Boolean = replies.collect {
                case Member(b) => b
              }.head
              if (isMember) {
                val maybeSwapReq = for {
                  fromTeam <- teams.find(_.name == from)
                  toTeam   <- teams.find(_.name == to)
                } yield InternalSwapRequest(memberId, fromTeam, toTeam, replyTo)

                maybeSwapReq.getOrElse(Fail(s"Team $from or $to is unknown", replyTo))
              } else {
                Fail(s"Member $memberId is not a member of team $from", replyTo)
              }
            },
            timeout = 2.seconds
          )
        )
        Behaviors.same

      case (_, InternalSwapRequest(memberId, from, to, replyTo)) =>
        val swapKey = SwapKey(from.name, to.name)
        if (!swapAttempts.contains(swapKey.reverse)) {
          // there is nobody from the destination team that wants to go to origin
          replyTo ! Registered(swapKey)
          val requestors = swapAttempts.withDefaultValue(Set.empty)(swapKey)
          apply(swapAttempts = swapAttempts + (swapKey -> requestors.incl(memberId)))
        } else {
          //We have a swap!
          val reverseKey = swapKey.reverse
          val requestors = swapAttempts(reverseKey)
          val swap       = Swap(reverseKey, (requestors.head, memberId))
          replyTo ! Registered(swapKey)
          val newRequestors = requestors.tail
          if (newRequestors.size == 0) {
            apply(swapAttempts.removed(reverseKey), swaps.incl(swap))
          } else {
            apply(swapAttempts.updated(reverseKey, newRequestors), swaps.incl(swap))
          }
        }

      case (_, GetSwaps(replyTo)) =>
        replyTo ! Swaps(swaps)
        Behaviors.same

      case (_, SwapDone(swap, replyTo)) =>
        replyTo ! Deleted(swap)
        apply(swaps = swaps.excl(swap))

      case (_, Fail(msg, replyTo)) =>
        replyTo ! Error(msg)
        Behaviors.same
    }
}

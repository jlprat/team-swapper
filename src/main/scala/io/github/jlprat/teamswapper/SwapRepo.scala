package io.github.jlprat.teamswapper

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior

object SwapRepo {

  case class SwapKey private (teamA: String, teamB: String) {
    def reverse: SwapKey = SwapKey(teamB, teamA)
  }

  case class Swap(key: SwapKey, memberIdPair: (String, String))

  sealed trait SwapRepoActions
  case class SwapRequest(
      memberId: String,
      from: Team,
      to: Team,
      replyTo: ActorRef[SwapRepoResponses]
  )                                                                     extends SwapRepoActions
  case class GetSwaps(replyTo: ActorRef[SwapRepoResponses])             extends SwapRepoActions
  case class SwapDone(swap: Swap, replyTo: ActorRef[SwapRepoResponses]) extends SwapRepoActions

  sealed trait SwapRepoResponses
  case class Registered(swap: SwapKey) extends SwapRepoResponses
  case class Swaps(swaps: Set[Swap])   extends SwapRepoResponses
  case class Deleted(swap: Swap)       extends SwapRepoResponses

  def apply(
      swapAttempts: Map[SwapKey, Set[String]] = Map.empty,
      swaps: Set[Swap] = Set.empty
  ): Behavior[SwapRepoActions] =
    Behaviors.receiveMessage {
      case SwapRequest(memberId, from, to, replyTo) =>
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

      case GetSwaps(replyTo) =>
        replyTo ! Swaps(swaps)
        Behaviors.same

      case SwapDone(swap, replyTo) =>
        replyTo ! Deleted(swap)
        apply(swaps = swaps.excl(swap))
    }
}

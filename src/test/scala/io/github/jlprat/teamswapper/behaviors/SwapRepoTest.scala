package io.github.jlprat.teamswapper.behaviors

import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import io.github.jlprat.teamswapper.behaviors.SwapRepo._
import io.github.jlprat.teamswapper.Team

class SwapRepoTest extends AnyFlatSpec with Matchers {
  "SwapRepo" should "register a new swap" in {
    val from             = Team("A", 3)
    val to               = Team("B", 4)
    val member           = "132ka"
    val swapRepoBehavior = BehaviorTestKit(SwapRepo())
    val inbox            = TestInbox[SwapRepoResponses]()

    swapRepoBehavior.run(InternalSwapRequest(member, from, to, inbox.ref))

    inbox.expectMessage(Registered(SwapKey(from.name, to.name)))
  }

  it should "be fine with double registering" in {
    val from             = Team("A", 3)
    val to               = Team("B", 4)
    val member           = "132ka"
    val swapkey          = SwapKey(from.name, to.name)
    val swapRepoBehavior = BehaviorTestKit(SwapRepo(swapAttempts = Map(swapkey -> Set(member))))
    val inbox            = TestInbox[SwapRepoResponses]()

    swapRepoBehavior.run(InternalSwapRequest(member, from, to, inbox.ref))

    inbox.expectMessage(Registered(swapkey))
  }

  it should "register swap request from 2 members of same team" in {
    val from             = Team("A", 3)
    val to               = Team("B", 4)
    val member1          = "132ka"
    val member2          = "131ka"
    val swapkey          = SwapKey(from.name, to.name)
    val swapRepoBehavior = BehaviorTestKit(SwapRepo(swapAttempts = Map(swapkey -> Set(member1))))
    val inbox            = TestInbox[SwapRepoResponses]()

    swapRepoBehavior.run(InternalSwapRequest(member2, from, to, inbox.ref))

    inbox.expectMessage(Registered(swapkey))

    swapRepoBehavior.run(GetSwaps(inbox.ref))
    inbox.expectMessage(Swaps(Set.empty))
  }

  it should "register swap request matching another one" in {
    val from             = Team("A", 3)
    val to               = Team("B", 4)
    val member1          = "132ka"
    val member2          = "131ka"
    val swapkey          = SwapKey(from.name, to.name)
    val swapRepoBehavior = BehaviorTestKit(SwapRepo(swapAttempts = Map(swapkey -> Set(member1))))
    val inbox            = TestInbox[SwapRepoResponses]()

    swapRepoBehavior.run(InternalSwapRequest(member2, to, from, inbox.ref))

    inbox.expectMessage(Registered(SwapKey(to.name, from.name)))

    swapRepoBehavior.run(GetSwaps(inbox.ref))
    inbox.expectMessage(Swaps(Set(Swap(swapkey, (member1, member2)))))
  }

  it should "delete a swap once it's done" in {
    val from             = Team("A", 3)
    val to               = Team("B", 4)
    val member1          = "132ka"
    val member2          = "131ka"
    val swapkey          = SwapKey(from.name, to.name)
    val swap             = Swap(swapkey, (member1, member2))
    val swapRepoBehavior = BehaviorTestKit(SwapRepo(swaps = Set(swap)))
    val inbox            = TestInbox[SwapRepoResponses]()

    swapRepoBehavior.run(SwapDone(swap, inbox.ref))
    inbox.expectMessage(Deleted(swap))

    swapRepoBehavior.run(GetSwaps(inbox.ref))
    inbox.expectMessage(Swaps(Set.empty))
  }  
}

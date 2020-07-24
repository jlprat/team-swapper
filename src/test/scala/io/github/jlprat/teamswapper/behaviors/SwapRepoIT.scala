package io.github.jlprat.teamswapper.behaviors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.jlprat.teamswapper.behaviors.TeamRepo
import io.github.jlprat.teamswapper.behaviors.SwapRepo.SwapRequest
import io.github.jlprat.teamswapper.behaviors.SwapRepo.SwapRepoResponses
import io.github.jlprat.teamswapper.behaviors.SwapRepo.Registered
import io.github.jlprat.teamswapper.behaviors.SwapRepo.SwapKey
import io.github.jlprat.teamswapper.behaviors.SwapRepo.Swaps
import io.github.jlprat.teamswapper.behaviors.SwapRepo.GetSwaps
import io.github.jlprat.teamswapper.behaviors.SwapRepo.Swap
import io.github.jlprat.teamswapper.behaviors.SwapRepo.Error
import io.github.jlprat.teamswapper.Team

class SwapRepoIT extends ScalaTestWithActorTestKit with AnyFlatSpecLike with Matchers {
  "SwapRepo" should "register a new swap" in {
    val from   = Team("A", 3)
    val to     = Team("B", 4)
    val member = "132ka"

    val teamRepo           = testKit.spawn(TeamRepo(Map(from.name -> from, to.name -> to)))
    val teamMembershipRepo = testKit.spawn(TeamMembershipRepo(Map(from.name -> Set(member))))
    val swapRepo           = testKit.spawn(SwapRepo())

    val probe = testKit.createTestProbe[SwapRepoResponses]()

    val request = SwapRequest(member, from.name, to.name, teamMembershipRepo, teamRepo, probe.ref)

    swapRepo ! request

    probe.expectMessage(Registered(SwapKey(from.name, to.name)))
  }

  it should "register a swap request from 2 members of same team" in {
    val from    = Team("A", 3)
    val to      = Team("B", 4)
    val member1 = "132ka"
    val member2 = "133ka"

    val teamRepo = testKit.spawn(TeamRepo(Map(from.name -> from, to.name -> to)))
    val teamMembershipRepo =
      testKit.spawn(TeamMembershipRepo(Map(from.name -> Set(member1, member2))))
    val swapRepo = testKit.spawn(SwapRepo())

    val probe = testKit.createTestProbe[SwapRepoResponses]()

    val request1 = SwapRequest(member1, from.name, to.name, teamMembershipRepo, teamRepo, probe.ref)
    val request2 = SwapRequest(member2, from.name, to.name, teamMembershipRepo, teamRepo, probe.ref)
    swapRepo ! request1
    probe.expectMessage(Registered(SwapKey(from.name, to.name)))
    swapRepo ! request2
    probe.expectMessage(Registered(SwapKey(from.name, to.name)))

    swapRepo ! GetSwaps(probe.ref)
    probe.expectMessage(Swaps(Set.empty))
  }

  it should "register a swap request matching an existing one" in {
    val from    = Team("A", 3)
    val to      = Team("B", 4)
    val member1 = "132ka"
    val member2 = "133ka"
    val swapkey = SwapKey(from.name, to.name)

    val teamRepo = testKit.spawn(TeamRepo(Map(from.name -> from, to.name -> to)))
    val teamMembershipRepo =
      testKit.spawn(TeamMembershipRepo(Map(from.name -> Set(member1), to.name -> Set(member2))))
    val swapRepo = testKit.spawn(SwapRepo())

    val probe = testKit.createTestProbe[SwapRepoResponses]()

    val request1 = SwapRequest(member1, from.name, to.name, teamMembershipRepo, teamRepo, probe.ref)
    val request2 = SwapRequest(member2, to.name, from.name, teamMembershipRepo, teamRepo, probe.ref)
    swapRepo ! request1
    probe.expectMessage(Registered(SwapKey(from.name, to.name)))
    swapRepo ! request2
    probe.expectMessage(Registered(SwapKey(to.name, from.name)))

    swapRepo ! GetSwaps(probe.ref)
    probe.expectMessage(Swaps(Set(Swap(swapkey, (member1, member2)))))
  }

  it should "fail to register if to team doesn't exist" in {
    val from   = Team("A", 3)
    val to     = Team("B", 4)
    val member = "132ka"

    val teamRepo           = testKit.spawn(TeamRepo(Map(from.name -> from)))
    val teamMembershipRepo = testKit.spawn(TeamMembershipRepo(Map(from.name -> Set(member))))
    val swapRepo           = testKit.spawn(SwapRepo())

    val probe = testKit.createTestProbe[SwapRepoResponses]()

    val request = SwapRequest(member, from.name, to.name, teamMembershipRepo, teamRepo, probe.ref)

    swapRepo ! request

    probe.expectMessage(Error(s"Team ${from.name} or ${to.name} is unknown"))
  }

  it should "fail to register if from team doesn't exist" in {
    val from   = Team("A", 3)
    val to     = Team("B", 4)
    val member = "132ka"

    val teamRepo           = testKit.spawn(TeamRepo(Map(to.name -> to)))
    val teamMembershipRepo = testKit.spawn(TeamMembershipRepo(Map(from.name -> Set(member))))
    val swapRepo           = testKit.spawn(SwapRepo())

    val probe = testKit.createTestProbe[SwapRepoResponses]()

    val request = SwapRequest(member, from.name, to.name, teamMembershipRepo, teamRepo, probe.ref)

    swapRepo ! request

    probe.expectMessage(Error(s"Member $member is not a member of team ${from.name}"))
  }

  it should "fail to register if team member doesn't belong to the from team" in {
    val from   = Team("A", 3)
    val to     = Team("B", 4)
    val member = "132ka"

    val teamRepo           = testKit.spawn(TeamRepo(Map(from.name -> from, to.name -> to)))
    val teamMembershipRepo = testKit.spawn(TeamMembershipRepo())
    val swapRepo           = testKit.spawn(SwapRepo())

    val probe = testKit.createTestProbe[SwapRepoResponses]()

    val request = SwapRequest(member, from.name, to.name, teamMembershipRepo, teamRepo, probe.ref)

    swapRepo ! request

    probe.expectMessage(Error(s"Member $member is not a member of team ${from.name}"))
  }
}

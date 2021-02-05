package io.github.jlprat.teamswapper.behaviors

import scala.concurrent.duration._

import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import io.github.jlprat.teamswapper.behaviors.TeamBehavior
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.Command
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.FindSwaps
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.RequestChange
import io.github.jlprat.teamswapper.domain.GeneralProtocol.OK
import io.github.jlprat.teamswapper.domain.GeneralProtocol.Response
import io.github.jlprat.teamswapper.domain.Move
import io.github.jlprat.teamswapper.domain.Team
import io.github.jlprat.teamswapper.domain.TeamMember
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TeamBehaviorTest extends ScalaTestWithActorTestKit with AnyFlatSpecLike with Matchers {

  "TeamBehavior" should "register team change requests for team members" in {
    val alice = TeamMember("Alice", "Abbot")
    val teamA = testKit.spawn(TeamBehavior(), "Team-A")
    val teamB = testKit.createTestProbe[Command]("Team-B")
    val probe = testKit.createTestProbe[Response]()

    teamA.tell(RequestChange(alice, teamB.ref, probe.ref))
    probe.expectMessage(OK)

    testKit.stop(teamA)
  }

  it should "find a simple 2-team swap" in {
    val alice         = TeamMember("Alice", "Abbot")
    val bob           = TeamMember("Bob", "Burger")
    val teamABehavior = testKit.spawn(TeamBehavior(), "Team-A")
    val teamBBehavior = testKit.spawn(TeamBehavior(), "Team-B")
    val probe         = testKit.createTestProbe[Response]()
    val swapProbe     = testKit.createTestProbe[Seq[Move]]()

    teamABehavior.tell(RequestChange(alice, teamBBehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamBBehavior.tell(RequestChange(bob, teamABehavior.ref, probe.ref))
    probe.expectMessage(OK)

    val teamA = Team("Team-A")
    val teamB = Team("Team-B")
    teamABehavior.tell(FindSwaps(swapProbe.ref))
    swapProbe.expectMessage(Seq(Move(alice, teamA, teamB), Move(bob, teamB, teamA)))

    testKit.stop(teamABehavior)
    testKit.stop(teamBBehavior)
  }

  it should "find a n-team swap" in {
    val alice         = TeamMember("Alice", "Abbot")
    val bob           = TeamMember("Bob", "Burger")
    val charlie       = TeamMember("Charlie", "Cake")
    val teamABehavior = testKit.spawn(TeamBehavior(), "Team-A")
    val teamBBehavior = testKit.spawn(TeamBehavior(), "Team-B")
    val teamCBehavior = testKit.spawn(TeamBehavior(), "Team-C")
    val probe         = testKit.createTestProbe[Response]()
    val swapProbe     = testKit.createTestProbe[Seq[Move]]()

    teamABehavior.tell(RequestChange(alice, teamBBehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamBBehavior.tell(RequestChange(bob, teamCBehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamCBehavior.tell(RequestChange(charlie, teamABehavior.ref, probe.ref))
    probe.expectMessage(OK)

    val teamA = Team("Team-A")
    val teamB = Team("Team-B")
    val teamC = Team("Team-C")
    teamABehavior.tell(FindSwaps(swapProbe.ref))
    swapProbe.expectMessage(
      Seq(Move(alice, teamA, teamB), Move(bob, teamB, teamC), Move(charlie, teamC, teamA))
    )

    testKit.stop(teamABehavior)
    testKit.stop(teamBBehavior)
    testKit.stop(teamCBehavior)
  }

  it should "not loop infinitely" in {
    //Bob wants to move to either A or D
    //Charlie wants to move to C
    //Alice wants to move to B
    //Bob wants to move to A
    // Starting on team C, we should find an infinite loop between teams A and B
    val alice         = TeamMember("Alice", "Abbot")
    val bob           = TeamMember("Bob", "Burger")
    val charlie       = TeamMember("Charlie", "Cake")
    val dave          = TeamMember("Dave", "Dinner")
    val teamABehavior = testKit.spawn(TeamBehavior(), "Team-A")
    val teamBBehavior = testKit.spawn(TeamBehavior(), "Team-B")
    val teamCBehavior = testKit.spawn(TeamBehavior(), "Team-C")
    val teamDBehavior = testKit.spawn(TeamBehavior(), "Team-D")
    val probe         = testKit.createTestProbe[Response]()
    val swapProbe     = testKit.createTestProbe[Seq[Move]]()

    teamABehavior.tell(RequestChange(alice, teamBBehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamBBehavior.tell(RequestChange(bob, teamABehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamCBehavior.tell(RequestChange(charlie, teamABehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamCBehavior.tell(RequestChange(charlie, teamDBehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamDBehavior.tell(RequestChange(dave, teamCBehavior.ref, probe.ref))
    probe.expectMessage(OK)

    val teamC = Team("Team-C")
    val teamD = Team("Team-D")
    LoggingTestKit.info("Found an inner loop, breaking!").expect {
      teamCBehavior.tell(FindSwaps(swapProbe.ref))
    }
    swapProbe.expectMessage(
      Seq(Move(charlie, teamC, teamD), Move(dave, teamD, teamC))
    )

    testKit.stop(teamABehavior)
    testKit.stop(teamBBehavior)
    testKit.stop(teamCBehavior)
    testKit.stop(teamDBehavior)
  }

  it should "find multiple swaps when they exist" in {
    val alice         = TeamMember("Alice", "Abbot")
    val bob           = TeamMember("Bob", "Burger")
    val charlie       = TeamMember("Charlie", "Cake")
    val teamABehavior = testKit.spawn(TeamBehavior(), "Team-A")
    val teamBBehavior = testKit.spawn(TeamBehavior(), "Team-B")
    val teamCBehavior = testKit.spawn(TeamBehavior(), "Team-C")
    val probe         = testKit.createTestProbe[Response]()
    val swapProbe     = testKit.createTestProbe[Seq[Move]]()

    teamABehavior.tell(RequestChange(alice, teamBBehavior.ref, probe.ref))
    probe.expectMessage(OK)
    teamABehavior.tell(RequestChange(alice, teamCBehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamBBehavior.tell(RequestChange(bob, teamABehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamCBehavior.tell(RequestChange(charlie, teamABehavior.ref, probe.ref))
    probe.expectMessage(OK)

    val teamA = Team("Team-A")
    val teamB = Team("Team-B")
    val teamC = Team("Team-C")
    teamABehavior.tell(FindSwaps(swapProbe.ref))
    val allSwaps = swapProbe.receiveMessages(2)

    allSwaps should contain theSameElementsAs Seq(
      Seq(Move(alice, teamA, teamB), Move(bob, teamB, teamA)),
      Seq(Move(alice, teamA, teamC), Move(charlie, teamC, teamA))
    )

    testKit.stop(teamABehavior)
    testKit.stop(teamBBehavior)
    testKit.stop(teamCBehavior)
  }

  it should "not find any swaps if there aren't any" in {
    val alice         = TeamMember("Alice", "Abbot")
    val bob           = TeamMember("Bob", "Burger")
    val teamABehavior = testKit.spawn(TeamBehavior(), "Team-A")
    val teamBBehavior = testKit.spawn(TeamBehavior(), "Team-B")
    val teamCBehavior = testKit.spawn(TeamBehavior(), "Team-C")
    val probe         = testKit.createTestProbe[Response]()
    val swapProbe     = testKit.createTestProbe[Seq[Move]]()

    teamABehavior.tell(RequestChange(alice, teamBBehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamBBehavior.tell(RequestChange(bob, teamCBehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamABehavior.tell(FindSwaps(swapProbe.ref))
    swapProbe.expectNoMessage(1.second)

    testKit.stop(teamABehavior)
    testKit.stop(teamBBehavior)
    testKit.stop(teamCBehavior)
  }
}

package io.github.jlprat.teamswapper

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import io.github.jlprat.teamswapper.behaviors.TeamBehavior
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.AddTeamMember
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.Error
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.OK
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.Response
import io.github.jlprat.teamswapper.domain.TeamMember
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.RemoveTeamMember
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.RequestChange
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.Command
import io.github.jlprat.teamswapper.domain.Swap
import io.github.jlprat.teamswapper.behaviors.TeamBehavior.FindSwaps
import io.github.jlprat.teamswapper.domain.Team

class TeamBehaviorTest extends ScalaTestWithActorTestKit with AnyFlatSpecLike with Matchers {

  "TeamBehavior" should "add people in team if enough places" in {
    val teamBehavior = testKit.spawn(TeamBehavior(2))
    val probe        = testKit.createTestProbe[Response]()
    val alice        = TeamMember("Alice", "Abbot")
    val bob          = TeamMember("Bob", "Burger")

    teamBehavior.tell(AddTeamMember(alice, probe.ref))
    probe.expectMessage(OK)

    teamBehavior.tell(AddTeamMember(bob, probe.ref))
    probe.expectMessage(OK)

    testKit.stop(teamBehavior)
  }
  it should "behave in idempotent manner if adding a person who is already a team member" in {

    val alice        = TeamMember("Alice", "Abbot")
    val teamBehavior = testKit.spawn(TeamBehavior(2, Set(alice)))
    val probe        = testKit.createTestProbe[Response]()

    teamBehavior.tell(AddTeamMember(alice, probe.ref))
    probe.expectMessage(OK)

    testKit.stop(teamBehavior)
  }

  it should "fail to add a team member if the team is full" in {
    val alice        = TeamMember("Alice", "Abbot")
    val bob          = TeamMember("Bob", "Burger")
    val teamBehavior = testKit.spawn(TeamBehavior(1, Set(alice)))
    val probe        = testKit.createTestProbe[Response]()

    teamBehavior.tell(AddTeamMember(bob, probe.ref))
    probe.expectMessage(Error("Team is already full, can't add Bob"))

    testKit.stop(teamBehavior)
  }

  it should "be able to remove team members" in {
    val alice        = TeamMember("Alice", "Abbot")
    val teamBehavior = testKit.spawn(TeamBehavior(2, Set(alice)))
    val probe        = testKit.createTestProbe[Response]()

    teamBehavior.tell(RemoveTeamMember(alice, probe.ref))
    probe.expectMessage(OK)

    testKit.stop(teamBehavior)
  }

  it should "fail to remove a non existing team member" in {
    val alice        = TeamMember("Alice", "Abbot")
    val bob          = TeamMember("Bob", "Burger")
    val teamBehavior = testKit.spawn(TeamBehavior(1, Set(alice)))
    val probe        = testKit.createTestProbe[Response]()

    teamBehavior.tell(RemoveTeamMember(bob, probe.ref))
    probe.expectMessage(Error("Can't remove Bob from team, because is not part of the team"))

    testKit.stop(teamBehavior)
  }

  it should "register team change requests for team members" in {
    val alice = TeamMember("Alice", "Abbot")
    val teamA = testKit.spawn(TeamBehavior(1, Set(alice)), "Team-A")
    val teamB = testKit.createTestProbe[Command]("Team-B")
    val probe = testKit.createTestProbe[Response]()

    teamA.tell(RequestChange(alice, teamB.ref, probe.ref))
    probe.expectMessage(OK)

    testKit.stop(teamA)
  }

  it should "fail to register a team change request for non team members" in {
    val alice = TeamMember("Alice", "Abbot")
    val bob   = TeamMember("Bob", "Burger")
    val teamA = testKit.spawn(TeamBehavior(1, Set(alice)), "Team-A")
    val teamB = testKit.createTestProbe[Command]("Team-B")
    val probe = testKit.createTestProbe[Response]()

    teamA.tell(RequestChange(bob, teamB.ref, probe.ref))
    probe.expectMessage(Error("Can't register request change as Bob is not part of the team"))

    testKit.stop(teamA)
  }

  it should "find a simple 2-team swap" in {
    val alice         = TeamMember("Alice", "Abbot")
    val bob           = TeamMember("Bob", "Burger")
    val teamABehavior = testKit.spawn(TeamBehavior(1, Set(alice)), "Team-A")
    val teamBBehavior = testKit.spawn(TeamBehavior(1, Set(bob)), "Team-B")
    val probe         = testKit.createTestProbe[Response]()
    val swapProbe     = testKit.createTestProbe[Seq[Swap]]()

    teamABehavior.tell(RequestChange(alice, teamBBehavior.ref, probe.ref))
    probe.expectMessage(OK)

    teamBBehavior.tell(RequestChange(bob, teamABehavior.ref, probe.ref))
    probe.expectMessage(OK)

    val teamA = Team("Team-A")
    val teamB = Team("Team-B")
    teamABehavior.tell(FindSwaps(swapProbe.ref))
    swapProbe.expectMessage(Seq(Swap(alice, teamA, teamB), Swap(bob, teamB, teamA)))

    testKit.stop(teamABehavior)
    testKit.stop(teamBBehavior)
  }

  it should "find a n-team swap" in {
    val alice         = TeamMember("Alice", "Abbot")
    val bob           = TeamMember("Bob", "Burger")
    val charlie       = TeamMember("Charlie", "Cake")
    val teamABehavior = testKit.spawn(TeamBehavior(1, Set(alice)), "Team-A")
    val teamBBehavior = testKit.spawn(TeamBehavior(1, Set(bob)), "Team-B")
    val teamCBehavior = testKit.spawn(TeamBehavior(1, Set(charlie)), "Team-C")
    val probe         = testKit.createTestProbe[Response]()
    val swapProbe     = testKit.createTestProbe[Seq[Swap]]()

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
      Seq(Swap(alice, teamA, teamB), Swap(bob, teamB, teamC), Swap(charlie, teamC, teamA))
    )

    testKit.stop(teamABehavior)
    testKit.stop(teamBBehavior)
    testKit.stop(teamCBehavior)
  }

}

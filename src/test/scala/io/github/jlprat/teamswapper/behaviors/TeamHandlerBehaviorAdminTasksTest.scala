package io.github.jlprat.teamswapper.behaviors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import io.github.jlprat.teamswapper.behaviors.TeamHandlerBehavior
import io.github.jlprat.teamswapper.domain.GeneralProtocol.Error
import io.github.jlprat.teamswapper.domain.GeneralProtocol.OK
import io.github.jlprat.teamswapper.domain.GeneralProtocol.Response

import io.github.jlprat.teamswapper.domain.Team
import io.github.jlprat.teamswapper.domain.TeamMember
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.github.jlprat.teamswapper.behaviors.TeamHandlerBehavior.CreateTeam
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import akka.actor.testkit.typed.Effect.Spawned
import io.github.jlprat.teamswapper.behaviors.TeamHandlerBehavior.TeamInfo
import io.github.jlprat.teamswapper.behaviors.TeamHandlerBehavior.AddMember
import io.github.jlprat.teamswapper.behaviors.TeamHandlerBehavior.RemoveMember

class TeamHandlerBehaviorAdminTasksTest extends ScalaTestWithActorTestKit with AnyFlatSpecLike with Matchers {

  "TeamBehavior" should "create new teams" in {
    val teamHandlerBehavior = BehaviorTestKit(TeamHandlerBehavior(Map.empty, Map.empty))
    val probe               = TestInbox[Response]()
    val team                = Team("A-Team")

    val _ = teamHandlerBehavior.retrieveAllEffects()

    teamHandlerBehavior.run(CreateTeam(team.name, 3, probe.ref))

    probe.expectMessage(OK)

    teamHandlerBehavior.expectEffectPF {
      case spawned: Spawned[_] => spawned.childName shouldBe team.name
    }
  }

  it should "fail if the team to create already exists" in {
    val team     = Team("A-Team")
    val fakeTeam = TestInbox[TeamBehavior.Command]()
    val teamHandlerBehavior =
      BehaviorTestKit(TeamHandlerBehavior(Map(team -> TeamInfo(fakeTeam.ref, 2)), Map.empty))
    val probe = TestInbox[Response]()

    teamHandlerBehavior.run(CreateTeam(team.name, 3, probe.ref))

    probe.expectMessage(Error(s"Team ${team.name} already exists"))

  }

  it should "add people in team if enough places" in {
    val team     = Team("A-Team")
    val fakeTeam = TestInbox[TeamBehavior.Command]()
    val teamHandlerBehavior =
      BehaviorTestKit(TeamHandlerBehavior(Map(team -> TeamInfo(fakeTeam.ref, 2)), Map.empty))
    val probe = TestInbox[Response]()
    val alice = TeamMember("Alice", "Abbot")

    teamHandlerBehavior.run(AddMember(team, alice, probe.ref))

    probe.expectMessage(OK)

  }
  it should "behave in idempotent manner if adding a person who is already a team member" in {

    val team     = Team("A-Team")
    val alice    = TeamMember("Alice", "Abbot")
    val fakeTeam = TestInbox[TeamBehavior.Command]()
    val teamHandlerBehavior =
      BehaviorTestKit(
        TeamHandlerBehavior(Map(team -> TeamInfo(fakeTeam.ref, 2)), Map(alice -> team))
      )
    val probe = TestInbox[Response]()

    teamHandlerBehavior.run(AddMember(team, alice, probe.ref))

    probe.expectMessage(OK)
  }

  it should "fail to add a team member if the team is full" in {
    val team     = Team("A-Team")
    val alice    = TeamMember("Alice", "Abbot")
    val bob      = TeamMember("Bob", "Burger")
    val fakeTeam = TestInbox[TeamBehavior.Command]()
    val teamHandlerBehavior =
      BehaviorTestKit(
        TeamHandlerBehavior(Map(team -> TeamInfo(fakeTeam.ref, 0)), Map(alice -> team))
      )
    val probe = TestInbox[Response]()

    teamHandlerBehavior.run(AddMember(team, bob, probe.ref))
    probe.expectMessage(Error(s"Team ${team.name} is already full, can't add ${bob.name}"))
  }

  it should "be able to remove team members" in {
    val team     = Team("A-Team")
    val alice    = TeamMember("Alice", "Abbot")
    val fakeTeam = TestInbox[TeamBehavior.Command]()
    val teamHandlerBehavior =
      BehaviorTestKit(
        TeamHandlerBehavior(Map(team -> TeamInfo(fakeTeam.ref, 0)), Map(alice -> team))
      )
    val probe = TestInbox[Response]()

    teamHandlerBehavior.run(RemoveMember(team, alice, probe.ref))
    probe.expectMessage(OK)
  }

  it should "fail to remove a team member from another team" in {
    val aTeam    = Team("A-Team")
    val bTeam    = Team("B-Team")
    val alice    = TeamMember("Alice", "Abbot")
    val bob      = TeamMember("Bob", "Burger")
    val fakeTeam = TestInbox[TeamBehavior.Command]()
    val teamHandlerBehavior =
      BehaviorTestKit(
        TeamHandlerBehavior(
          Map(aTeam -> TeamInfo(fakeTeam.ref, 0)),
          Map(alice -> aTeam, bob -> bTeam)
        )
      )
    val probe = TestInbox[Response]()

    teamHandlerBehavior.run(RemoveMember(aTeam, bob, probe.ref))
    probe.expectMessage(
      Error(s"Can't remove ${bob.name} from team, because is not part of the team")
    )
  }

  it should "fail to remove a non existing team member" in {
    val team     = Team("A-Team")
    val alice    = TeamMember("Alice", "Abbot")
    val bob      = TeamMember("Bob", "Burger")
    val fakeTeam = TestInbox[TeamBehavior.Command]()
    val teamHandlerBehavior =
      BehaviorTestKit(
        TeamHandlerBehavior(Map(team -> TeamInfo(fakeTeam.ref, 0)), Map(alice -> team))
      )
    val probe = TestInbox[Response]()

    teamHandlerBehavior.run(RemoveMember(team, bob, probe.ref))
    probe.expectMessage(
      Error(s"Can't remove ${bob.name} from team, because is not part of the team")
    )
  }

  it should "fail to remove a team member from a non existing team" in {
    val aTeam    = Team("A-Team")
    val bTeam    = Team("B-Team")
    val alice    = TeamMember("Alice", "Abbot")
    val fakeTeam = TestInbox[TeamBehavior.Command]()
    val teamHandlerBehavior =
      BehaviorTestKit(
        TeamHandlerBehavior(Map(aTeam -> TeamInfo(fakeTeam.ref, 0)), Map(alice -> aTeam))
      )
    val probe = TestInbox[Response]()

    teamHandlerBehavior.run(RemoveMember(bTeam, alice, probe.ref))
    probe.expectMessage(
      Error(s"Team ${bTeam.name} doesn't exist")
    )
  }
}

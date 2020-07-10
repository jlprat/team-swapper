package io.github.jlprat.teamswapper

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should._
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox

class TeamRepoTest extends AnyFlatSpec with Matchers {

  "TeamRepo" should "accept team creation messages for new teams" in {
    val repoBehavior = BehaviorTestKit(TeamRepo())
    val inbox = TestInbox[TeamRepo.TeamRepoResponses]()
    val newTeamMsg = TeamRepo.NewTeam("A", 4, inbox.ref)
    repoBehavior.run(newTeamMsg)

    inbox.expectMessage(TeamRepo.Created(Team("A", 4)))
  }

  it should "accept team creation message if the team already exists and has same capacity" in {
    val team = Team("A", 3)
    val repoBehavior = BehaviorTestKit(TeamRepo(Map(team.name -> team)))
    val inbox = TestInbox[TeamRepo.TeamRepoResponses]()
    val newTeamMsg = TeamRepo.NewTeam(team.name, team.capacity, inbox.ref)
    repoBehavior.run(newTeamMsg)

    inbox.expectMessage(TeamRepo.Created(team))
  }

  it should "reject creation if the team already exists but differs in capacity" in {
    val team = Team("A", 3)
    val repoBehavior = BehaviorTestKit(TeamRepo(Map(team.name -> team)))
    val inbox = TestInbox[TeamRepo.TeamRepoResponses]()
    val newTeamMsg = TeamRepo.NewTeam(team.name, team.capacity + 3, inbox.ref)
    repoBehavior.run(newTeamMsg)

    inbox.expectMessage(TeamRepo.Failure(s"Team ${team.name} already exists with different capacity"))
  }

  it should "update existing teams" in {
    val team = Team("A", 3)
    val repoBehavior = BehaviorTestKit(TeamRepo(Map(team.name -> team)))
    val inbox = TestInbox[TeamRepo.TeamRepoResponses]()
    val updateTeam = TeamRepo.UpdateTeam(team.name, team.capacity + 3, inbox.ref)
    repoBehavior.run(updateTeam)

    inbox.expectMessage(TeamRepo.Updated(Team(team.name, team.capacity + 3)))
  }

  it should "reject updating non existing teams" in {
    val team = Team("A", 3)
    val repoBehavior = BehaviorTestKit(TeamRepo())
    val inbox = TestInbox[TeamRepo.TeamRepoResponses]()
    val newTeamMsg = TeamRepo.UpdateTeam(team.name, team.capacity, inbox.ref)
    repoBehavior.run(newTeamMsg)

    inbox.expectMessage(TeamRepo.Failure(s"Team ${team.name} not present"))
  }

  it should "list empty lists when there are no teams" in {
    val repoBehavior = BehaviorTestKit(TeamRepo())
    val inbox = TestInbox[TeamRepo.TeamRepoResponses]()
    repoBehavior.run(TeamRepo.ListTeams(inbox.ref))

    inbox.expectMessage(TeamRepo.Teams(Set.empty))
  }

  it should "list all teams" in {
    val teamA = Team("A", 3)
    val teamB = Team("B", 3)
    val repoBehavior = BehaviorTestKit(TeamRepo(Map(teamA.name -> teamA, teamB.name -> teamB)))
    val inbox = TestInbox[TeamRepo.TeamRepoResponses]()
    repoBehavior.run(TeamRepo.ListTeams(inbox.ref))

    inbox.expectMessage(TeamRepo.Teams(Set(teamA, teamB)))
  }

  it should "create and list teams" in {
    val team = Team("A", 4)
    val repoBehavior = BehaviorTestKit(TeamRepo())
    val inbox = TestInbox[TeamRepo.TeamRepoResponses]()
    val newTeamMsg = TeamRepo.NewTeam(team.name, team.capacity, inbox.ref)
    repoBehavior.run(newTeamMsg)

    inbox.expectMessage(TeamRepo.Created(team))

    repoBehavior.run(TeamRepo.ListTeams(inbox.ref))

    inbox.expectMessage(TeamRepo.Teams(Set(team)))
  }

  it should "be able to get a specific team" in {
    val team = Team("A", 4)
    val repoBehavior = BehaviorTestKit(TeamRepo(Map(team.name -> team)))
    val inbox = TestInbox[TeamRepo.TeamRepoResponses]()
    repoBehavior.run(TeamRepo.GetTeam(team.name, inbox.ref))

    inbox.expectMessage(TeamRepo.Present(team))
  }
}

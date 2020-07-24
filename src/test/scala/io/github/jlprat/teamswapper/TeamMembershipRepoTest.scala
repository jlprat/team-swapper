package io.github.jlprat.teamswapper

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should._
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.BeforeAndAfterAll

class TeamMembershipRepoTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  val testKit = ActorTestKit()

  "TeamMembershipRepo" should "acccept memberships if team is new" in {
    val team                   = Team("A", 3)
    val member                 = "12k"
    val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo())
    val inbox                  = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()
    teamMembershipBehavior.run(TeamMembershipRepo.JoinTeam(team, member, inbox.ref))

    inbox.expectMessage(TeamMembershipRepo.Joined)
  }

  it should "acccept memberships if team is already there" in {
    val team                   = Team("A", 3)
    val member1                = "12k"
    val member2                = "124k"
    val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo(Map(team.name -> Set(member1))))
    val inbox                  = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()
    teamMembershipBehavior.run(TeamMembershipRepo.JoinTeam(team, member2, inbox.ref))

    inbox.expectMessage(TeamMembershipRepo.Joined)
  }

  it should "reject a request to join if team is already full" in {
    val team                   = Team("A", 1)
    val member1                = "12k"
    val member2                = "124k"
    val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo(Map(team.name -> Set(member1))))
    val inbox                  = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()
    teamMembershipBehavior.run(TeamMembershipRepo.JoinTeam(team, member2, inbox.ref))

    inbox.expectMessage(TeamMembershipRepo.TeamFull)
  }

  it should "accept leave request from team members" in {
    val team                   = Team("A", 3)
    val member                 = "12k"
    val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo(Map(team.name -> Set(member))))
    val inbox                  = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()
    teamMembershipBehavior.run(TeamMembershipRepo.LeaveTeam(team, member, inbox.ref))

    inbox.expectMessage(TeamMembershipRepo.Left)
  }

  it should "reject leave request from non team members" in {
    val team                   = Team("A", 3)
    val member1                = "12k"
    val member2                = "11k"
    val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo(Map(team.name -> Set(member1))))
    val inbox                  = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()
    teamMembershipBehavior.run(TeamMembershipRepo.LeaveTeam(team, member2, inbox.ref))

    inbox.expectMessage(TeamMembershipRepo.Member(false))
  }

  it should "reply with membership of team members once the team is retrieved" in {
    val team                   = Team("A", 3)
    val member1                = "12k"
    val member2                = "11k"
    val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo(Map(team.name -> Set(member1))))
    val inbox                  = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()

    teamMembershipBehavior.run(TeamMembershipRepo.IsMemberInt(team, member2, inbox.ref))
    inbox.expectMessage(TeamMembershipRepo.Member(false))

    teamMembershipBehavior.run(TeamMembershipRepo.IsMemberInt(team, member1, inbox.ref))
    inbox.expectMessage(TeamMembershipRepo.Member(true))
  }

  it should "reply with membership of team members" in {
    val team                   = Team("A", 3)
    val member1                = "12k"
    val member2                = "11k"
    val teamMembershipBehavior = testKit.spawn(TeamMembershipRepo(Map(team.name -> Set(member1))))

    val inbox = testKit.createTestProbe[TeamMembershipRepo.TeamMembershipResponses]()
    val teamRepo = testKit.spawn(TeamRepo(Map(team.name -> team)))

    teamMembershipBehavior.tell(
      TeamMembershipRepo.IsMember(team.name, member2, teamRepo, inbox.ref)
    )
    inbox.expectMessage(TeamMembershipRepo.Member(false))

    teamMembershipBehavior.tell(TeamMembershipRepo.IsMember(team.name, member1, teamRepo, inbox.ref))
    inbox.expectMessage(TeamMembershipRepo.Member(true))

    teamMembershipBehavior.tell(
      TeamMembershipRepo.IsMember("NOT EXISTING TEAM", member2, teamRepo, inbox.ref)
    )
    inbox.expectMessage(TeamMembershipRepo.Member(false))
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}

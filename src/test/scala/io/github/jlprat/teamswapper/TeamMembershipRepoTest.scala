package io.github.jlprat.teamswapper

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should._
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.testkit.typed.scaladsl.TestInbox

class TeamMembershipRepoTest extends AnyFlatSpec with Matchers {

    "TeamMembershipRepo" should "acccept memberships if team is new" in {
        val team = TeamRepo.Team("A", 3)
        val member = TeamMembershipRepo.Member("12k", "Alice")
        val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo())
        val inbox = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()
        teamMembershipBehavior.run(TeamMembershipRepo.JoinTeam(team, member, inbox.ref))

        inbox.expectMessage(TeamMembershipRepo.Joined)
    }

    it should "acccept memberships if team is already there" in {
        val team = TeamRepo.Team("A", 3)
        val member1 = TeamMembershipRepo.Member("12k", "Alice")
        val member2 = TeamMembershipRepo.Member("124k", "Bob")
        val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo(Map(team.name -> Set(member1))))
        val inbox = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()
        teamMembershipBehavior.run(TeamMembershipRepo.JoinTeam(team, member2, inbox.ref))

        inbox.expectMessage(TeamMembershipRepo.Joined)
    }

    it should "reject a request to join if team is already full" in {
        val team = TeamRepo.Team("A", 1)
        val member1 = TeamMembershipRepo.Member("12k", "Alice")
        val member2 = TeamMembershipRepo.Member("124k", "Bob")
        val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo(Map(team.name -> Set(member1))))
        val inbox = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()
        teamMembershipBehavior.run(TeamMembershipRepo.JoinTeam(team, member2, inbox.ref))

        inbox.expectMessage(TeamMembershipRepo.TeamFull)
    }

    it should "accept leave request from team members" in {
        val team = TeamRepo.Team("A", 3)
        val member = TeamMembershipRepo.Member("12k", "Alice")
        val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo(Map(team.name -> Set(member))))
        val inbox = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()
        teamMembershipBehavior.run(TeamMembershipRepo.LeaveTeam(team, member, inbox.ref))

        inbox.expectMessage(TeamMembershipRepo.Left)
    }


    it should "reject leave request from non team members" in {
        val team = TeamRepo.Team("A", 3)
        val member1 = TeamMembershipRepo.Member("12k", "Alice")
        val member2 = TeamMembershipRepo.Member("11k", "Bob")
        val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo(Map(team.name -> Set(member1))))
        val inbox = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()
        teamMembershipBehavior.run(TeamMembershipRepo.LeaveTeam(team, member2, inbox.ref))

        inbox.expectMessage(TeamMembershipRepo.NotMember)
    }

    it should "reply with membership of team members" in {
        val team = TeamRepo.Team("A", 3)
        val member1 = TeamMembershipRepo.Member("12k", "Alice")
        val member2 = TeamMembershipRepo.Member("11k", "Bob")
        val teamMembershipBehavior = BehaviorTestKit(TeamMembershipRepo(Map(team.name -> Set(member1))))
        val inbox = TestInbox[TeamMembershipRepo.TeamMembershipResponses]()
        
        teamMembershipBehavior.run(TeamMembershipRepo.IsMember(team, member2, inbox.ref))
        inbox.expectMessage(TeamMembershipRepo.NotMember)

        teamMembershipBehavior.run(TeamMembershipRepo.IsMember(team, member1, inbox.ref))
        inbox.expectMessage(TeamMembershipRepo.Member)
    }
}

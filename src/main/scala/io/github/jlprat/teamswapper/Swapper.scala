package io.github.jlprat.teamswapper

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import scala.concurrent.duration._
import TeamRepo._
import TeamMembershipRepo._
import io.github.jlprat.teamswapper.SwapRepo.SwapRepoActions
import scala.concurrent.Future
import akka.actor.typed.ActorSystem
import scala.concurrent.ExecutionContext


class Swapper(teamRepo: ActorRef[TeamRepo.TeamRepoActions], teamMembershipRepo: ActorRef[TeamMembershipActions], swapRepo: ActorRef[SwapRepoActions])(implicit system: ActorSystem[_]) {
  

    implicit val timeout: Timeout = 3.seconds
 
    def requestSwap(memberId: String, from: String, to: String)(implicit ec: ExecutionContext): Future[Boolean] = {
        
        for {
            fromTeam <- teamRepo.ask(ref => GetTeam(from, ref))
            toTeam <- teamRepo.ask(ref => GetTeam(to, ref))
            member <- teamMembershipRepo.ask(ref => IsMember(fromTeam.team, memberId, ref)) if member.is
            
        } yield true
    }
}

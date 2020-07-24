package io.github.jlprat.teamswapper.behaviors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import io.github.jlprat.teamswapper.Team

object TeamRepo {

  sealed trait TeamRepoActions
  case class NewTeam(
      name: String,
      capacity: Int,
      replyTo: ActorRef[TeamRepoResponses]
  )                                                          extends TeamRepoActions
  case class ListTeams(replyTo: ActorRef[TeamRepoResponses]) extends TeamRepoActions
  case class UpdateTeam(
      name: String,
      capacity: Int,
      replyTo: ActorRef[TeamRepoResponses]
  )                                                                      extends TeamRepoActions
  case class GetTeam(name: String, replyTo: ActorRef[TeamRepoResponses]) extends TeamRepoActions

  sealed trait TeamRepoResponses
  case class Failure(reason: String) extends TeamRepoResponses
  case class Created(team: Team)     extends TeamRepoResponses
  case class Updated(team: Team)     extends TeamRepoResponses
  case class Teams(teams: Set[Team]) extends TeamRepoResponses
  case class Present(team: Team)     extends TeamRepoResponses

  def apply(teams: Map[String, Team] = Map.empty): Behavior[TeamRepoActions] =
    Behaviors.receiveMessage {
      case NewTeam(name, capacity, replyTo)
          if teams.isDefinedAt(name) && teams(name).capacity == capacity =>
        replyTo ! Created(Team(name, capacity))
        Behaviors.same
      case NewTeam(name, _, replyTo) if teams.isDefinedAt(name) =>
        replyTo ! Failure(s"Team $name already exists with different capacity")
        Behaviors.same
      case NewTeam(name, capacity, replyTo) =>
        val team = Team(name, capacity)
        replyTo ! Created(team)
        apply(teams.+(name -> team))
      case UpdateTeam(name, capacity, replyTo) if teams isDefinedAt (name) =>
        val team = Team(name, capacity)
        replyTo ! Updated(team)
        apply(teams.updated(name, team))
      case UpdateTeam(name, _, replyTo) =>
        replyTo ! Failure(s"Team $name not present")
        Behaviors.same
      case ListTeams(replyTo) =>
        replyTo ! Teams(teams.values.toSet)
        Behaviors.same
      case GetTeam(name, replyTo) =>
        replyTo ! teams.get(name).map(team => Present(team)).getOrElse(Failure(s"No team with this name: $name"))
        Behaviors.same
    }

}
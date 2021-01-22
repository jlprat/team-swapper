package io.github.jlprat.teamswapper.domain

object GeneralProtocol {
  sealed trait Response
  final case object OK                extends Response
  final case class Error(msg: String) extends Response
}

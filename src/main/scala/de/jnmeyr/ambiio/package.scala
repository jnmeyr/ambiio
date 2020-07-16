package de.jnmeyr

package object ambiio {

  type Consume[F[_]] = F[Bridge.Values]

  type Consumer[F[_]] = Consume[F] => F[Unit]

  type Produce[F[_]] = Bridge.Values => F[Unit]

  type Producer[F[_]] = (Produce[F], F[Boolean]) => F[Unit]

}

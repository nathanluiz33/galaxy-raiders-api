package galaxyraiders.core.game

import galaxyraiders.Config
import galaxyraiders.core.physics.Point2D
import galaxyraiders.core.physics.Vector2D

object ExplosionConfig {
  private val config = Config(prefix = "GR__CORE__GAME__SPACE_SHIP__")

  val boost = config.get<Double>("BOOST")
}

class Explosion(
//   is_triggered: Boolean,
  initialPosition: Point2D,
  initialVelocity: Vector2D,
  radius: Double,
  mass: Double
) :
  SpaceObject("Explosion", '@', initialPosition, initialVelocity, radius, mass)

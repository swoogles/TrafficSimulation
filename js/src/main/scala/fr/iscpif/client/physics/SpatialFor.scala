package fr.iscpif.client.physics

trait SpatialFor[A] {
  def makeSpatial(a: A): Spatial
}

object SpatialFor {
  def disect[T: SpatialFor](t: T): Spatial =
    implicitly[SpatialFor[T]].makeSpatial(t)
}

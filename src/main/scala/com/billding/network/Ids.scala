package com.billding.network

/**
  * A section's stable name.
  *
  * Plain strings rather than generated UUIDs are the point: a hand-written test
  * fixture reads as `SectionId("mainline-0")`, and phase J needs to derive a
  * child ID from a parent when it splits a section mid-road.
  */
final case class SectionId(value: String) extends AnyVal

/** A movement's stable name, same reasoning as [[SectionId]]. */
final case class MovementId(value: String) extends AnyVal

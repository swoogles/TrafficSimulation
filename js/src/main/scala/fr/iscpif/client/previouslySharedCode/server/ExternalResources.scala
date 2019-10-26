package fr.iscpif.client.previouslySharedCode.server

import scalatags.Text.all.href

import scalatags.Text.all._
import scalatags.Text.{all => tags}

object ExternalResources {

  val bootstrapMin = LocalAndCdnResources(
    "css/bootstrap.min.css",
    "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"
  )

  val bttn = LocalAndCdnResources(
    "css/bttn.min.css",
    "https://cdnjs.com/libraries/bttn.css" // TODO This isn't the file I expect...
  )

  val allResources = List(
    bootstrapMin,
    bttn
  )

  val mkStyleSheet =
    (ref: String) =>
      tags.link(tags.rel := "stylesheet",
                tags.`type` := "text/css",
                href := ref)

  val localResources =
    allResources
      .map(_.localRef)
      .map(mkStyleSheet)

  val externalResources =
    allResources
      .map(_.cdnRef)
      .map(mkStyleSheet)

}

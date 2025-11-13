package lila.official
package ui

import lila.ui.Helpers

final class OfficialUi(helpers: Helpers):
  export helpers.{ *, given }

  lazy val show: OfficialShowUi = OfficialShowUi(helpers)
  lazy val home: OfficialHomeUi = OfficialHomeUi(helpers)
  lazy val form: OfficialFormUi = OfficialFormUi(helpers)(translatedVariantChoicesWithVariants)

  // Helper for variant choices (same as Swiss)
  private def translatedVariantChoicesWithVariants(
      f: chess.variant.Variant => String
  )(using lila.core.i18n.Translate): List[(String, String, Option[String])] =
    chess.variant.Variant.list.all.map: v =>
      (v.id.toString, f(v), v.title.some)

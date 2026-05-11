package com.thejusticeman.glyphos

const val SPECIAL_ACTION_OPEN_APP_LIST = "open_app_list"

data class SpecialFunction(
  val id: String,
  val label: String,
  val subtitle: String,
)

object SpecialFunctions {
  val all: List<SpecialFunction> = listOf(
    SpecialFunction(
      id = SPECIAL_ACTION_OPEN_APP_LIST,
      label = "Open app list",
      subtitle = "Special function",
    ),
  )

  fun find(id: String?): SpecialFunction? {
    if (id == null) return null
    return all.firstOrNull { it.id == id }
  }

  fun asTarget(function: SpecialFunction): AppDetail {
    return AppDetail(
      label = function.label,
      packageName = "glyphos.special.${function.id}",
      icon = null,
      specialActionId = function.id,
      subtitle = function.subtitle,
    )
  }
}

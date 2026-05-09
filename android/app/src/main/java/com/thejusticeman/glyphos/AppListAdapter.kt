package com.thejusticeman.glyphos

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class AppListAdapter(private val context: Context) : BaseAdapter() {
  var apps: List<AppDetail> = emptyList()
    set(value) {
      field = value
      notifyDataSetChanged()
    }

  override fun getCount(): Int = apps.size

  override fun getItem(position: Int): AppDetail = apps[position]

  override fun getItemId(position: Int): Long = apps[position].packageName.hashCode().toLong()

  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    val holder: Holder
    val row = if (convertView == null) {
      val root = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
      }
      val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
      }
      val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      }
      val label = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 15f
        maxLines = 1
      }
      val packageName = TextView(context).apply {
        setTextColor(Color.rgb(136, 136, 136))
        textSize = 12f
        maxLines = 1
      }
      textColumn.addView(label)
      textColumn.addView(packageName)
      root.addView(icon)
      root.addView(textColumn)
      holder = Holder(icon, label, packageName)
      root.tag = holder
      root
    } else {
      holder = convertView.tag as Holder
      convertView as LinearLayout
    }

    val app = getItem(position)
    holder.icon.setImageDrawable(app.icon)
    holder.label.text = app.label
    holder.packageName.text = app.packageName
    return row
  }

  private data class Holder(
    val icon: ImageView,
    val label: TextView,
    val packageName: TextView,
  )

  private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}

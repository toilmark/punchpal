package com.punchpal.app

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.Collator
import java.util.Locale

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable?
)

/**
 * 列出手机里所有带桌面图标的 App，供用户选择“要跳到哪个打卡软件”。
 */
class AppPickerDialog(private val context: Context) {

    fun show(onPick: (AppEntry) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_app_picker, null)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerApps)
        val emptyText = view.findViewById<TextView>(R.id.textEmpty)
        val search = view.findViewById<EditText>(R.id.editSearch)

        recycler.layoutManager = LinearLayoutManager(context)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pick_app)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .create()

        val all = loadLaunchableApps()
        val adapter = AppAdapter { entry ->
            onPick(entry)
            dialog.dismiss()
        }
        recycler.adapter = adapter
        adapter.submit(all)
        emptyText.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val keyword = s?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                val filtered = if (keyword.isEmpty()) {
                    all
                } else {
                    all.filter {
                        it.label.lowercase(Locale.getDefault()).contains(keyword) ||
                            it.packageName.lowercase(Locale.getDefault()).contains(keyword)
                    }
                }
                adapter.submit(filtered)
                emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        dialog.show()
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.CHINA)

        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map {
                AppEntry(
                    label = pm.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    icon = pm.getApplicationIcon(it)
                )
            }
            .sortedWith(Comparator { a, b -> collator.compare(a.label, b.label) })
            .toList()
    }

    private class AppAdapter(private val onClick: (AppEntry) -> Unit) :
        RecyclerView.Adapter<AppAdapter.Holder>() {

        private val items = ArrayList<AppEntry>()

        fun submit(list: List<AppEntry>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = items[position]
            holder.icon.setImageDrawable(entry.icon)
            holder.title.text = entry.label
            holder.subtitle.text = entry.packageName
            holder.itemView.setOnClickListener { onClick(entry) }
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.imageIcon)
            val title: TextView = view.findViewById(R.id.textLabel)
            val subtitle: TextView = view.findViewById(R.id.textPackage)
        }
    }
}

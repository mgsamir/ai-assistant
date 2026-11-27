package com.example.assistant

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Reusable screen content; host Activity can place these views in its existing navigation. */
object AssistantModuleScreens {
    fun upgrades(context: Context, manager: UpgradeManager, upgrader: GitHubUpgrader, refresh: () -> Unit): View {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        val scroll = ScrollView(context); val cards = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }; scroll.addView(cards); root.addView(scroll)
        fun render() {
            cards.removeAllViews()
            val items = manager.pending()
            if (items.isEmpty()) cards.addView(TextView(context).apply { text = "No pending upgrades."; setTextColor(Color.WHITE) })
            items.forEach { item ->
                val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); setBackgroundColor(0xff202027.toInt()) }
                card.addView(TextView(context).apply { text = "${item.title}\nSource: ${item.source}\n\n${item.reason}\n\nWhat: ${item.description}\n\nRisk: ${item.risk}\n\nWhere: ${item.location}"; setTextColor(Color.WHITE) })
                val actions = LinearLayout(context).apply { gravity = Gravity.END }
                actions.addView(Button(context).apply { text = "Deny"; setOnClickListener { manager.deny(item.id); render(); refresh() } })
                actions.addView(Button(context).apply { text = "Approve / create PR"; setOnClickListener { val result = upgrader.approveAndOpenPullRequest(item.id); text = if (result.ok) "PR opened" else result.message; if (result.ok) { render(); refresh() } } })
                card.addView(actions); cards.addView(card)
            }
        }
        render(); return root
    }
    fun health(context: Context, report: SelfAuditor.Report): View = TextView(context).apply { text = report.render(); setTextColor(Color.WHITE); setPadding(24, 16, 24, 16) }
}

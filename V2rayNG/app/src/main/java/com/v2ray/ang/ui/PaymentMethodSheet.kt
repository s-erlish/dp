package com.v2ray.ang.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.v2ray.ang.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Reusable, Incy-styled (dark surface + blue accents) bottom sheet that lets the user pick a
 * payment method. It exists because the backend rejects payments with HTTP 400
 * "Не выбран способ оплаты" when the app sends no method — callers show this sheet, get the
 * chosen id back through [onPicked], and forward it to the payment request.
 *
 * Rows are built at runtime from [item_payment_method] into the [sheet_payment_method]
 * container, so the sheet works with any number of Platega methods (СБП, карта…), plus an
 * optional "С баланса" row that returns the id "balance".
 *
 * Because a BottomSheetDialogFragment is recreated on rotation (and its lambda cannot be
 * saved in a Bundle), the [onPicked] callback is kept in a static, per-instance holder keyed
 * by an id stored in the fragment arguments. If the process is killed and the callback is
 * lost, a later pick simply dismisses without firing — acceptable for this transient UX.
 */
class PaymentMethodSheet : BottomSheetDialogFragment() {

    /** Chosen method id, or "balance" for the balance row. May be set directly if constructed by hand. */
    var onPicked: ((methodId: String) -> Unit)? = null

    private var title: String = ""
    private var balanceLabel: String? = null
    private var methods: List<Pair<String, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        if (args != null) {
            title = args.getString(ARG_TITLE, "")
            balanceLabel = args.getString(ARG_BALANCE_LABEL)
            val ids = args.getStringArrayList(ARG_METHOD_IDS) ?: arrayListOf()
            val labels = args.getStringArrayList(ARG_METHOD_LABELS) ?: arrayListOf()
            methods = ids.indices.map { ids[it] to (labels.getOrNull(it) ?: ids[it]) }
            // Recover the callback registered in show(); may be null after process death.
            if (onPicked == null) {
                onPicked = callbackHolder[args.getLong(ARG_CALLBACK_KEY, 0L)]
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val root = inflater.inflate(R.layout.sheet_payment_method, container, false)

        val tvTitle = root.findViewById<TextView>(R.id.tv_pay_title)
        tvTitle.text = title.ifBlank { getString(R.string.pay_method_title) }

        val rows = root.findViewById<LinearLayout>(R.id.ll_pay_methods)

        // Balance row first, when a balance label is supplied.
        balanceLabel?.let { label ->
            addRow(
                inflater = inflater,
                parent = rows,
                iconRes = R.drawable.ic_pay_wallet,
                tileBgRes = R.drawable.bg_icon_green,
                // Intentional green differentiator for the balance row; not a plain blue accent.
                tintColor = ContextCompat.getColor(requireContext(), R.color.icon_green),
                label = getString(R.string.pay_method_from_balance_fmt, label),
                methodId = ID_BALANCE,
            )
        }

        // Platega methods (СБП, карта…).
        for ((id, label) in methods) {
            val isSbp = id.contains("sbp", ignoreCase = true) || label.contains("СБП", ignoreCase = true)
            addRow(
                inflater = inflater,
                parent = rows,
                iconRes = if (isSbp) R.drawable.ic_pay_sbp else R.drawable.ic_pay_card,
                tileBgRes = R.drawable.bg_icon_blue,
                // Mono-safe blue accent: resolves to grey under the monochrome theme.
                tintColor = resolveThemeColor(R.attr.iconTintBlue),
                label = label,
                methodId = id,
            )
        }

        return root
    }

    private fun addRow(
        inflater: LayoutInflater,
        parent: LinearLayout,
        iconRes: Int,
        tileBgRes: Int,
        tintColor: Int,
        label: String,
        methodId: String,
    ) {
        val row = inflater.inflate(R.layout.item_payment_method, parent, false)
        row.findViewById<TextView>(R.id.tv_pay_label).text = label
        row.findViewById<FrameLayout>(R.id.fl_pay_tile).setBackgroundResource(tileBgRes)
        val icon = row.findViewById<ImageView>(R.id.iv_pay_icon)
        icon.setImageResource(iconRes)
        icon.setColorFilter(tintColor)
        row.setOnClickListener {
            onPicked?.invoke(methodId)
            dismissAllowingStateLoss()
        }
        parent.addView(row)
    }

    /** Resolves a theme colour attr (e.g. [R.attr.iconTintBlue]) to an ARGB int for the current theme. */
    private fun resolveThemeColor(attr: Int): Int {
        val tv = TypedValue()
        requireContext().theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onDestroy() {
        // Drop the callback so it cannot leak the host after the sheet is gone.
        arguments?.getLong(ARG_CALLBACK_KEY, 0L)?.let { key ->
            if (key != 0L && !requireActivity().isChangingConfigurations) {
                callbackHolder.remove(key)
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PaymentMethodSheet"

        /** Id returned by the "С баланса" row. */
        const val ID_BALANCE = "balance"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_BALANCE_LABEL = "arg_balance_label"
        private const val ARG_METHOD_IDS = "arg_method_ids"
        private const val ARG_METHOD_LABELS = "arg_method_labels"
        private const val ARG_CALLBACK_KEY = "arg_callback_key"

        private val keySeq = AtomicLong(1L)
        private val callbackHolder = ConcurrentHashMap<Long, (String) -> Unit>()

        /**
         * Shows a themed bottom sheet titled [title].
         * If [balanceLabel] != null, the FIRST row is "С баланса — <balanceLabel>" and returns id "balance".
         * Each of [methods] (id to label) becomes a row returning its id (these are Platega methods: СБП, карта…).
         * [onPicked] fires with the chosen id, then the sheet dismisses.
         */
        fun show(
            fm: FragmentManager,
            title: String,
            balanceLabel: String?,
            methods: List<Pair<String, String>>,
            onPicked: (methodId: String) -> Unit,
        ) {
            val key = keySeq.getAndIncrement()
            callbackHolder[key] = onPicked

            val sheet = PaymentMethodSheet()
            sheet.onPicked = onPicked
            sheet.arguments = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_BALANCE_LABEL, balanceLabel)
                putStringArrayList(ARG_METHOD_IDS, ArrayList(methods.map { it.first }))
                putStringArrayList(ARG_METHOD_LABELS, ArrayList(methods.map { it.second }))
                putLong(ARG_CALLBACK_KEY, key)
            }
            sheet.show(fm, TAG)
        }
    }
}

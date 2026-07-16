package com.v2ray.ang.ui

import android.os.Bundle
import android.widget.ImageButton
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.Utils

/**
 * «Схемы URL-адресов» — a reference screen listing every supported depv:// deeplink,
 * grouped by action, with a copy-to-clipboard button per row.
 */
class UrlSchemeListActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(
            R.layout.activity_url_scheme_list,
            showHomeAsUp = true,
            title = getString(R.string.url_scheme_list_title)
        )

        bindCopy(R.id.btn_connect, "depv://connect")
        bindCopy(R.id.btn_open, "depv://open")
        bindCopy(R.id.btn_disconnect, "depv://disconnect")
        bindCopy(R.id.btn_close, "depv://close")
        bindCopy(R.id.btn_toggle, "depv://toggle")
        bindCopy(R.id.btn_import, "depv://import/{base64}")
        bindCopy(R.id.btn_add, "depv://add/{url}")
        bindCopy(R.id.btn_routing_add, "depv://routing/add/{base64}")
        bindCopy(R.id.btn_routing_onadd, "depv://routing/onadd/{base64}")
    }

    private fun bindCopy(buttonId: Int, value: String) {
        findViewById<ImageButton>(buttonId)?.setOnClickListener {
            Utils.setClipboard(this, value)
            toast(R.string.url_scheme_copied)
        }
    }
}

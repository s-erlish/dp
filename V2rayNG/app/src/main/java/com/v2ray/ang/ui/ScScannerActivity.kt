package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager

class ScScannerActivity : HelperBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)
        importQRcode()
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                val result = AngConfigManager.importBatchConfig(scanResult, "", false)

                when {
                    result.countSub > 0 -> {
                        val loaded = result.subFetch?.configCount ?: 0
                        if (loaded > 0) {
                            toastSuccess("Серверы добавлены: $loaded")
                        } else {
                            toastError("Не удалось загрузить серверы подписки")
                        }
                    }
                    result.count > 0 -> toastSuccess(getString(R.string.title_import_config_count, result.count))
                    result.subDuplicate -> toast("Подписка уже добавлена")
                    result.subRejected -> toast("Эта ссылка не от departament. Используйте подписку из нашего бота.")
                    else -> toastError(R.string.toast_failure)
                }

                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }
}
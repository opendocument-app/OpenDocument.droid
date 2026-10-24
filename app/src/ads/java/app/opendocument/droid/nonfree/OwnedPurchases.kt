package app.opendocument.droid.nonfree

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Asks play whether the account owns the ad removal that lite sold until 2023: a one-time product,
 * and before 2022 a subscription. Nothing sells it any more, but a buyer who reinstalls or changes
 * device has only this to restore it from.
 */
object OwnedPurchases {

    private val PRODUCT_TYPES =
        listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)

    /** Calls [onOwned] on the main thread if any purchase is owned; says nothing otherwise. */
    fun query(context: Context, crashManager: CrashManager, onOwned: () -> Unit) {
        val mainThread = Handler(Looper.getMainLooper())

        val client =
            BillingClient.newBuilder(context.applicationContext)
                .setListener { _, _ -> }
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                )
                .build()

        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        crashManager.log("billing setup failed: " + result.responseCode)

                        client.endConnection()

                        return
                    }

                    var pending = PRODUCT_TYPES.size
                    var owned = false

                    for (type in PRODUCT_TYPES) {
                        val params = QueryPurchasesParams.newBuilder().setProductType(type).build()

                        client.queryPurchasesAsync(params) { queryResult, purchases ->
                            mainThread.post {
                                if (
                                    queryResult.responseCode != BillingClient.BillingResponseCode.OK
                                ) {
                                    crashManager.log(
                                        "billing query failed: " +
                                            type +
                                            "/" +
                                            queryResult.responseCode
                                    )
                                }

                                owned =
                                    owned ||
                                        purchases.any {
                                            it.purchaseState == Purchase.PurchaseState.PURCHASED
                                        }

                                pending--
                                if (pending == 0) {
                                    client.endConnection()

                                    if (owned) {
                                        onOwned()
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // the next launch asks again
                }
            }
        )
    }
}

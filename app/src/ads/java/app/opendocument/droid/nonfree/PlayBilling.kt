package app.opendocument.droid.nonfree

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Lite's connection to play: what the account owns, and the purchase of the yearly pass. One per
 * activity, and [end] goes with it. Every callback runs on the main thread.
 */
class PlayBilling(
    context: Context,
    private val crashManager: CrashManager,
    private val onOwned: (Entitlement) -> Unit,
) {

    private val mainThread = Handler(Looper.getMainLooper())

    private val client =
        BillingClient.newBuilder(context.applicationContext)
            .setListener { result, _ -> mainThread.post { onPurchasesUpdated(result) } }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()

    private var connected = false

    /** What waits for the connection: each is told whether it came up. */
    private val waiting = ArrayList<(Boolean) -> Unit>()

    /** The pass as [queryPassPrice] found it, which is what [buyPass] needs to start the flow. */
    private var passDetails: ProductDetails? = null
    private var passOfferToken: String? = null

    /**
     * Reports what the account owns through `onOwned`, and acknowledges what is not acknowledged
     * yet: play refunds a purchase that is not within three days. Says nothing if play did not
     * answer, so a launch without a network takes nothing away.
     */
    fun queryOwned() {
        whenConnected { ok ->
            if (!ok) {
                return@whenConnected
            }

            val types = listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)
            val owned = HashMap<String, List<Purchase>>()
            var pending = types.size
            var failed = false

            for (type in types) {
                val params = QueryPurchasesParams.newBuilder().setProductType(type).build()

                client.queryPurchasesAsync(params) { result, purchases ->
                    mainThread.post {
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            owned[type] = purchases.filter {
                                it.purchaseState == Purchase.PurchaseState.PURCHASED
                            }
                        } else {
                            crashManager.log(
                                "billing query failed: " + type + "/" + result.responseCode
                            )

                            failed = true
                        }

                        pending--
                        if (pending == 0 && !failed) {
                            owned.values.flatten().forEach(::acknowledge)

                            onOwned(
                                Entitlement.of(
                                    owned.getValue(BillingClient.ProductType.INAPP).flatMap {
                                        it.products
                                    },
                                    owned.getValue(BillingClient.ProductType.SUBS).flatMap {
                                        it.products
                                    },
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /** Calls [callback] with the yearly price in the local currency, or null if play has none. */
    fun queryPassPrice(callback: (String?) -> Unit) {
        whenConnected { ok ->
            if (!ok) {
                callback(null)

                return@whenConnected
            }

            val product =
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(Entitlement.PASS_PRODUCT)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            val params =
                QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()

            client.queryProductDetailsAsync(params) { result, details ->
                mainThread.post {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        crashManager.log("pass details failed: " + result.responseCode)
                    }

                    val pass = details.productDetailsList.firstOrNull()
                    // the base plan, not a promotion on top of it: that is the one with no offer id
                    val basePlan =
                        pass?.subscriptionOfferDetails?.firstOrNull { it.offerId == null }

                    passDetails = pass
                    passOfferToken = basePlan?.offerToken

                    callback(
                        basePlan?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                    )
                }
            }
        }
    }

    /** Starts play's purchase sheet. Only after [queryPassPrice] found a price. */
    fun buyPass(activity: Activity) {
        val details = passDetails ?: return
        val offerToken = passOfferToken ?: return

        val product =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
        val params =
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()

        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            crashManager.log("pass flow failed: " + result.responseCode)
        }
    }

    fun end() {
        waiting.clear()

        client.endConnection()
    }

    private fun onPurchasesUpdated(result: BillingResult) {
        when (result.responseCode) {
            // asked again rather than read from the update: one answer, whichever way it came
            BillingClient.BillingResponseCode.OK,
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwned()

            BillingClient.BillingResponseCode.USER_CANCELED -> {}

            else -> crashManager.log("purchase failed: " + result.responseCode)
        }
    }

    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) {
            return
        }

        val params =
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()

        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // the next launch tries again, well inside the three days
                crashManager.log("acknowledge failed: " + result.responseCode)
            }
        }
    }

    private fun whenConnected(action: (Boolean) -> Unit) {
        if (connected) {
            action(true)

            return
        }

        waiting.add(action)

        // the first one to wait has already asked for the connection
        if (waiting.size > 1) {
            return
        }

        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    mainThread.post {
                        connected = result.responseCode == BillingClient.BillingResponseCode.OK
                        if (!connected) {
                            crashManager.log("billing setup failed: " + result.responseCode)
                        }

                        val actions = waiting.toList()
                        waiting.clear()

                        actions.forEach { it(connected) }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // the next call connects again
                    mainThread.post { connected = false }
                }
            }
        )
    }
}

package com.getcapacitor.community.stripe.paymentsheet;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.core.util.Supplier;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.getcapacitor.community.stripe.models.Executor;
import com.google.android.gms.common.util.BiConsumer;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.Stripe;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;
import com.stripe.android.paymentsheet.PaymentSheetResultCallback;

public class PaymentSheetExecutor extends Executor {
    public PaymentSheet paymentSheet;
    private final JSObject emptyObject = new JSObject();
    private PaymentSheet.Configuration paymentConfiguration;

    private String paymentIntentClientSecret;

    public PaymentSheetExecutor(
        Supplier<Context> contextSupplier,
        Supplier<Activity> activitySupplier,
        BiConsumer<String, JSObject> notifyListenersFunction,
        String pluginLogTag
    ) {
        super(contextSupplier, activitySupplier, notifyListenersFunction, pluginLogTag, "GooglePayExecutor");
        this.contextSupplier = contextSupplier;
    }

    public void createPaymentSheet(final PluginCall call, String publishableKey) {
        PaymentConfiguration.init(this.contextSupplier.get(), publishableKey);

        paymentIntentClientSecret = call.getString("paymentIntentClientSecret", null);
        String customerEphemeralKeySecret = call.getString("customerEphemeralKeySecret", null);
        String customerId = call.getString("customerId", null);

        if (paymentIntentClientSecret == null || customerId == null) {
            notifyListeners(PaymentSheetEvents.FailedToLoad.getWebEventName(), emptyObject);
            call.reject("invalid Params");
            return;
        }


        String merchantDisplayName = call.getString("merchantDisplayName");

        if (merchantDisplayName == null) {
            merchantDisplayName = "";
        }

        paymentConfiguration = new PaymentSheet.Configuration(
                merchantDisplayName,
                new PaymentSheet.CustomerConfiguration(
                        customerId,
                        customerEphemeralKeySecret
                )
        );

        Boolean useGooglePay = call.getBoolean("useGooglePay", false);

        if (useGooglePay) {
            Boolean GooglePayEnvironment = call.getBoolean("GooglePayIsTesting", false);

            PaymentSheet.GooglePayConfiguration.Environment environment = PaymentSheet.GooglePayConfiguration.Environment.Production;
            if (GooglePayEnvironment) {
                environment = PaymentSheet.GooglePayConfiguration.Environment.Test;
            }

            final PaymentSheet.GooglePayConfiguration googlePayConfiguration =
                    new PaymentSheet.GooglePayConfiguration(
                            environment,
                            call.getString("countryCode", "US")
                    );
            paymentConfiguration.setGooglePay(googlePayConfiguration);
        }

        notifyListeners(PaymentSheetEvents.Loaded.getWebEventName(), emptyObject);
        call.resolve();
    }

    public void presentPaymentSheet(final PluginCall call) {
        try {
            paymentSheet.presentWithPaymentIntent(
                    paymentIntentClientSecret,
                    paymentConfiguration
            );
        } catch (Exception ex) {
            call.reject(ex.getLocalizedMessage(), ex);
        }
    }

    public void onPaymentSheetResult(
            Bridge bridge,
            String callbackId,
            final PaymentSheetResult paymentSheetResult
    ) {
        PluginCall call = bridge.getSavedCall(callbackId);

        if (paymentSheetResult instanceof PaymentSheetResult.Canceled) {
            notifyListeners(PaymentSheetEvents.Canceled.getWebEventName(), emptyObject);
            call.resolve(new JSObject().put("paymentResult", PaymentSheetEvents.Canceled.getWebEventName()));
        } else if (paymentSheetResult instanceof PaymentSheetResult.Failed) {
            notifyListeners(PaymentSheetEvents.Failed.getWebEventName(), emptyObject);
            call.resolve(new JSObject().put("paymentResult", PaymentSheetEvents.Failed.getWebEventName()));
            // ((PaymentSheetResult.Failed) paymentSheetResult).getError()
        } else if (paymentSheetResult instanceof PaymentSheetResult.Completed) {
            notifyListeners(PaymentSheetEvents.Completed.getWebEventName(), emptyObject);
            call.resolve(new JSObject().put("paymentResult", PaymentSheetEvents.Completed.getWebEventName()));
        }
    }
}

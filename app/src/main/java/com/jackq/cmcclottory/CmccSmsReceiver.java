package com.jackq.cmcclottory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

public class CmccSmsReceiver extends BroadcastReceiver {
    static final private String SMS_URI = "android.provider.Telephony.SMS_RECEIVED";
    static final private String TAG = "jackq-SMS_RECEIVER";
    static private SmsMessageHandler smsMessageHandler = null;

    public CmccSmsReceiver(){
        Log.d(TAG, "CmccSmsReceiver Initialize");
    }
    public static void setMessageHandler(SmsMessageHandler messageHandler) {
        Log.d(TAG, "CmccSmsReceiver Set handler");
        smsMessageHandler = messageHandler;
    }

    public interface SmsMessageHandler{
        boolean handler(SmsMessage[] msgs);
    }

    @SuppressWarnings("deprecation")
    public final SmsMessage[] getMessagesFromIntent(Intent intent){
        Object[] messages = (Object[]) intent.getSerializableExtra("pdus");
        byte[][] pduObjs = new byte[messages.length][];
        for (int i = 0; i < messages.length; i++)
        {
            pduObjs[i] = (byte[]) messages[i];
        }
        byte[][] pdus = new byte[pduObjs.length][];
        int pduCount = pdus.length;
        SmsMessage[] msgs = new SmsMessage[pduCount];
        for (int i = 0; i < pduCount; i++) {
            pdus[i] = pduObjs[i];
            msgs[i] = SmsMessage.createFromPdu(pdus[i]);
        }
        return msgs;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "CmccSmsReceiver Got intent");
        // Get the received intent
        if(intent.getAction().equals(SMS_URI)){
            Log.d(TAG,"Valid handler");
            // Get extra data for sms message
            Bundle bundle = intent.getExtras();
            if(bundle != null){
                SmsMessage[] messagesFromIntent;
                if(Build.VERSION.SDK_INT < 19){
                    messagesFromIntent = getMessagesFromIntent(intent)   ;
                }else{
                    messagesFromIntent = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                }
                if(smsMessageHandler!=null){
                    if(smsMessageHandler.handler(messagesFromIntent)){
                        Log.d(TAG,"aborted SMS message");
                        this.abortBroadcast();
                    }
                }
            }
        }

    }
}

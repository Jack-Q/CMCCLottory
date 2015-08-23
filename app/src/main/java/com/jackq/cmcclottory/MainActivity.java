package com.jackq.cmcclottory;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private Activity self = this;

    private enum WorkflowState {
        ST_OFF,
        ST_PAGE_INDEX,
        ST_PAGE_PHONE_NUM,
        ST_PAGE_VALIDATE,
        ST_PAGE_SEND_VALID,
        ST_PAGE_CONFIRM,
        ST_PAGE_RESULT
    }

    private enum CancelWorkflowState {
        ST_SMS_CANCEL_OFF,
        ST_SMS_CANCEL_REQ,
        ST_SMS_CANCEL_CONF
    }

    private WorkflowState workflowState;
    private CancelWorkflowState cancelWorkflowState;
    private String checkCode;
    private String phoneNumber;


    private Button btnStart = null;
    private Button btnSwitch = null;
    private Button btnReset = null;

    private TextView logText = null;

    private WebView webView = null;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Bind UI elements to variables
        btnStart = (Button) findViewById(R.id.buttonStart);
        btnSwitch = (Button) findViewById(R.id.buttonSwitch);
        btnReset = (Button) findViewById(R.id.buttonCancel);

        webView = (WebView) findViewById(R.id.webView);

        logText = (TextView) findViewById(R.id.textView);

        // Web view settings
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true); // Enable JavaScript
        webSettings.setLoadsImagesAutomatically(true); // Disable Image auto-load

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                log("Finish load page");
                switch (workflowState) {
                    case ST_OFF:
                        /* Do nothing */
                        break;
                    case ST_PAGE_INDEX:
                    case ST_PAGE_PHONE_NUM:
                    case ST_PAGE_VALIDATE:
                    case ST_PAGE_SEND_VALID:
                    case ST_PAGE_CONFIRM:
                    case ST_PAGE_RESULT:
                        doWorkflow();
                        break;
                }
                super.onPageFinished(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                log("Opening url: " + url);
                return true;
            }
        });

        // Bind button click event handler
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (workflowState != WorkflowState.ST_OFF) {
                    Toast.makeText(self, "Internal Error!", Toast.LENGTH_LONG).show();
                    return;
                }
                doWorkflow();
            }
        });

        btnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(self).setCancelable(false)
                        .setTitle(R.string.switch_phone_number).setItems(R.array.phone_number_list, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        phoneNumber = getResources().getStringArray(R.array.phone_number_list)[which];
                        log(getString(R.string.selected) + phoneNumber);
                    }
                }).show();
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelWorkflow();
            }
        });

        // Bind SMS receiver events handler
        CmccSmsReceiver.setMessageHandler(new CmccSmsReceiver.SmsMessageHandler() {
            @Override
            public boolean handler(SmsMessage[] msgs) {
                log(getString(R.string.received_messages));
                log(getString(R.string.message_fragments) + msgs.length);
                String originatingAddress = null;
                String fullMessage = "";
                for (SmsMessage msg : msgs) {
                    String messageBody = msg.getMessageBody();
                    originatingAddress = msg.getOriginatingAddress();
                    log(getString(R.string.message_from) + originatingAddress);
                    log(getString(R.string.message_content) + messageBody.substring(0, Math.min(messageBody.length(), 12)).replace('\n', ' '));
                    if ("10086".equals(originatingAddress)) {
                        if (workflowState == WorkflowState.ST_PAGE_VALIDATE || workflowState == WorkflowState.ST_PAGE_SEND_VALID) {
                            log(getString(R.string.try_to_match));
                            Pattern p = Pattern.compile("(\\d{6})");
                            Matcher matcher = p.matcher(messageBody);
                            // Find check code
                            if (matcher.find()) {
                                checkCode = matcher.group();
                                doWorkflow();
                                return true;
                            }
                        }

                    }
                    if ("10086977".equals(originatingAddress)) {
                        fullMessage += messageBody;
                    }
                }
                if ("10086977".equals(originatingAddress)) {
                    if (cancelWorkflowState == CancelWorkflowState.ST_SMS_CANCEL_CONF) {
                        if (fullMessage.contains("和通讯录-会员")) {
                            cancelWorkflow();
                        } else {
                            Toast.makeText(self,getString(R.string.no_require_cancel), Toast.LENGTH_LONG).show();
                            log(getString(R.string.no_require_cancel));
                            cancelWorkflowState = CancelWorkflowState.ST_SMS_CANCEL_OFF;
                            btnReset.setEnabled(true);
                        }
                        return true;
                    }
                }
                return false;
            }
        });

        // initial State
        workflowState = WorkflowState.ST_OFF;
        cancelWorkflowState = CancelWorkflowState.ST_SMS_CANCEL_OFF;
        // Select the phone number at once
        btnSwitch.performClick();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Prompt notification
            new AlertDialog.Builder(self).setTitle(getString(R.string.confirmation)).setPositiveButton(getString(R.string.quit), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    self.finish();
                }
            }).setNegativeButton(getString(R.string.cancel), null).setCancelable(true).setMessage(getString(R.string.sure_to_abort_or_quit)).show();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void log(String log) {
        logText.append(log + "\n");
        ScrollView sv = (ScrollView) logText.getParent();
        sv.scrollTo(0,logText.getMeasuredHeight() - sv.getMaxScrollAmount());
    }

    void doWorkflow() {
        switch (workflowState) {
            // Reset state
            case ST_OFF:
                log(getString(R.string.start_workflow));
                workflowState = WorkflowState.ST_PAGE_INDEX;
                // Disable start button
                btnStart.setEnabled(false);
                btnSwitch.setEnabled(false);
                // Reset the web view state
                webView.clearCache(false);
                webView.clearHistory();
                // Clear check code
                checkCode = null;
                // Begin the workflow
                webView.loadUrl("http://wap.sx.10086.cn/qrcodehall/qrcodedealing/forBusiness?param=f71876e798d84b6ca32216d357c7b260");
                break;
            // Start web page
            case ST_PAGE_INDEX:
                log(">>> 01 - Finish loading index, go to next");
                workflowState = WorkflowState.ST_PAGE_PHONE_NUM;
                webView.loadUrl("javascript:void($('div.j-call-next').trigger('click'))");
                break;
            case ST_PAGE_PHONE_NUM:
                log(">>> 02 - Finish loading, fill phone number, go to next");
                workflowState = WorkflowState.ST_PAGE_VALIDATE;
                webView.loadUrl("javascript:void($('input#mobilePhone').val('"+ phoneNumber+ "'),$('.j-call-next').trigger('click'))");
                break;
            case ST_PAGE_VALIDATE:
                if (checkCode != null) {
                    log(">>> 03 - Successfully get check code");
                } else {
                    log(">>> 03 - Successfully load page");
                }
                workflowState = WorkflowState.ST_PAGE_SEND_VALID;
                break;
            case ST_PAGE_SEND_VALID:
                if (checkCode == null) {
                    log("********************");
                    log("**   FATAL ERROR  **");
                    log("********************");
                    endWorkflow();
                    return;
                }
                log(">>> 04 - Uploading check code");
                webView.loadUrl("javascript:void($('input#vCode').val('" + checkCode + "'),$('.j-call-next').trigger('click'))");
                workflowState = WorkflowState.ST_PAGE_CONFIRM;
                break;
            case ST_PAGE_CONFIRM:
                log(">>> 05 - Uploading check code");
                webView.loadUrl("javascript:void(goBusiness())");
                workflowState = WorkflowState.ST_PAGE_RESULT;
                break;
            case ST_PAGE_RESULT:
                log(">>> 06 - Workflow Pt - 1 Finished");
                endWorkflow();

                new AlertDialog.Builder(self).setTitle(getString(R.string.tip_check)).setPositiveButton(getString(R.string.got_it), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        webView.loadUrl("javascript:void(goLottery())");    // Try to go lottery
                        // If the state of cancellation  is OFF, then start the cancellation workflow
                        if (cancelWorkflowState == CancelWorkflowState.ST_SMS_CANCEL_OFF) {
                            cancelWorkflow();
                        }
                    }
                }).setCancelable(false).setMessage(getString(R.string.finish_reqister)).show();
                break;

        }
    }

    void endWorkflow() {

        log(getString(R.string.end_workflow));
        workflowState = WorkflowState.ST_OFF;
        // Disable start button
        btnStart.setEnabled(true);
        btnSwitch.setEnabled(true);
    }

    void cancelWorkflow() {
        switch (cancelWorkflowState) {
            case ST_SMS_CANCEL_OFF:
                log(getString(R.string.start_cancel_workflow));
                btnReset.setEnabled(false);
                cancelWorkflowState = CancelWorkflowState.ST_SMS_CANCEL_REQ;
                cancelWorkflow();
                break;
            case ST_SMS_CANCEL_REQ:
                log(">>> 01 - "+getString(R.string.prep_to_send_sms));

                SmsManager.getDefault().sendTextMessage("10086", null, "0000", null, null);
                cancelWorkflowState = CancelWorkflowState.ST_SMS_CANCEL_CONF;

                break;
            case ST_SMS_CANCEL_CONF:
                log(">>> 02 - "+getString(R.string.prep_to_send_sms));
                SmsManager.getDefault().sendTextMessage("10086977", null, "1", null, null);
                log(getString(R.string.finish_cancel));
                cancelWorkflowState = CancelWorkflowState.ST_SMS_CANCEL_OFF;
                btnReset.setEnabled(true);
                break;
        }
    }
}

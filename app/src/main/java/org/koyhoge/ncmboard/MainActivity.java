package org.koyhoge.ncmboard;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.nifty.cloud.mb.core.DoneCallback;
import com.nifty.cloud.mb.core.FindCallback;
import com.nifty.cloud.mb.core.LoginCallback;
import com.nifty.cloud.mb.core.NCMB;
import com.nifty.cloud.mb.core.NCMBException;
import com.nifty.cloud.mb.core.NCMBInstallation;
import com.nifty.cloud.mb.core.NCMBObject;
import com.nifty.cloud.mb.core.NCMBObjectService;
import com.nifty.cloud.mb.core.NCMBQuery;
import com.nifty.cloud.mb.core.NCMBUser;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    /**  Request code for floating action button */
    public static final int REQ_CODE_FAB = 1;

    /** Object class name for messages */
    private static final String NCMB_CLASSNAME_MESSAGES = "messages";

    /** Current user object */
    public NCMBUser currentUser = null;

    protected ListView messageView;
    protected List<MessageItem> messages;
    protected MessageItemAdapter messageAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialize NCMB
        String app_key = "xxxxxxxx";
        String client_key = "xxxxxxxx";
        NCMB.initialize(this, app_key, client_key);

        final NCMBInstallation installation = NCMBInstallation.getCurrentInstallation();

        //GCMからRegistrationIdを取得しinstallationに設定する
        String fcm_sender_id ="xxxxxxxx";
        installation.getRegistrationIdInBackground(fcm_sender_id, new DoneCallback() {
            @Override
            public void done(NCMBException e) {
                if (e == null) {
                    installation.saveInBackground(new DoneCallback() {
                        @Override
                        public void done(NCMBException e) {
                            if (e == null) {
                                //保存成功
                            } else if (NCMBException.DUPLICATE_VALUE.equals(e.getCode())) {
                                //保存失敗 : registrationID重複
                                updateInstallation(installation);
                            } else {
                                //保存失敗 : その他
                            }
                        }
                    });
                } else {
                    //ID取得失敗
                }
            }
        });

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        showLoginDialog();

        // setup message container
        messages = new ArrayList<MessageItem>();
        messageAdapter = new MessageItemAdapter(this, 0, messages);
        messageView = (ListView)findViewById(R.id.listView);
        messageView.setAdapter(messageAdapter);

        // load exists messages
        loadMessages();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentUser == null) {
                    Toast.makeText(MainActivity.this,
                            "Please login first",
                            Toast.LENGTH_LONG)
                            .show();
                    showLoginDialog();
                    return;
                }
                int requestCode = REQ_CODE_FAB;
                startActivityForResult(new Intent(
                        MainActivity.this,
                        PostActivity.class
                ), requestCode);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Bundle bundle = data.getExtras();
        switch (requestCode) {
            case REQ_CODE_FAB:
                if (resultCode == RESULT_OK) {
                    String message = bundle.getString("key.StringData");

                    MessageItem item = new MessageItem();
                    item.setMessage(message);
                    item.setUserName(currentUserName());
                    item.setTimestamp(new Date());
                    appendMessage(item);
                } else if (resultCode == RESULT_CANCELED) {
                    // Do nothing
                }
                break;
        }
    }

    /**
     * Update installation data with own device.
     * @param installation NCMBInstallation instance
     */
    public static void updateInstallation(final NCMBInstallation installation) {
        //installationクラスを検索するクエリの作成
        NCMBQuery<NCMBInstallation> query = NCMBInstallation.getQuery();

        //同じRegistration IDをdeviceTokenフィールドに持つ端末情報を検索する
        query.whereEqualTo("deviceToken", installation.getDeviceToken());

        //データストアの検索を実行
        query.findInBackground(new FindCallback<NCMBInstallation>() {
            @Override
            public void done(List<NCMBInstallation> results, NCMBException e) {

                //検索された端末情報のobjectIdを設定
                installation.setObjectId(results.get(0).getObjectId());

                //端末情報を更新する
                installation.saveInBackground();
            }
        });
    }

    /**
     * Append message item to list
     * @param item appended message item
     */
    protected void appendMessage(MessageItem item) {
        final MessageItem tmpItem = item;

        NCMBObject messageObj = new NCMBObject(NCMB_CLASSNAME_MESSAGES);
        messageObj.put("userId", currentUser.getObjectId());
        messageObj.put("userName", currentUser.getUserName());
        messageObj.put("message", item.getMessage());

        messageObj.saveInBackground(new DoneCallback() {
            @Override
            public void done(NCMBException e) {
                if (e != null) {
                    Toast.makeText(MainActivity.this,
                            "Message post failed",
                            Toast.LENGTH_LONG)
                            .show();
                    return;
                }
                // update all messages
                loadMessages();
            }
        });
    }
    /**
     * load all messages already exists
     */
    protected void loadMessages() {
        NCMBQuery<NCMBObject> query = new NCMBQuery<>(NCMB_CLASSNAME_MESSAGES);
        query.findInBackground(new FindCallback<NCMBObject>() {
            @Override
            public void done(List<NCMBObject> list, NCMBException e) {
                if (e != null) {
                    Toast.makeText(MainActivity.this,
                            "Failed loading messages",
                            Toast.LENGTH_LONG)
                            .show();
                } else {
                    List<MessageItem> tmpMessages = new ArrayList<MessageItem>();
                    for (NCMBObject obj : list) {
                        MessageItem item = new MessageItem();
                        item.setUserName(obj.getString("userName"));
                        item.setUserId(obj.getString("userId"));
                        item.setMessage(obj.getString("message"));
                        item.setTimestamp(obj.getUpdateDate());

                        tmpMessages.add(item);
                    }

                    // update messages
                    messages.clear();
                    messages.addAll(tmpMessages);
                    messageAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    /**
     * Get current user name as string
     * @return user name
     */
    public String currentUserName() {
        if (currentUser == null) {
            return "";
        }
        return currentUser.getUserName();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Show login dialog
     */
    public void showLoginDialog() {
        LayoutInflater li = LayoutInflater.from(this);
        View prompt = li.inflate(R.layout.login_dialog, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(prompt);
        final EditText user = (EditText) prompt.findViewById(R.id.login_name);
        final EditText pass = (EditText) prompt.findViewById(R.id.login_password);

//        user.setText("testuser1");
//        pass.setText("testhogehoge");

        alertDialogBuilder.setTitle("NcmBoard LOGIN");
        alertDialogBuilder.setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id)
                    {
                        String password = pass.getText().toString();
                        String username = user.getText().toString();
                        try {
                            if (username.length() < 2 || password.length() < 2) {
                                Toast.makeText(MainActivity.this,
                                        "Invalid username or password",
                                        Toast.LENGTH_LONG)
                                        .show();
                                showLoginDialog();
                            } else {
                                NCMBUser.loginInBackground(username, password, new LoginCallback() {
                                    @Override
                                    public void done(NCMBUser ncmbUser, NCMBException e) {
                                        if (e != null) {
                                            // Login failed
                                            Toast.makeText(MainActivity.this,
                                                    "Invalid username or passwrd",
                                                    Toast.LENGTH_LONG)
                                                    .show();
                                            showLoginDialog();
                                        } else {
                                            // Login successed
                                            currentUser = ncmbUser;
                                        }
                                    }
                                });
                            }
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this,e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });

        alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id)
            {
                dialog.cancel();
            }
        });

        alertDialogBuilder.show();
    }
}

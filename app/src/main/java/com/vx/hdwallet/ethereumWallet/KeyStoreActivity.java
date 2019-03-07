package com.vx.hdwallet.ethereumWallet;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.vx.hdwallet.R;

public class KeyStoreActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_store);
        TextView keystore = findViewById(R.id.keystore);
        keystore.setText(getIntent().getStringExtra("keystore"));
    }
}

package com.vx.hdwallet.ethereumWallet;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.widget.TextView;

import com.vx.hdwallet.R;

public class PrivateKeyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private_key);
        TextView privateKey = findViewById(R.id.private_key);
        privateKey.setText(getIntent().getStringExtra("pk"));
    }
}

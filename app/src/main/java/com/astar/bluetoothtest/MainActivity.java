package com.astar.bluetoothtest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.astar.bluetoothtest.connection.Bluetooth;

import java.nio.charset.Charset;

public class MainActivity extends AppCompatActivity {

    private TextView mTextViewMessages;
    private EditText mEditTextMessage;
    private Button mButtonSend;

    private Bluetooth mBluetooth;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextViewMessages = findViewById(R.id.textViewMessages);
        mEditTextMessage = findViewById(R.id.editTextMessage);
        mButtonSend = findViewById(R.id.buttonSend);

        mHandler = createHandler();
        mBluetooth = Bluetooth.getInstance(this, mHandler);
        mBluetooth.enable();
        mBluetooth.start();

        mButtonSend.setOnClickListener((view) -> {
            String message = mEditTextMessage.getText().toString();
            Toast.makeText(this, "Сообщение: " + message, Toast.LENGTH_SHORT).show();
            mBluetooth.write(message.getBytes());
        });
    }

    @SuppressLint("HandlerLeak")
    private Handler createHandler() {
        return mHandler = new Handler() {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case Bluetooth.MESSAGE_STATE_CHANGED:
                        int state = msg.arg1;
                        switch (state) {
                            case Bluetooth.STATE_NONE:
                                mTextViewMessages.setText("Нет соединения...");
                                break;
                            case Bluetooth.STATE_LISTEN:
                                mTextViewMessages.setText("Слушаем подключения...");
                                break;
                            case Bluetooth.STATE_CONNECTING:
                                mTextViewMessages.setText("Подключаемся...");
                                break;
                            case Bluetooth.STATE_CONNECTED:
                                mTextViewMessages.setText("Подключено!");
                                break;
                        }
                        break;
                    case Bluetooth.MESSAGE_READ:
                        String s = new String((byte[]) msg.obj, Charset.defaultCharset());
                        mTextViewMessages.setText(s);
                        break;
                    case Bluetooth.MESSAGE_WRITE:
                        // TODO: 01.08.2019
                        break;
                }
            }
        };
    }
}
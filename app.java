package com.example.smart_switch;

import android.app.Dialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private int timeSelected = 0;
    private CountDownTimer timeCountDown = null;
    private boolean isTimerRunning = false;
    private static final String ESP32_IP = "http://192.168.1.114"; // Replace with your ESP32 IP address

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startBtn = findViewById(R.id.start_btn);
        startBtn.setOnClickListener(v -> setTimeFunction());

        Button stopBtn = findViewById(R.id.stop_btn);
        stopBtn.setOnClickListener(v -> {
            if (isTimerRunning) {
                pauseTimer();
            } else {
                resetTimer();
            }
        });

        fetchDataFromESP32();
    }

    private void resetTimer() {
        if (timeCountDown != null) {
            timeCountDown.cancel();
            timeCountDown = null;
            isTimerRunning = false;

            ProgressBar progressBar = findViewById(R.id.timer);
            progressBar.setProgress(0);

            TextView timeLeft = findViewById(R.id.count);
            timeLeft.setText("0:00");

            // Send command to ESP32 indicating timer is stopped (false)
            sendStatusToESP32('0');
        }
    }

    private void startTimer() {
        ProgressBar progressBar = findViewById(R.id.timer);
        progressBar.setMax(timeSelected);

        timeCountDown = new CountDownTimer(timeSelected * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                int minutes = secondsLeft / 60;
                int seconds = secondsLeft % 60;

                TextView timeLeft = findViewById(R.id.count);
                timeLeft.setText(String.format("%d:%02d", minutes, seconds));

                progressBar.setProgress(timeSelected - secondsLeft);
            }

            @Override
            public void onFinish() {
                resetTimer();
                Toast.makeText(MainActivity.this, "Times Up!", Toast.LENGTH_SHORT).show();
            }
        };

        timeCountDown.start();
        isTimerRunning = true;

        // Send command to ESP32 indicating timer is running (true)
        sendStatusToESP32('1');
    }

    private void pauseTimer() {
        if (timeCountDown != null) {
            timeCountDown.cancel();
            isTimerRunning = false;

            // Send command to ESP32 indicating timer is paused
            sendStatusToESP32('0');
        }
    }

    private void setTimeFunction() {
        Dialog timeDialog = new Dialog(this);
        timeDialog.setContentView(R.layout.dialog_box);
        EditText timeSet = timeDialog.findViewById(R.id.duration);

        timeDialog.findViewById(R.id.dialog_btn).setOnClickListener(v -> {
            if (timeSet.getText().toString().isEmpty()) {
                Toast.makeText(this, "Enter Time Duration", Toast.LENGTH_SHORT).show();
            } else {
                resetTimer();
                int minutes = Integer.parseInt(timeSet.getText().toString());
                timeSelected = minutes * 60;

                TextView timeLeft = findViewById(R.id.count);
                timeLeft.setText(String.format("%d:00", minutes));

                ProgressBar progressBar = findViewById(R.id.timer);
                progressBar.setMax(timeSelected);

                startTimer(); // Automatically start timer after setting time
            }
            timeDialog.dismiss();
        });

        timeDialog.show();
    }

    private void sendStatusToESP32(char status) {
        new Thread(() -> {
            try {
                URL url = new URL(ESP32_IP + "/status?value=" + status);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                int responseCode = urlConnection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();
                }
                urlConnection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void fetchDataFromESP32() {
        new FetchDataTask().execute(ESP32_IP + "/data");
    }

    private class FetchDataTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {
            StringBuilder result = new StringBuilder();
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    result.append(inputLine);
                }
                in.close();
                urlConnection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            String[] parts = result.split("&");
            String voltage = parts[0].split("=")[1];
            String current = parts[1].split("=")[1];

            TextView voltageText = findViewById(R.id.voltage_value);
            TextView currentText = findViewById(R.id.current_value);

            voltageText.setText(voltage);
            currentText.setText(current);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeCountDown != null) {
            timeCountDown.cancel();
        }
    }
}
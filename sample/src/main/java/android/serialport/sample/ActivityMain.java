/*
 * Copyright 2009 Cedric Priscal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.serialport.sample;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.google.common.io.BaseEncoding;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ActivityMain extends Activity {
    private static final String TAG = ActivityMain.class.getName();
    private Spinner spinnerSerialPort;
    private TtyAdapter<TtyFile> adapter;
    private TextView textViewReceive, textViewReceiveCount, textViewReceiveCountMatch, textViewReceiveMessage;
    private EditText editTextDelay, editTextSendTimes, editTextSendMsg;
    private Switch switchOpenClose;
    private Switch switchSend;
    private CheckBox checkboxHex, checkboxReceiveHex, checkboxFrameBySend;

    private ImageButton imageButtonClear;
    private SerialSample serialSample;
    private Thread writeThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.textViewReceive = this.findViewById(R.id.textViewReceive);
        this.textViewReceiveCount = this.findViewById(R.id.textViewReceiveCount);
        this.textViewReceiveCountMatch = this.findViewById(R.id.textViewReceiveCountMatch);
        this.textViewReceiveMessage = this.findViewById(R.id.textViewReceiveMessage);
        this.imageButtonClear = this.findViewById(R.id.imageButtonClear);
        this.editTextDelay = this.findViewById(R.id.editTextDelay);
        this.editTextSendTimes = this.findViewById(R.id.editTextSendTimes);
        this.spinnerSerialPort = this.findViewById(R.id.spinnerSerialPort);
        this.adapter = new TtyAdapter<>();
        this.spinnerSerialPort.setAdapter(adapter);
        this.editTextSendMsg = this.findViewById(R.id.editTextSendMsg);
        this.checkboxHex = this.findViewById(R.id.checkboxHex);
        this.checkboxReceiveHex = this.findViewById(R.id.checkboxReceiveHex);
        this.checkboxFrameBySend = this.findViewById(R.id.checkboxFrameBySend);
        this.switchOpenClose = this.findViewById(R.id.switchOpenClose);
        this.switchSend = this.findViewById(R.id.switchSend);

        ((Switch) this.findViewById(R.id.switchOpenClose)).setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                resetBufferedReceive();
                Spinner pathSpinner = (Spinner) findViewById(R.id.spinnerSerialPort);
                String path = ((TextView) pathSpinner.getSelectedView()).getText().toString();
                String br = ((EditText) findViewById(R.id.editBaudRate)).getText().toString();
                Log.d(TAG, "准备打开串口：" + path + ":" + br);
                try {
                    if (serialSample == null) serialSample = new SerialSample();
                    serialSample.open(new File(path), Integer.parseInt(br), (code, obj) -> {
                        if (obj instanceof byte[]) countReadBytes((byte[]) obj);
                    });

                } catch (Exception e) {
                    mHandler.obtainMessage(1, "串口打开失败：" + e.getMessage()).sendToTarget();
                    if (serialSample != null) serialSample.close();
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(3), 400);
                    return;
                }

            } else {
                if (serialSample != null) serialSample.close();
            }
        });
        ((Switch) this.findViewById(R.id.switchSend)).setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                if (serialSample == null || !serialSample.isReady()) {
                    mHandler.obtainMessage(1, "串口未就绪，请先打开串口");
                    return;
                }
                final int sendTimes = Integer.parseInt(editTextSendTimes.getText().toString());
                final int delay = Integer.parseInt(editTextDelay.getText().toString());
                byte[] rb = getWriteBytes();
                Log.d(TAG, "开始发送数据，次数：" + sendTimes + " 间隔：" + delay + " 字节：" + BaseEncoding.base16().encode(rb));
                writeThread = new Thread(() -> {
                    boolean writeOk = true;
                    for (int i = 0; i < sendTimes; i++) {
                        if (i > 0 && delay > 0) {
                            try {
                                Thread.sleep(delay);
                            } catch (InterruptedException e) {
                                writeResp.callback(2, String.valueOf(i));
                                break;
                            }
                        }
                        try {
                            serialSample.write(rb);
                        } catch (IOException e) {
                            writeOk = false;
                            writeResp.callback(1, "写入失败：" + e.getMessage());
                            break;
                        }
                    }
                    if (writeOk) writeResp.callback(0, "写入完成");
                });
                writeThread.start();
            } else {

            }
        });
        this.imageButtonClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.obtainMessage(7).sendToTarget();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.listSerialPort();
    }

    private byte[] sendBytes = null;
    private int totalReceiveCount = 0, totalMatch = 0;
    private int matchIndex = -1;

    private void resetBufferedReceive() {
        this.sendBytes = null;
        this.totalReceiveCount = 0;
        this.matchIndex = -1;
        this.totalMatch = 0;
    }

    private void countReadBytes(byte[] readBs) {
        String receiveTxt = null;
        if (checkboxFrameBySend.isChecked()) {
            if (this.sendBytes == null) parseSendBytes();
            int lineSize = this.sendBytes.length;
            for (byte b : readBs) {
                if (matchIndex == -1) {
                    if (b == sendBytes[0]) matchIndex = 1;
                } else if (b == sendBytes[matchIndex]) {
                    matchIndex++;
                    if (matchIndex == lineSize) {
                        Log.d(TAG, "完帧");
                        totalMatch++;
                        receiveTxt = BaseEncoding.base16().encode(sendBytes);
                        matchIndex = -1;
                    }
                } else {
                    matchIndex = -1;
                }
            }
        } else {
            if (checkboxReceiveHex.isChecked()) receiveTxt = BaseEncoding.base16().encode(readBs);
            else receiveTxt = new String(readBs);
        }
        if (receiveTxt != null) {
            //Log.d(TAG, "收到：" + receiveTxt);
            this.mHandler.obtainMessage(5, receiveTxt).sendToTarget();
        }
        totalReceiveCount += readBs.length;
        this.mHandler.obtainMessage(6, totalReceiveCount, totalMatch, null).sendToTarget();
    }

    private void parseSendBytes() {
        if (checkboxHex.isChecked())
            this.sendBytes = BaseEncoding.base16().decode(editTextSendMsg.getText().toString());
        else this.sendBytes = editTextSendMsg.getText().toString().getBytes();
        Log.d(TAG, "发送的字节为：" + BaseEncoding.base16().encode(this.sendBytes));
    }

    private final INormalResponse writeResp = (code, message) -> {
        Log.d(TAG, code + "=============" + message);
        this.mHandler.obtainMessage(4, null).sendToTarget();

    };

    private byte[] getWriteBytes() {
        String input = editTextSendMsg.getText().toString();
        if (checkboxHex.isChecked()) {
            return BaseEncoding.base16().decode(input);
        } else return input.getBytes(StandardCharsets.UTF_8);
    }

    private void listSerialPort() {
        new Thread(() -> {
            File devFile = new File("/dev/");
            if (!devFile.exists() || !devFile.isDirectory()) {
                mHandler.obtainMessage(1, "无法访问设备串口目录").sendToTarget();
                return;
            }
            File[] ttyFiles = devFile.listFiles();
            if (ttyFiles == null || ttyFiles.length == 0) {
                mHandler.obtainMessage(1, "设备串口为空").sendToTarget();
                return;
            }
            ArrayList<TtyFile> ttyFileArrayList = new ArrayList<>();
            for (File tty : ttyFiles) {
                if (!tty.isDirectory() && tty.canRead()) {
                    String name = tty.getName();
                    if (name.startsWith("tty") && name.length() > 3) {
                        Log.d(TAG, "----------------" + tty.getName() + " isFile=" + tty.isFile() + " r/w=" + tty.canRead() + "/" + tty.canWrite());
                        TtyFile ttyFile = new TtyFile("/dev/" + name, tty.canWrite());
                        ttyFileArrayList.add(ttyFile);
                    }
                }
            }
            if (ttyFileArrayList.size() > 0) mHandler.obtainMessage(2, ttyFileArrayList).sendToTarget();
        }).start();
    }

    private void showTtyList(ArrayList<TtyFile> ttyFileArrayList) {
        adapter.setItems(ttyFileArrayList);
        adapter.notifyDataSetChanged();
    }

    private static class TtyFile {
        private String name;
        private boolean canWrite;

        public String getName() {
            return name;
        }

        public boolean isCanWrite() {
            return canWrite;
        }

        public TtyFile(String name, boolean canWrite) {
            this.name = name;
            this.canWrite = canWrite;
        }
    }

    private static class TtyAdapter<T> extends BaseAdapter {
        private ArrayList<TtyFile> ttyFileList;

        @Override
        public int getCount() {
            if (ttyFileList == null) return 0;
            return ttyFileList.size();
        }

        @Override
        public Object getItem(int i) {
            if (ttyFileList == null) return null;
            return ttyFileList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            LayoutInflater _LayoutInflater = LayoutInflater.from(viewGroup.getContext());
            view = _LayoutInflater.inflate(R.layout.layout_spinner_tty, null);
            if (view != null) {
                TextView textView = (TextView) view.findViewById(R.id.textViewLineName);
                textView.setText(((TtyFile) ttyFileList.get(i)).getName());
            }
            return view;
        }

        public void setItems(ArrayList<TtyFile> ttyFileArrayList) {
            this.ttyFileList = ttyFileArrayList;
        }
    }


    private final Handler mHandler = new Handler(message -> {
        switch (message.what) {
            case 0:
                Log.d(TAG, "提示：" + message.obj);
                break;
            case 1:
                Log.e(TAG, "错误：" + message.obj);
                break;
            case 2:
                ArrayList<TtyFile> ttyFileArrayList = (ArrayList<TtyFile>) message.obj;
                showTtyList(ttyFileArrayList);
                break;
            case 3:
                switchOpenClose.setChecked(false);
                break;
            case 4:
                switchSend.setChecked(false);
                break;
            case 5:
                if (textViewReceiveMessage.getLineCount() > 10) textViewReceiveMessage.setText("");
                else if (textViewReceiveMessage.getLineCount() != 0) textViewReceiveMessage.append("\n");
                textViewReceiveMessage.append(String.valueOf(message.obj));
                break;
            case 6:
                textViewReceiveCount.setText(String.valueOf(message.arg1));
                textViewReceiveCountMatch.setText(String.valueOf(message.arg2));
                break;
            case 7:
                textViewReceiveMessage.setText("");
                break;
            default:
                break;
        }
        return false;
    });
}

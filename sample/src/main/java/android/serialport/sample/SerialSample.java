package android.serialport.sample;

import android.serialport.SerialPort;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class SerialSample {
    private static final String TAG = SerialSample.class.getName();
    private SerialPort serialPort;
    private boolean ready;
    private Thread threadRead;

    public void open(File pathFile, int rate, INormalResponse response) throws SecurityException, IOException {
        this.close();
        this.serialPort = new SerialPort(pathFile, rate);
        if (!pathFile.canRead()) {
            Log.w(TAG, "串口不可读，尝试修改：" + pathFile.getAbsolutePath());
            this.serialPort.makeAccessible(true, true);
        }
        this.serialPort.open();
        if (!pathFile.canRead()) {
            Log.w(TAG, "串口不可读，打开串口失败：" + pathFile.getAbsolutePath());
            this.serialPort.close();
            throw new SecurityException(pathFile.getAbsolutePath() + " cannot read");
        }
        ready = true;
        threadRead = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int canRead = serialPort.getInputStream().available();
                    if (canRead > 0) {
                        byte[] readBs = new byte[canRead];
                        int readC = serialPort.getInputStream().read(readBs);
                        if (response != null && readC == canRead) response.callback(0, readBs);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        threadRead.start();
    }

    public boolean isReady() {
        return this.ready;
    }

    public void write(byte[] bytes) throws IOException {
        this.serialPort.getOutputStream().write(bytes);
        this.serialPort.getOutputStream().flush();
    }

    public void close() {
        if (threadRead != null) threadRead.interrupt();
        threadRead = null;
        try {
            this.serialPort.getInputStream().close();
        } catch (Exception e) {
        }
        try {
            this.serialPort.getOutputStream().close();
        } catch (Exception e) {
        }
        if (this.serialPort != null) this.serialPort.close();
        this.serialPort = null;
        this.ready = false;
    }
}

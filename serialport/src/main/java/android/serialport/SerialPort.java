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

package android.serialport;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class SerialPort {

    private static final String TAG = "SerialPort";

    private static final String DEFAULT_SU_PATH = "/system/bin/su";
    private final File device;
    private final int baudRate;
    private final int dataBits;
    private final int parity;
    private final int stopBits;
    private final int flags;

    /*
     * Do not remove or rename the field mFd: it is used by native method close();
     */
    private FileDescriptor mFd;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;

    /**
     * 串口
     *
     * @param device   串口设备文件
     * @param baudRate 波特率
     * @param dataBits 数据位；默认8,可选值为5~8
     * @param parity   奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
     * @param stopBits 停止位；默认1；1:1位停止位；2:2位停止位
     * @param flags    默认0
     */
    public SerialPort(@NonNull File device, int baudRate, int dataBits, int parity, int stopBits,
                      int flags) throws SecurityException {
        this.device = device;
        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.parity = parity;
        this.stopBits = stopBits;
        this.flags = flags;
    }

    /**
     * 串口，默认的8n1
     *
     * @param device   串口设备文件
     * @param baudRate 波特率
     */
    public SerialPort(@NonNull File device, int baudRate) throws SecurityException {
        this(device, baudRate, 8, 0, 1, 0);
    }

    /**
     * 串口
     *
     * @param device   串口设备文件
     * @param baudRate 波特率
     * @param dataBits 数据位；默认8,可选值为5~8
     * @param parity   奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
     * @param stopBits 停止位；默认1；1:1位停止位；2:2位停止位
     */
    public SerialPort(@NonNull File device, int baudRate, int dataBits, int parity, int stopBits)
            throws SecurityException {
        this(device, baudRate, dataBits, parity, stopBits, 0);
    }

    public void makeAccessible(boolean readable, boolean writeable) {
        this.makeAccessible(device.getAbsolutePath(), readable, writeable, null);
    }

    public void makeAccessible(String devPath, boolean readable, boolean writeable, String suPath) {
        String cmd = null;
        if (writeable && !device.canWrite()) {
            cmd = "chmod 666 " + devPath + "\n" + "exit\n";
        } else if (readable && !device.canRead()) {
            cmd = "chmod 664 " + devPath + "\n" + "exit\n";
        }
        if (cmd == null) return;
        String ePath = suPath;
        if (suPath == null) ePath = DEFAULT_SU_PATH;
        try {
            /* Missing read/write permission, trying to chmod the file */
            Process su;
            su = Runtime.getRuntime().exec(ePath);
            su.getOutputStream().write(cmd.getBytes());
            if ((su.waitFor() != 0) || !device.canRead() || !device.canWrite()) {
                throw new SecurityException();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new SecurityException();
        }
    }

    public void open() throws IOException {
        mFd = open(device.getAbsolutePath(), baudRate, dataBits, parity, stopBits, flags);
        if (mFd == null) {
            Log.e(TAG, "native open returns null");
            throw new IOException();
        }
        mFileInputStream = new FileInputStream(mFd);
        mFileOutputStream = new FileOutputStream(mFd);
    }

    // Getters and setters
    @NonNull
    public InputStream getInputStream() {
        return mFileInputStream;
    }

    @NonNull
    public OutputStream getOutputStream() {
        return mFileOutputStream;
    }

    /**
     * 串口设备文件
     */
    @NonNull
    public File getDevice() {
        return device;
    }

    /**
     * 波特率
     */
    public int getBaudRate() {
        return baudRate;
    }

    /**
     * 数据位；默认8,可选值为5~8
     */
    public int getDataBits() {
        return dataBits;
    }

    /**
     * 奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
     */
    public int getParity() {
        return parity;
    }

    /**
     * 停止位；默认1；1:1位停止位；2:2位停止位
     */
    public int getStopBits() {
        return stopBits;
    }

    public int getFlags() {
        return flags;
    }

    // JNI
    private native FileDescriptor open(String absolutePath, int baudRate, int dataBits, int parity, int stopBits, int flags);

    public native void close();

    /**
     * 关闭流和串口，已经try-catch
     */
    public void tryClose() {
        try {
            mFileInputStream.close();
        } catch (IOException e) {
            //e.printStackTrace();
        }

        try {
            mFileOutputStream.close();
        } catch (IOException e) {
            //e.printStackTrace();
        }

        try {
            close();
        } catch (Exception e) {
            //e.printStackTrace();
        }
    }

    static {
        System.loadLibrary("serial_port");
    }
}

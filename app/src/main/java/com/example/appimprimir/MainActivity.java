package com.example.appimprimir;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Set;
import java.util.UUID;
import android.os.Handler;

public class MainActivity extends AppCompatActivity {

    private TextView txtEstado;
    private EditText editTextaImprimir;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothDevice bluetoothDevice;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread thread;
    private byte[] readBuffer;
    private int readBufferPosition;
    volatile boolean stopWorker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        inicializarControles();
    }

    private void inicializarControles() {
        txtEstado = findViewById(R.id.textEstatus);
        editTextaImprimir = findViewById(R.id.editText);
    }

    private void findBluetoothDevice(){
        try{
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if(bluetoothAdapter == null){
                txtEstado.setText("No Bluetooth Adapter Found");
            }
            if(bluetoothAdapter.isEnabled()){
                Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBT,0);
            }
            Set<BluetoothDevice> pairedDevice = bluetoothAdapter.getBondedDevices();
            if(pairedDevice.size()>0){
                for(BluetoothDevice pairedDev:pairedDevice){
                    //here we have to look for our device name
                    if(pairedDev.getName().equals("InnerPrinter")){
                        bluetoothDevice = pairedDev;
                        txtEstado.setText("Bluetooth Printer Attached: "+bluetoothDevice.getName());
                        break;
                    }
                }
            }
            txtEstado.setText("Bluetooth Printer Attached");

        }catch (Exception ex){
            ex.printStackTrace();
        }
    }
    private void openBluetoothPrinter() throws IOException{
        try {
            //Standard uuid from string
            UUID uuidString = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
            bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuidString);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            beginListenData();

        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void beginListenData() {
        try {
            final Handler handler = new Handler();
            final byte delimiter=10;
            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!Thread.currentThread().isInterrupted() && !stopWorker){
                        try {
                            int byteAvailable = inputStream.available();
                            if(byteAvailable>0){
                                byte[] packetByte = new byte[byteAvailable];
                                inputStream.read(packetByte);
                                for(int i=0;i<byteAvailable;i++){
                                    byte b = packetByte[i];
                                    if(b==delimiter){
                                        byte[] encodedByte = new byte[readBufferPosition];
                                        System.arraycopy(
                                                readBuffer,
                                                0,
                                                encodedByte,
                                                0,
                                                encodedByte.length
                                        );
                                        final String data = new String(encodedByte,"US-ASCII");
                                        readBufferPosition = 0;
                                        handler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                txtEstado.setText(data);
                                            }
                                        });
                                    }else{
                                        readBuffer[readBufferPosition++]=b;
                                    }
                                }
                            }
                        }catch (Exception ex){
                            stopWorker=true;
                        }
                    }
                }
            });
            thread.start();
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }
    private void printData() throws IOException{
        try{
            String msg = editTextaImprimir.getText().toString() + "\n";
            outputStream.write("Hola a todos".getBytes());
            outputStream.write("\n".getBytes());
            outputStream.write(msg.getBytes());
            txtEstado.setText("Printing text ...");
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }
    private void disconnectBT() throws IOException{
        try {
            stopWorker = true;
            outputStream.close();
            inputStream.close();
            bluetoothSocket.close();
            txtEstado.setText("Printer Disconnected.");
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    public void conectar(View view) {
        try {
            findBluetoothDevice();
            openBluetoothPrinter();
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    public void desconectar(View view) {
        try {
            disconnectBT();
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    public void imprimir(View view) {
        try {
            //printData();

            outputStream = bluetoothSocket.getOutputStream();
            byte[] printformat = new byte[]{0x1B,0x21,0x03};
            outputStream.write(printformat);


            printCustom("Fair Group BD",2,1);
            printCustom("Pepperoni Foods Ltd.",0,1);
            printPhoto(R.mipmap.test);
            printCustom("H-123, R-123, Dhanmondi, Dhaka-1212",0,1);
            printCustom("Hot Line: +88000 000000",0,1);
            printCustom("Vat Reg : 0000000000,Mushak : 11",0,1);
            String dateTime[] = getDateTime();
            printText(leftRightAlign(dateTime[0], dateTime[1]));
            printText(leftRightAlign("Qty: Name" , "Price "));
            printCustom(new String(new char[32]).replace("\0", "."),0,1);
            printText(leftRightAlign("Total" , "2,0000/="));
            printNewLine();
            printCustom("Thank you for coming & we look",0,1);
            printCustom("forward to serve you again",0,1);
            printNewLine();
            printNewLine();
            printNewLine();

            outputStream.flush();
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

///////////**//////////
//print custom
private void printCustom(String msg, int size, int align) {
    //Print config "mode"
    byte[] cc = new byte[]{0x1B,0x21,0x03};  // 0- normal size text
    //byte[] cc1 = new byte[]{0x1B,0x21,0x00};  // 0- normal size text
    byte[] bb = new byte[]{0x1B,0x21,0x08};  // 1- only bold text
    byte[] bb2 = new byte[]{0x1B,0x21,0x20}; // 2- bold with medium text
    byte[] bb3 = new byte[]{0x1B,0x21,0x10}; // 3- bold with large text
    try {
        switch (size){
            case 0:
                outputStream.write(cc);
                break;
            case 1:
                outputStream.write(bb);
                break;
            case 2:
                outputStream.write(bb2);
                break;
            case 3:
                outputStream.write(bb3);
                break;
        }

        switch (align){
            case 0:
                //left align
                outputStream.write(PrinterCommands.ESC_ALIGN_LEFT);
                break;
            case 1:
                //center align
                outputStream.write(PrinterCommands.ESC_ALIGN_CENTER);
                break;
            case 2:
                //right align
                outputStream.write(PrinterCommands.ESC_ALIGN_RIGHT);
                break;
        }
        outputStream.write(msg.getBytes());
        outputStream.write(PrinterCommands.LF);
        //outputStream.write(cc);
        //printNewLine();
    } catch (IOException e) {
        e.printStackTrace();
    }

}

    //print photo
    public void printPhoto(int img) {
        try {
            Bitmap bmp = BitmapFactory.decodeResource(getResources(),
                    img);
            if(bmp!=null){
                byte[] command = Utils.decodeBitmap(bmp);
                outputStream.write(PrinterCommands.ESC_ALIGN_CENTER);
                printText(command);
            }else{
                Log.e("Print Photo error", "the file isn't exists");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("PrintTools", "the file isn't exists");
        }
    }

    //print unicode
    public void printUnicode(){
        try {
            outputStream.write(PrinterCommands.ESC_ALIGN_CENTER);
            printText(Utils.UNICODE_TEXT);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //print new line
    private void printNewLine() {
        try {
            outputStream.write(PrinterCommands.FEED_LINE);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public  void resetPrint() {
        try{
            outputStream.write(PrinterCommands.ESC_FONT_COLOR_DEFAULT);
            outputStream.write(PrinterCommands.FS_FONT_ALIGN);
            outputStream.write(PrinterCommands.ESC_ALIGN_LEFT);
            outputStream.write(PrinterCommands.ESC_CANCEL_BOLD);
            outputStream.write(PrinterCommands.LF);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //print text
    private void printText(String msg) {
        try {
            // Print normal text
            outputStream.write(msg.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //print byte[]
    private void printText(byte[] msg) {
        try {
            // Print normal text
            outputStream.write(msg);
            printNewLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String[] getDateTime() {
        final Calendar c = Calendar.getInstance();
        String dateTime [] = new String[2];
        dateTime[0] = c.get(Calendar.DAY_OF_MONTH) +"/"+ c.get(Calendar.MONTH) +"/"+ c.get(Calendar.YEAR);
        dateTime[1] = c.get(Calendar.HOUR_OF_DAY) +":"+ c.get(Calendar.MINUTE);
        return dateTime;
    }
    private String leftRightAlign(String str1, String str2) {
        String ans = str1 +str2;
        if(ans.length() <31){
            int n = (31 - str1.length() + str2.length());
            ans = str1 + new String(new char[n]).replace("\0", " ") + str2;
        }
        return ans;
    }



}

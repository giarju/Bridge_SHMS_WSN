/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tugas_akhir.sink;

import com.fazecast.jSerialComm.SerialPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 *
 * @author toni
 */
public class Xbee {
     public static void main(String[] args) throws IOException {
        
        SerialPort port = SerialPort.getCommPort("/dev/ttyUSB0");
        port.setComPortParameters(
            115200, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY
        );
        port.openPort();
        
        OutputStream output = port.getOutputStream();
        InputStream input = port.getInputStream();
        byte[] data = "+++".getBytes();
        
        if(port.isOpen()){
            try{
                output.write(data);

                while(input.available() == 0)
                    Thread.sleep(100);

                byte[] readBuffer = new byte[input.available()];
                input.read(readBuffer);
                System.out.println(new String(readBuffer));

            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
            }
            port.closePort();
        }else{
            System.out.println("not work");
        }
    }
}

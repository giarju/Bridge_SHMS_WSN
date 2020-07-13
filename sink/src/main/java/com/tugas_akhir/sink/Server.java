/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tugas_akhir.sink;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author toni
 */
public class Server{
   private final ServerSocket serverSocket;
   private final Socket server;
   private final DataOutputStream out;
   
   public Server(int port) throws IOException {
      serverSocket = new ServerSocket(port);
      server = serverSocket.accept();
      out = new DataOutputStream(server.getOutputStream());
   }
   
   public void write(int data) throws IOException{
       out.writeInt(data);
   }
}
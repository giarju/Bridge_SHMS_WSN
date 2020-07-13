package com.tugas_akhir.sink;

import mraa.Gpio;
import mraa.Dir;
import mraa.Result;
import mraa.mraa;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.io.IOException;

/**
 *
 * @author Achmad Fathoni 13215061
 */
public class Adc {
    static final Gpio[] outpins = new Gpio[9];
    static final Gpio[] inpins = new Gpio[18];
    static volatile AtomicIntegerArray data = new AtomicIntegerArray(2);
    static volatile long elapsed;
    final static int T_CONV = 315000; // nanosecond
    
    public static void main(String[] args) throws InterruptedException, IOException {
        ///Config pins
        outpins[0] = new Gpio(3);//OS0
        outpins[1] = new Gpio(5);//OS1
        outpins[2] = new Gpio(7);//OS2
        outpins[3] = new Gpio(8);//RAGE
        
        ///Control pins
        outpins[4] = new Gpio(11);//CVA
        outpins[5] = new Gpio(10);//CVB
        outpins[6] = new Gpio(12);//RD
        outpins[7] = new Gpio(13);//RST
        outpins[8] = new Gpio(15);//CS
        
        setDir(outpins, Dir.DIR_OUT);
        
        ///DB0 to DB15
        inpins[0]  = new Gpio(19); inpins[1]  = new Gpio(22);
        inpins[2]  = new Gpio(21); inpins[3]  = new Gpio(24);
        inpins[4]  = new Gpio(23); inpins[5]  = new Gpio(28); 
        inpins[6]  = new Gpio(27); inpins[7]  = new Gpio(32);
        inpins[8]  = new Gpio(29); inpins[9]  = new Gpio(36);
        inpins[10] = new Gpio(31); inpins[11] = new Gpio(38);
        inpins[12] = new Gpio(33); inpins[13] = new Gpio(40);
        inpins[14] = new Gpio(35); inpins[15] = new Gpio(37);
        inpins[16] = new Gpio(16); //BUSY
        inpins[17] = new Gpio(18); //FRST
        
        setDir(inpins, Dir.DIR_IN);
        System.out.println("Success configuring GPIO");
        //Set all configuration Pin to Operate According to Figure 2 and 4
        //16 bit serial
        
        ///Pull down RST
        outpins[7].write(0);
        
        ///Oversampling ratio 64
        outpins[0].write(0);
        outpins[1].write(1);
        outpins[2].write(1);
        
        ///Range +-10V
        outpins[3].write(1);
        
        ///Initial control pin condition
        outpins[4].write(1);
        outpins[5].write(1);
        outpins[6].write(1);
        outpins[8].write(1);
        
        ///Pulling up RST to apply initial control pin configuration.
        outpins[7].write(1);
        outpins[7].write(0);
        
        if(args.length != 0){
            switch(Integer.parseInt(args[0])){
                case 0:
                    Thread t_print = new Thread(new Print());
                    t_print.start();
                    break;
                case 1:
                    Thread t_network0 = new Thread(new Network());
                    t_network0.start();
                    break;
            }
        }
        
        //Reading Cycle
        while(true){
            long start = System.nanoTime();
            //Clock CVA and CVB
            outpins[4].write(0);
            outpins[5].write(0);
            outpins[4].write(1);
            outpins[5].write(1);
            
            //Wait Busy Fall
            long start2 = System.nanoTime();
            while(System.nanoTime()-start2 <= T_CONV){}
                
            //Falling CS
            outpins[8].write(0);
            
            //Read 8 Data
            for (int i = 0; i < 2; i++) {
                 //Falling RD
                outpins[6].write(0);
                
                //Read only first 2 data
                short raw = 0;
                for (int j = 0; j < 16; j++) {
                    raw +=  (inpins[j].read() << j);
                }
                data.set(i, raw);
                                
                //Clock RD
                outpins[6].write(1);
            }
            // Rising CS
            outpins[8].write(1);
            
            elapsed = System.nanoTime()-start;
        }
    }
    
    static class Print implements Runnable {
        @Override
        public void run() {
            while(true){
                System.out.printf(
                    "%d %d %.0f%n",data.get(0), data.get(1),10e9/elapsed
                );
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                } 
            }
        }
    }
    
    static class Network implements Runnable{     
        @Override
        public void run(){
            final long periode = 1000000000/10000;//10e9/frequency
            while(true){
                try{
                    Server server = new Server(6066);
                    while(true){
                        long start = System.nanoTime();
                        while(System.nanoTime() - start <= periode){}
                        //channel 1(short) and channel 2(short) combined to integer
                        server.write(data.get(0) + (data.get(1) << 16));
                    }
                }catch(IOException e){
                }
            }
        }
    }
    
    static private void setDir(Gpio[] gpios, Dir dir){
        for (Gpio gpio : gpios) {
            Result result;
            boolean triggered = false;
            while ((result = gpio.dir(dir)) != Result.SUCCESS) {
                if(!triggered){
                    System.err.printf("Retry to set pin %d, ", gpio.getPin());
                    mraa.printError(result);
                    triggered = true;
                }       
            }
        }
    }
}    
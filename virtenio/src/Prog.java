/*
 * Copyright (c) 2011., Virtenio GmbH
 * All rights reserved.
 *
 * Commercial software license.
 * Only for test and evaluation purposes.
 * Use in commercial products prohibited.
 * No distribution without permission by Virtenio.
 * Ask Virtenio for other type of license at info@virtenio.de
 *
 * Kommerzielle Softwarelizenz.
 * Nur zum Test und Evaluierung zu verwenden.
 * Der Einsatz in kommerziellen Produkten ist verboten.
 * Ein Vertrieb oder eine Ver�ffentlichung in jeglicher Form ist nicht ohne Zustimmung von Virtenio erlaubt.
 * F�r andere Formen der Lizenz nehmen Sie bitte Kontakt mit info@virtenio.de auf.
 */

/*
 * Copyright (c) 2011., Virtenio GmbH
 * All rights reserved.
 *
 * Commercial software license.
 * Only for test and evaluation purposes.
 * Use in commercial products prohibited.
 * No distribution without permission by Virtenio.
 * Ask Virtenio for other type of license at info@virtenio.de
 *
 * Kommerzielle Softwarelizenz.
 * Nur zum Test und Evaluierung zu verwenden.
 * Der Einsatz in kommerziellen Produkten ist verboten.
 * Ein Vertrieb oder eine Ver�ffentlichung in jeglicher Form ist nicht ohne Zustimmung von Virtenio erlaubt.
 * F�r andere Formen der Lizenz nehmen Sie bitte Kontakt mit info@virtenio.de auf.
 */

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.virtenio.driver.device.ADXL345;
import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.gpio.GPIO;
import com.virtenio.driver.gpio.NativeGPIO;
import com.virtenio.driver.led.LED;
import com.virtenio.driver.spi.NativeSPI;
import com.virtenio.driver.timer.NativeTimer;
import com.virtenio.driver.timer.Timer;
import com.virtenio.io.Console;
import com.virtenio.preon32.shuttle.Shuttle;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.vm.Time;
import com.virtenio.vm.event.AsyncEvent;
import com.virtenio.vm.event.AsyncEventHandler;
import com.virtenio.vm.event.AsyncEvents;
import com.virtenio.driver.button.Button;
import com.virtenio.preon32.cpu.CPUConstants;
import com.virtenio.preon32.cpu.CPUHelper;




/* 1. need to add noise filter -> need to measure noise
 * 		1.1 need to change frequency display on fft based on time sampling
 * 		1.2 need to make sure sampling frequency is consistent
 * 2. need to know the start of vibration -> need to know mean and std value of accel data when there is no vibration
 * 3. need to add cli for external setting of radio address, panid, etc
 * 4. need to add customized radio setting (cca,csma, backoff, etc)
 * 		4.5 need to add additional application layer to make decision of keep trying or not in case of radio failure
 * 4.6 need to add automatic address broadcast of clusterhead?
 * 5. need to add backup register to save settings in case of power shutdown
 * 6. need to add sleep setting and decision of when to sleep
 * 7. need to add wake setting and when to wake
 * 8. customized cpu helper
 */


/**
 * Example for the Triple Axis Accelerometer sensor ADXL345
 */
public class Prog {
	private ADXL345 accelerationSensor;
	private GPIO accelIrqPin1;
	private GPIO accelIrqPin2;
	private GPIO accelCs;
	/** Interne Variablen */
	private int COMMON_CHANNEL = 0x0010;
	private int COMMON_PANID = 0x000A;
	private int ADDR_HEAD = 0xAF01;
	private int ADDR_WORKER1 = 0xAE01;
	private int ADDR_WORKER2 = 0xAE02;
	private int ADDR_WORKER3 = 0xAE03;
	private int ADDR_WORKER4 = 0xAE04;
	private long counter;
	private float samp_time;
	

	private void init() throws Exception {
		System.out.println("GPIO(Init)");		
		
		accelIrqPin1 = NativeGPIO.getInstance(37);
		accelIrqPin2 = NativeGPIO.getInstance(25);
		accelCs = NativeGPIO.getInstance(20);

		System.out.println("SPI(Init)");
		NativeSPI spi = NativeSPI.getInstance(0);
		spi.open(ADXL345.SPI_MODE, ADXL345.SPI_BIT_ORDER, ADXL345.SPI_MAX_SPEED);

		System.out.println("ADXL345(Init)");
		accelerationSensor = new ADXL345(spi, accelCs);
		accelerationSensor.open();
		accelerationSensor.setDataFormat(ADXL345.DATA_FORMAT_RANGE_2G);
		accelerationSensor.setDataRate(ADXL345.DATA_RATE_3200HZ);
		accelerationSensor.setPowerControl(ADXL345.POWER_CONTROL_MEASURE);

		System.out.println("Done(Init)");
	}

	public void prog_sender(String values, int short_address_origin, int dest_address) throws Exception {
		final Shuttle shuttle = Shuttle.getInstance();

		AT86RF231 radio = RadioInit.initRadio();
		radio.setChannel(COMMON_CHANNEL);
		radio.setPANId(COMMON_PANID);
		radio.setShortAddress(short_address_origin);

		LED green = shuttle.getLED(Shuttle.LED_GREEN);
		green.open();

		LED red = shuttle.getLED(Shuttle.LED_RED);
		red.open();

		boolean isOK = false;
		while (!isOK) {
			try {
				// ///////////////////////////////////////////////////////////////////////
				Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
						| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
				frame.setSrcAddr(short_address_origin);
				frame.setSrcPanId(COMMON_PANID);
				frame.setDestAddr(dest_address);
				frame.setDestPanId(COMMON_PANID);
				radio.setState(AT86RF231.STATE_TX_ARET_ON);
				frame.setPayload(values.getBytes());
				radio.transmitFrame(frame);
				// ///////////////////////////////////////////////////////////////////////
				Misc.LedBlinker(green, 100, false);
//				System.out.println(values);
				isOK = true;
			} catch (Exception e) {
				Misc.LedBlinker(red, 100, false);
				System.out.println(" ERROR: no receiver");
			}
		}
		radio.close();
	}


	public void prog_receiver() throws Exception {
		System.out.println("Receiver");

		final Shuttle shuttle = Shuttle.getInstance();

		final AT86RF231 radio = RadioInit.initRadio();
		radio.setChannel(COMMON_CHANNEL);
		radio.setPANId(COMMON_PANID);
		radio.setShortAddress(ADDR_HEAD);

		final LED orange = shuttle.getLED(Shuttle.LED_ORANGE);
		orange.open();

		Thread reader = new Thread() {
			@Override
			public void run() {
				while (true) {
					Frame f = null;
					try {
						f = new Frame();
						radio.setState(AT86RF231.STATE_RX_AACK_ON);
						radio.waitForFrame(f);
						Misc.LedBlinker(orange, 100, false);
					} catch (Exception e) {
					}

					if (f != null) {
						byte[] dg = f.getPayload();
						String str = new String(dg, 0, dg.length);
						
					    int addr = (int) f.getSrcAddr();
						String hex_addr = Integer.toHexString((int) f.getSrcAddr());
					    String worker = "none";
						if (addr == ADDR_WORKER1) {
							worker = "Worker1";
						}
						else if (addr == ADDR_WORKER2) {
							worker = "Worker2";
						}
						else if (addr == ADDR_WORKER3) {
							worker = "Worker3";
						}
						else if (addr == ADDR_WORKER4) {
							worker = "Worker4";
						}
						
						System.out.println(worker + " " + hex_addr + " : " + str);
					}
				}
			}
		};
		reader.start();

		while (true) {
			Misc.sleep(1000);
		}
	}

		
	public void transformRadix2(float[] real, float[] imag) {
		// Length variables
		int n = real.length;
		if (n != imag.length)
			throw new IllegalArgumentException("Mismatched lengths");
		int levels = 31 - Integer.numberOfLeadingZeros(n);  // Equal to floor(log2(n))
		if (1 << levels != n)
			throw new IllegalArgumentException("Length is not a power of 2");
		
		// Trigonometric tables
		float[] cosTable = new float[n / 2];
		float[] sinTable = new float[n / 2];
		for (int i = 0; i < n / 2; i++) {
			cosTable[i] = (float)Math.cos(2 * Math.PI * i / n);
			sinTable[i] = (float)Math.sin(2 * Math.PI * i / n);
		}
		
		// Bit-reversed addressing permutation
		for (int i = 0; i < n; i++) {
			int j = Integer.reverse(i) >>> (32 - levels);
			if (j > i) {
				float temp = real[i];
				real[i] = real[j];
				real[j] = temp;
				temp = imag[i];
				imag[i] = imag[j];
				imag[j] = temp;
			}
		}
		
		// Cooley-Tukey decimation-in-time radix-2 FFT
		for (int size = 2; size <= n; size *= 2) {
			int halfsize = size / 2;
			int tablestep = n / size;
			for (int i = 0; i < n; i += size) {
				for (int j = i, k = 0; j < i + halfsize; j++, k += tablestep) {
					int l = j + halfsize;
					float tpre =  real[l] * cosTable[k] + imag[l] * sinTable[k];
					float tpim = -real[l] * sinTable[k] + imag[l] * cosTable[k];
					real[l] = real[j] - tpre;
					imag[l] = imag[j] - tpim;
					real[j] += tpre;
					imag[j] += tpim;
				}
			}
			if (size == n)  // Prevent overflow in 'size *= 2'
				break;
		}
	}

	
	public float[] getData(int nsample, long[] time) throws Exception{
        
        short[] values = new short[3];
        float[] accel_in_g = new float[3]; 
                
        int i=0;
        float []y=new float[nsample];
        //isi window awal untuk keperluan
        while(i<nsample){
        	if (accelerationSensor.isDataReady()) {
	        	accelerationSensor.getValuesRaw(values, 0);
			 	accelerationSensor.convertRaw(values, 0 , accel_in_g, 0);
			 	y[i] = accel_in_g[2];
			 	time[i] = counter;
	            i ++;
        	}
        }
                                
        return y;
    }

	/* real jadi magnitude, imag jadi frequency */
	public void magfft(float[] real, float[] imag){
        for (int i = 0; i < real.length; i++){
            real[i] = (float) (2* Math.sqrt((real[i]*real[i])+(imag[i]*imag[i])))/real.length;
            imag[i] = (float)( i );            
        }
        
    }
	
	public void peakPicking(float[] mag, float[] freq, float[] peak){
	    float ctemp_mag;
	    float ctemp_freq;
	    boolean swapped = true;
	    while ( swapped ) {
	        swapped = false;
	        for ( int i=0;i<(mag.length-1)/2;i++ ) {
	        if (mag[i] < mag[i+1]) {
	                ctemp_mag = mag[i];
	                ctemp_freq = freq[i];
	                mag[i] = mag[i+1];
	                freq[i] = freq[i+1];
	                mag[i+1] = ctemp_mag;
	                freq[i+1] = ctemp_freq;
	                swapped = true;
	            }
	        }
	    }
//	    float f = (float) round(complex[0].getFrequency(), 2);
//	    float modeshape = (float) round(calculateModeShape(f, (float)complex[0].getMagnitude()), 10);
//	    return  f + ":" + modeshape;
	    
	    peak[0] = mag[0];
	    peak[1] = freq[0]/512*1000;;
	} 
	
	public void timerInit() {
		final int eventId = 7;
		final int delta = 500;

		AsyncEvents events = AsyncEvents.getAsyncEvents();		
		AsyncEvent event = events.getEvent(eventId);
		event.addHandler(new AsyncEventHandler() {
			@Override
			public void handleAsyncEvent(int eventId) {
				counter += delta;
			}
		});
		events.start();
		System.out.println("Timer(init)");

		// open the timer to generate events
		NativeTimer timer = NativeTimer.getInstance(0);
		try {
			timer.open(Timer.MODE_CONTINUOUS, eventId, delta, Timer.UNIT_MICROS);
			timer.enable();
		} catch (Exception e) {
			System.out.println("Timer error");
		}
	}

	
	public void runGetData() throws Exception {
		init();
		timerInit();
		
		Console console = new Console();
		Prog program = new Prog();
		
		float []accel_z_real = new float[512];
		float []accel_z_imag = new float[512];
		float []peak = new float[2];
		long []time = new long[512];
		
		String line = "worker1";//console.readLine("'worker1234' or 'head'?");
		System.out.println("id " + Integer.toHexString(ADDR_WORKER1));
		System.out.println("channel " + Integer.toHexString(COMMON_CHANNEL));
		System.out.println("pan " + Integer.toHexString(COMMON_PANID));
		System.out.println("head " + Integer.toHexString(ADDR_HEAD));
		
		if (line.equalsIgnoreCase("worker1") || line.equalsIgnoreCase("worker2") || line.equalsIgnoreCase("worker3") || line.equalsIgnoreCase("worker4")) { 
			try {
				System.out.println("==========accel==========");
				accel_z_real = getData(512, time);
//				for ( int i=0; i < 512;i++ ) {
//					System.out.println(time[i] + " " + accel_z_real[i]);
//			    } 
//				System.out.println("==========fft==========");
//				program.transformRadix2(accel_z_real, accel_z_imag);
//				program.magfft(accel_z_real, accel_z_imag);
//				for ( int i=0; i < 512;i++ ) {
//					System.out.println(accel_z_real[i] + " " + accel_z_imag[i]);
//				} 
//				
//				System.out.println("==========peak==========");
//				program.peakPicking(accel_z_real, accel_z_imag, peak);
//				for ( int i=0; i < 512;i++ ) {
//					System.out.println(accel_z_real[i] + " " + accel_z_imag[i]);
//				} 
//				System.out.println("");
//				System.out.println(Arrays.toString(peak));
			} catch (Exception e) {
				System.out.println("ADXL345 error");
			}
			
			if (line.equalsIgnoreCase("worker1")) {
				program.prog_sender(Arrays.toString(accel_z_real), ADDR_WORKER1, ADDR_HEAD);
			}
			else if (line.equalsIgnoreCase("worker2")) {
				program.prog_sender(Arrays.toString(peak), ADDR_WORKER2, ADDR_HEAD);
			}
			else if (line.equalsIgnoreCase("worker3")) {
				program.prog_sender(Arrays.toString(peak), ADDR_WORKER3, ADDR_HEAD);
			}
			else if (line.equalsIgnoreCase("worker4")) {
				program.prog_sender(Arrays.toString(peak), ADDR_WORKER4, ADDR_HEAD);
			}
		}
		else if (line.equalsIgnoreCase("head")) {
			program.prog_receiver();
		}						
	}
	
	public void run() throws Exception {
		init();
		timerInit();

			
		short[] values = new short[3];
		float[] accel_in_g = new float[3];
		while(true) {
			if (accelerationSensor.isDataReady()) {
				try {
					accelerationSensor.getValuesRaw(values, 0);
				 	accelerationSensor.convertRaw(values, 0 , accel_in_g, 0);
					System.out.println(counter + " " + Arrays.toString(accel_in_g));
				} catch (Exception e) {
					System.out.println("ADXL345 error");
				}
			}
		}
	}

	public void runSend() throws Exception {
		init();
		Console console = new Console();
		Prog program = new Prog();
		
		short[] values = new short[3];
		float[] accel_in_g = new float[3]; 
//		String line = console.readLine("'worker1234' or 'head'?");
		String line = "worker1";
		System.out.println("id " + Integer.toHexString(ADDR_WORKER1));
		System.out.println("channel " + Integer.toHexString(COMMON_CHANNEL));
		System.out.println("pan " + Integer.toHexString(COMMON_PANID));
		System.out.println("head " + Integer.toHexString(ADDR_HEAD));
		while (true) {
			if (line.equalsIgnoreCase("worker1")) {
//				try {
//					accelerationSensor.getValuesRaw(values, 0);
//				 	accelerationSensor.convertRaw(values, 0 , accel_in_g, 0);
//				} catch (Exception e) {
//					System.out.println("ADXL345 error");
//				}
//				program.prog_sender(Arrays.toString(accel_in_g), ADDR_WORKER1, ADDR_HEAD);
				float[] testarray = new float[5];
				float[] sendarray = new float[9];
				testarray[0] = 0.1f;
				testarray[1] = 0.2f;
				testarray[2] = 0.3f;
				testarray[3] = 0.4f;
				testarray[4] = 0.5f;
				sendarray = program.prepareDataToSink(testarray, 0.6f, 0.7f);
				System.out.println(Arrays.toString(sendarray));
				program.sendData(FloatArray2ByteArray(sendarray), COMMON_CHANNEL, COMMON_PANID, ADDR_WORKER1, ADDR_HEAD);
			}
			if (line.equalsIgnoreCase("worker2")) {
				try {
					accelerationSensor.getValuesRaw(values, 0);
				 	accelerationSensor.convertRaw(values, 0 , accel_in_g, 0);
				} catch (Exception e) {
					System.out.println("ADXL345 error");
				}
				program.prog_sender(Arrays.toString(accel_in_g), ADDR_WORKER2, ADDR_HEAD);
			}
			if (line.equalsIgnoreCase("worker3")) {
				try {
					accelerationSensor.getValuesRaw(values, 0);
				 	accelerationSensor.convertRaw(values, 0 , accel_in_g, 0);
				} catch (Exception e) {
					System.out.println("ADXL345 error");
				}
				program.prog_sender(Arrays.toString(accel_in_g), ADDR_WORKER3, ADDR_HEAD);
			}
			if (line.equalsIgnoreCase("worker4")) {
				try {
					accelerationSensor.getValuesRaw(values, 0);
				 	accelerationSensor.convertRaw(values, 0 , accel_in_g, 0);
				} catch (Exception e) {
					System.out.println("ADXL345 error");
				}
				float[] testarray = new float[6];
				testarray[0] = 0.1f;
				testarray[1] = 0.2f;
				testarray[2] = 0.3f;
				testarray[3] = 0.4f;
				testarray[4] = 0.5f;
				testarray[5] = 0.6f;
//				program.prog_sender(Arrays.toString(accel_in_g), ADDR_WORKER4, ADDR_HEAD);
				program.sendData(FloatArray2ByteArray(testarray), COMMON_CHANNEL, COMMON_PANID, ADDR_WORKER4, ADDR_HEAD);
			}
			if (line.equalsIgnoreCase("head")) {
				program.prog_receiver();
			}
								
			Thread.sleep(500);
		}
		
	}
	
	public boolean sendData(byte []values, int channel, int pan_id, int source_id, int dest_id) throws Exception {
		final Shuttle shuttle = Shuttle.getInstance();

		AT86RF231 radio = RadioInit.initRadio();
		radio.setChannel(channel);
		radio.setPANId(pan_id);
		radio.setShortAddress(source_id);

		LED green = shuttle.getLED(Shuttle.LED_GREEN);
		green.open();

		long start_time = counter;
		long last_time = 0;
		boolean isOK = false;
		while (!isOK && (last_time - start_time < 10000)) {
			try {
				
				// ///////////////////////////////////////////////////////////////////////
				Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
						| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
				frame.setSrcAddr(source_id);
				frame.setSrcPanId(COMMON_PANID);
				frame.setDestAddr(dest_id);
				frame.setDestPanId(COMMON_PANID);
				radio.setState(AT86RF231.STATE_TX_ARET_ON);
				frame.setPayload(values);
				radio.transmitFrame(frame);
				// ///////////////////////////////////////////////////////////////////////				
				Misc.LedBlinker(green, 100, false);
//				System.out.println(values);
				isOK = true;
			} catch (Exception e) {
				System.out.println(" ERROR: no receiver");
			}
		}
		radio.close();
		
		return (isOK);
	}
	
	public static byte[] FloatArray2ByteArray(float[] values){
	    ByteBuffer buffer = ByteBuffer.allocate(4 * values.length);

	    for (float value : values){
	        buffer.putFloat(value);
	    }

	    return buffer.array();
	}
	
	public float[] prepareDataToSink(float []mode, float mean_freq, float mean_amp) {
		float []data_to_sink = new float[mode.length + 4];
		for (byte i = 0; i < mode.length; i++){
			if (mode[i] != (float) 0x7fc00001 && mode[i] != (float)0x7fc00002) {
				data_to_sink[i+1] = mode[i];
			}
			else {
				data_to_sink[i+1] = 0;
			}
		}
		
		byte[] nan1 = new byte[4];
		nan1[0] = (byte) 0x7f;
		nan1[1] = (byte) 0xc0; 
		nan1[2] =(byte) 0x00;
		nan1[3] = (byte) 0x01;
		data_to_sink[0] = byteArray2Float(nan1);
		
		if (mean_freq != (float) 0x7fc00001 && mean_freq != (float) 0x7fc00002) {
			data_to_sink[6] = mean_freq;
		}
		else {
			data_to_sink[6] = 0;
		}
		if (mean_amp != (float) 0x7fc00001 && mean_amp != (float) 0x7fc00002) {
			data_to_sink[7] = mean_amp;
		}
		else {
			data_to_sink[7] = 0;
		}
		byte[] nan2 = new byte[4];
		nan2[0] = (byte) 0x7f;
		nan2[1] = (byte) 0xc0; 
		nan2[2] =(byte) 0x00;
		nan2[3] = (byte) 0x02;
		data_to_sink[8] = byteArray2Float(nan2);
				
		return data_to_sink;
	}
	
	public static float byteArray2Float (byte []array) {
		byte []temp = new byte[4];
		if (array.length == 4) {
			 temp = array;
		 }
		 if (array.length == 3) {
			 temp[0] = 0;
			 temp[1] = array[0];
			 temp[2] = array[1];
			 temp[3] = array[2];
		 }
		 else if (array.length == 2) {
			 temp[0] = 0;
			 temp[1] = 0;
			 temp[2] = array[0];
			 temp[3] = array[1];
		 }
		 else if (array.length == 1) {
			 temp[0] = 0;
			 temp[1] = 0;
			 temp[2] = 0;
			 temp[3] = array[0];
		 }		
		return ByteBuffer.wrap(temp).getFloat();
	}
	
	public static void main(String[] args) throws Exception {
		final Shuttle shuttle = Shuttle.getInstance();
		CPUHelper.setClock(CPUConstants.V_CLOCK_MODE_HSI, CPUConstants.V_CLOCK_PROFILE_5); 

		final LED yellow = shuttle.getLED(Shuttle.LED_YELLOW);
		
		yellow.open();
		yellow.on();
		
//		new Prog().runGetData();
//		new Prog().runSend();
		new Prog().prog_receiver();
		
	}
}

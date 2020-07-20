/**
 * @author Gian Arjuna
 *
 */


/* Import Library */
import java.util.Arrays;
import com.virtenio.driver.button.Button;
import com.virtenio.driver.device.ADXL345;
import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.flash.Flash;
import com.virtenio.driver.gpio.GPIO;
import com.virtenio.driver.gpio.GPIOException;
import com.virtenio.driver.gpio.NativeGPIO;
import com.virtenio.driver.led.LED;
import com.virtenio.driver.spi.NativeSPI;
import com.virtenio.driver.spi.SPIException;
import com.virtenio.driver.timer.NativeTimer;
import com.virtenio.driver.timer.Timer;
import com.virtenio.io.Console;
import com.virtenio.preon32.cpu.CPUConstants;
import com.virtenio.preon32.cpu.CPUHelper;
import com.virtenio.preon32.node.Node;
import com.virtenio.preon32.shuttle.Shuttle;
import com.virtenio.vm.event.AsyncEvent;
import com.virtenio.vm.event.AsyncEventHandler;
import com.virtenio.vm.event.AsyncEvents;
import com.virtenio.driver.cpu.CPU;
import com.virtenio.driver.cpu.CPUException;
import com.virtenio.driver.cpu.NativeCPU;

/* Class untuk worker sensor node */
public class Worker extends Sensor {

	/* 
	 * objek akselerometer 
	 * 
	 * objek ini digunakan untuk memanggil driver akselerometer pada varisens
	 */
	private ADXL345 accelerationSensor;
	
	/* 
	 * object  GPIO
	 * 
	 * objek ini digunakan untuk mengaktifkan pin-pin pada shuttle preon
	 * untuk digunakan bersama objek akselerometer
	 */
	private GPIO accelIrqPin1;
	private GPIO accelIrqPin2;
	private GPIO accelCs;
	
	/* 
	 * Variable identitas
	 * 
	 * head_id adalah address head pada jaringan
	 */
	private int head_id;
	
	/* 
	 * Variable akuisisi data
	 * 
	 * sample_num : panjang window yang digunakan saat melakukan fft
	 * resampling_factor : faktor pengali untuk mendapatkan panjang window saat sampling data
	 * accel_z_real_long : array untuk menyimpan raw data
	 * accel_z_real : array untuk menyimpan data setelah resampling
	 * accel_z_imag : array untuk menyimpan data imaginer saat fft
	 * peak : array untuk menyimpan magnituda terbesar beserta frekuensinya
	 * samp_freq : variable untuk menyimpan frekuensi sampling
	 */
	private int sample_num = 512;
	private int resampling_factor = 10;
	private float []accel_z_real_long = new float[sample_num*resampling_factor];
	private float []accel_z_real = new float[sample_num];
	private float []accel_z_imag = new float[sample_num];
	private float []peak = new float[2];
//	private long []time = new long[sample_num*3];
	private float samp_freq;
	private final int samp_time = 1;
	
	/* 
	 * Variable status 
	 * 
	 * send_status : menyimpan status keberhasilan pengiriman data
	 */
	private int index = 0;
	private int send_status = ~SEND_SUCCESS;

	
	/** 
	 * Melakukan inisialisai pin dan variable identitas dari sensor node
	 * 
	 * @param
	 */
	private void workerInit() throws Exception {
		/*
		 * Deklarasi variable shuttle preon, LED, dan button. LED kuning akan menyala
		 * untuk menandakan bahwa node sudah aktif
		 */
		final Shuttle shuttle = Shuttle.getInstance();
		final LED yellow = shuttle.getLED(Shuttle.LED_YELLOW);		
		yellow.open();
		yellow.on();
		final Button button = shuttle.getButton();
		button.open();
		
		/*
		 * inisialisasi GPIO  
		 */
		System.out.println("GPIO(Init)");				
		accelIrqPin1 = NativeGPIO.getInstance(37);
		accelIrqPin2 = NativeGPIO.getInstance(25);
		accelCs = NativeGPIO.getInstance(20);

		/*
		 * Inisialisasi SPI 
		 */
		System.out.println("SPI(Init)");
		NativeSPI spi = NativeSPI.getInstance(0);
		spi.open(ADXL345.SPI_MODE, ADXL345.SPI_BIT_ORDER, ADXL345.SPI_MAX_SPEED);

		/*
		 * Inisialisasi akselerometer
		 * range data adalah -2G sampai 2G
		 * Frekuensi akuisisi data adalah 3200 Hz, namun secara empiris didapatkan frekuensi akuisisi maksimum 100 Hz
		 */
		System.out.println("ADXL345(Init)");
		accelerationSensor = new ADXL345(spi, accelCs);
		accelerationSensor.open();
		accelerationSensor.setDataFormat(ADXL345.DATA_FORMAT_RANGE_2G);
		accelerationSensor.setDataRate(ADXL345.DATA_RATE_3200HZ);
		accelerationSensor.setPowerControl(ADXL345.POWER_CONTROL_MEASURE);
		
		/*
		 * Inisialisasi modul radio 
		 */
		System.out.println("Radio(Init)");
		
		
		/*
		 * Inisialisasi data identitas node dan mengambil data dari flash
		 */
		Flash flash = Node.getInstance().getFlash();
		flash.open();	
		byte[] data = new byte[8];

		/* membaca data address worker (address sendiri) */
		flash.read(0xFFFF0, data, 0, 4);
		id = byteArray2IntFlash(data);
		
		/* membaca data channel radio */
		flash.read(0xFFFF4, data, 0, 4);
		common_channel = byteArray2IntFlash(data);
		
		/* membaca data pan id */
		flash.read(0xFFFF8, data, 0, 4);
		common_pan_id = byteArray2IntFlash(data);
		
		/* membaca data address head */
		flash.read(0xFFFFC, data, 0, 4);
		head_id = byteArray2IntFlash(data);
		
		
		/* 
		 * jika data address worker, channel, pan id belum ada atau SW button ditekan
		 * maka sistem akan meminta untuk menyimpan data baru
		 */
		if (id == 0 || common_channel == 0|| common_pan_id == 0 || button.isPressed())
		{
			/* menerima data dari console pc */
			Console console = new Console();
			System.out.println("Setup Worker");
			String id_s = console.readLine("Insert ID (0-255): ");
			String head_s = console.readLine("Insert Head ID (0-255): ");
			String channel_s = console.readLine("Insert channel: ");
			String pan_id_s = console.readLine("Insert pan id : ");
			
			/* menghapus block flash agar data dapat ditimpa dengan bersih */
			flash.eraseBlock(0xFFFF0);
			
			/* 
			 * menyimpan data address worker (sendiri) 
			 * address worker pasti dimulai dengan 0xAE.. 
			 */
			id = (0xAE << 8) | Byte.parseByte(id_s);		
			data = int2ByteArray(id); 
			flash.write(0xFFFF0, data, 0, 4); 	
			
			/* menyimpan data channel */
		    common_channel = Integer.parseInt(channel_s);
		    data = int2ByteArray(common_channel); 
			flash.write(0xFFFF4, data, 0, 4);
			
			/* menyimpan data pan id */
		    common_pan_id = Integer.parseInt(pan_id_s);
		    data = int2ByteArray(common_pan_id); 
			flash.write(0xFFFF8, data, 0, 4);
			
			/* menyimpan address head */
			head_id = (0xAF << 8) | Byte.parseByte(head_s); 
			data = int2ByteArray(head_id); 
			flash.write(0xFFFFC, data, 0, 4); 	    
		}	
		flash.close();

		/* menampilkan data yang sudah disimpan */
		System.out.println("Done(Init)");
		System.out.println("id " + Integer.toHexString(id));
		System.out.println("channel " + common_channel);
		System.out.println("pan " + Integer.toHexString(common_pan_id));
		System.out.println("head " + Integer.toHexString(head_id));
	}
	
	/** 
	 * Melakukan filtering dengan IIR filter. konstanta filter harus diubah secara manual pada kode dan 
	 * harus di-cast menjadi float.
	 * 
	 * @param data array yang akan difilter
	 * @return array data yang sudah difilter
	 */
	public float[] IIRFilter(float []data) throws Exception {
	    float[] a    = new float[] {(float)1,(float)-5.0838113, (float)10.829378, (float)-12.365814, (float)7.9796319, (float)-2.7580497, (float)0.39878172};
	    float[] b    = new float[] {(float)1.8093250e-06, (float)1.0855950e-05, (float)2.7139877e-05, (float)3.6186502e-05, (float)2.7139875e-05, (float)1.0855950e-05, (float)1.8093250e-06};
		float[] temp = new float[data.length];
		float[] buffX = new float[a.length];
		float[] buffY = new float[b.length];
		float center = 0;

	    for (int i = 0; i < data.length; i++)
	    {
	        temp[i] = 0;
	    }
	    
	    for (int i = 0; i < b.length; i++) {
		    buffX[i] = 0;
	    	buffY[i] = 0;
	    }
	    
	    for(int j = 0; j < data.length; j++){
	    	// Shift the register values.
		     for(int k = b.length-1; k > 0; k--) {
		    	 buffX[k] = buffX[k-1];
		    	 buffY[k] = buffY[k-1];
		     }
		    
		     
		     buffX[0] = data[j];
		     center = b[0] * buffX[0];
		     for(int k = 1; k < b.length; k++)
		     {
		       center += b[k] * buffX[k] - a[k] * buffY[k];
		     }
		     buffY[0] = center * a[0];  		     
		     temp[j] = buffY[0]; 
		     
	    }
	    	    
	    return temp;	  
	}

	/** 
	 * Melakukan downsampling terhadap array data berdasarkan faktor pengali down_samp
	 * 
	 * @param data array yang akan di-downsampling
	 * @return array data yang sudah di-downsampling 
	 */
	public float[] reSampling (float[] data, int down_samp){
		float[] resampled_data = new float[(int) data.length/down_samp];
		for (int i = 0; i < (int) data.length/down_samp; i++) {
			resampled_data[i] = data[i*down_samp];
		}
		
		return (resampled_data);
	}
	
	/** 
	 * Melakukan FFT radix 2. array input digunakan kembali sebagai output untuk menghemat memori
	 * 
	 * @param real array ini berfungsi sebagai input raw data dan output data real hasil fft
	 * @param imag array ini berfungsi sebagai output data imaginer hasil fft
	 */
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
	
	/** 
	 * Melakukan perhitungan magnituda dan frekuensi berdasarkan hasil FFT. array input digunakan 
	 * kembali sebagai output untuk menghemat memori
	 * 
	 * @param real berfungsi sebagai input array nilai real hasil fft dan output array magnituda
	 * @param imag berfungsi sebagai input array nilai imaginer hasil fft dan output array frekuensi
	 */
	public void magfft(float[] real, float[] imag){
        for (int i = 0; i < real.length ; i++){
            real[i] = (float) (2* Math.sqrt((real[i]*real[i])+(imag[i]*imag[i])))/real.length;
            imag[i] = (float)(i);   
        }
        for (int i = 0; i < 1 ; i++){
            real[i] = (float) 0;
            imag[i] = (float)(i);   
        }
    }

	/** 
	 * Mencari nilai magnituda terbesar dari respon frekuensi beserta frekuensinya. implementasi algoritma ini 
	 * dilakukan dengan menggunakan bubble sort
	 * 
	 * @param mag array magnituda dari respon frekuensi
	 * @param freq array frekuensi dari respon frekuensi
	 * @param peak array output berisi nilai magnituda terbesar beserta frekuensinya
	 */
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
	    
	    peak[0] = mag[0];
	    peak[1] = freq[0]/mag.length*samp_freq/resampling_factor;
	}

	/** 
	 * Menghapus nilai array hasil akuisisi data agar dapat ditimpa kembali pada pengukuran selanjutnya
	 * 
	 * @param accel_z_real_long raw data hasil akuisisi dari akselerometer
	 * @param accel_z_real data magnituda dari respon frekuensi
	 * @param accel_z_imag data frekuensi dari respon frekuensi
	 * @param peak array magnituda terbesar beserta frekuensinya
	 */
	private void resetValue(float[] accel_z_real_long, float []accel_z_real, float []accel_z_imag, float []peak) {
		for (int i = 0; i < sample_num*resampling_factor; i++) {
			accel_z_real_long[i] = 0;
		}
		for (int i = 0; i < sample_num; i++) {
			accel_z_real[i] = 0;
			accel_z_imag[i] = 0;
		}
		for (int i = 0; i < 2; i++) {
			peak[i] = 0;
		}
	}

	public void eventAccelInit() {
		final int event_id = 3;	
		AsyncEvents events = AsyncEvents.getAsyncEvents();

		AsyncEvent event_accel = events.getEvent(event_id);
		event_accel.addHandler(new AsyncEventHandler() {
			@Override
			public void handleAsyncEvent(int event_id){
				short[] values = new short[3];
		        float[] accel_in_g = new float[3]; 
		        if (index < sample_num*resampling_factor) {
					try {
						accelerationSensor.getValuesRaw(values, 0);
					} catch (SPIException e) {
						e.printStackTrace();
					} catch (GPIOException e) {
						e.printStackTrace();
					}
					accelerationSensor.convertRaw(values, 0 , accel_in_g, 0);
					accel_z_real_long[index] = accel_in_g[0];
					index++;
		        }
			}
		});
	}

	/** 
	 * Melakukan akuisisi data akselerasi pada frekuensi tertentu sesuai dengan input samp_time
	 * secara otomatis data akan disimpan pada variable accel_z_real_long 
	 * dengan panjang window yang digunakan adalah sample_num*resampling_factor
	 * 
	 * @param samp_time perioda sampling saat melakukan akuisisi data dalam millisecond
	 */
	public void timedGetData(final int samp_time, int event_id) throws Exception {
		Timer timer_accel = NativeTimer.getInstance(1);
		timer_accel.open(Timer.MODE_CONTINUOUS, event_id, samp_time, Timer.UNIT_MILLIS);
		timer_accel.enable();
		
		try {
			Thread.sleep(samp_time*sample_num*resampling_factor*2);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		timer_accel.disable();
		timer_accel.close();
		index = 0;
		samp_freq = 1/(float)samp_time*1000;
		
	}
	
	/** 
	 * Menjalankan fungsi akuisisi data, downsampling, FFT, dan peak picking secara sekuensial.
	 * 
	 */
	public void runGetData() throws Exception {						
			System.out.println("==========accel==========");
			timedGetData(samp_time, 3);
//			for ( int i=0; i < sample_num*resampling_factor;i++ ) {
//				System.out.println(accel_z_real_long[i]);
//		    } 
			System.out.println("==========filter==========");
			accel_z_real_long = IIRFilter(accel_z_real_long);
//			for ( int i=0; i < sample_num*resampling_factor;i++ ) {
//				System.out.println(accel_z_real_long[i]);
//		    } 
			System.out.println("==========resampling==========");
			accel_z_real = reSampling(accel_z_real_long, resampling_factor);
//			for ( int i=0; i < sample_num;i++ ) {
//				System.out.println(accel_z_real[i]);
//		    } 
			System.out.println("==========fft==========");
			transformRadix2(accel_z_real, accel_z_imag);
			magfft(accel_z_real, accel_z_imag);
//			for ( int i=0; i < sample_num;i++ ) {
//				System.out.println(accel_z_real[i] + " " + accel_z_imag[i]);
//			} 
//			System.out.println(samp_freq);		
			System.out.println("==========peak==========");
			peakPicking(accel_z_real, accel_z_imag, peak);
//			System.out.println(Arrays.toString(peak));					
	}	
		

	public static void main(String[] args) throws Exception {
		
		/* Melakukan inisialisasi awal yang tidak dapat dilakukan dalam fungsi */
		Worker worker = new Worker();
		worker.workerInit();
		worker.eventAccelInit();
		NativeTimer timer = NativeTimer.getInstance(0);
		timer = worker.timerInit(timer);
		final Shuttle shuttle = Shuttle.getInstance();
//		final CPU cpu = NativeCPU.getInstance();
		final AT86RF231 radio = RadioInit.initRadio();
		final Button button = shuttle.getButton();
		LED green = shuttle.getLED(Shuttle.LED_GREEN);
		LED orange = shuttle.getLED(Shuttle.LED_ORANGE);
		LED red = shuttle.getLED(Shuttle.LED_RED);
		green.open();
		orange.open();
		red.open();
		button.open();	
		worker.status = 0x00;
		long timer_start = 0;
		long timer_end = 0;
		
		
		/* Infinite Loop */
		while (true) {
			/* State sleep, hanya radio yang aktif */
			if (worker.status == 0x00) {
				/* Menerima perintah jika ada kiriman dari head */
				worker.receiveData(radio, red, worker.common_channel, worker.common_pan_id, worker.id);
				
				/* jika perintah yang diberikan adalah wake */
				if (worker.received_data != null) {
//					if(button.isPressed()) {
					if (byteArray2Int(worker.received_data.getPayload()) == 0xBA17) {
						worker.status = 0x01;
						System.out.println("me wakey");
						orange.off();
					}
				}
				
				/* jika tidak terdapat perintah untuk bangun */
				else {
					System.out.println("slp");
					orange.on();
					/* set cpu dalam mode standby selama 600 detik, jika ada kiriman radio maka sleep akan ter-interrupt */
					CPUHelper.setPowerState(CPUConstants.V_POWER_STATE_STANDBY, 6000);
//					Misc.sleep(500);
					System.out.println("me see");
				}
			}
			 
			/* state akuisisi data */
			else if (worker.status == 0x01) {
				System.out.println("get data");
				
				/* Menyalakan LED hijau tanda pengambilan data */
				green.on();
				
				/* menghapus nilai array sebelumnya dan menimpa dengan nilai baru */
				worker.resetValue(worker.accel_z_real_long, worker.accel_z_real, worker.accel_z_imag, worker.peak);
				worker.runGetData();
				
				/* otomatis lanjut ke state berikutnya */
				green.off();
				timer_start = worker.counter;
				worker.status = 0x02;
			}
			
			/* state mengirimkan data ke head */
			else if (worker.status == 0x02) {
				/* kembali ke state sleep jika data sudah berhasil dikirimkan atau sudah lebih dari waktu timeout */
				if (worker.send_status == worker.SEND_SUCCESS || timer_end - timer_start > 60000) {
					worker.status = 0x00;
					System.out.println("me sleepy");
					worker.resetValue(worker.accel_z_real_long, worker.accel_z_real, worker.accel_z_imag, worker.peak);
					worker.send_status = ~worker.SEND_SUCCESS;
				}
				
				/* mencoba untuk mengirimkan data ke head */
				else {
					System.out.println(Arrays.toString(worker.peak));	
					worker.send_status = worker.sendData(radio, red, FloatArray2ByteArray(worker.peak), worker.common_channel, worker.common_pan_id, worker.id, worker.head_id);
				}
				timer_end = worker.counter;
			}
			
			/* garbage collector */
			System.gc(); 
		}
	}
}

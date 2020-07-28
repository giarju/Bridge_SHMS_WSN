/**
 * @author Gian Arjuna
 *
 */

import java.util.Arrays;
import com.virtenio.driver.button.Button;
import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.flash.Flash;
import com.virtenio.driver.led.LED;
import com.virtenio.driver.timer.NativeTimer;
import com.virtenio.io.Console;
import com.virtenio.preon32.cpu.CPUConstants;
import com.virtenio.preon32.cpu.CPUHelper;
import com.virtenio.preon32.node.Node;
import com.virtenio.preon32.shuttle.Shuttle;
import com.virtenio.radio.ieee_802_15_4.Frame;


public class ClusterHead extends Sensor {
	
	/* 
	 * Variable 
	 * 
	 * num_worker : jumlah worker yang sedang terkoneksi dengan head
	 * max_num_worker : jumlah maksimum worker yang dapat dikoneksikan dengan head
	 * nat_freq : array yang menyimpan data frekuensi natural dari worker
	 * max_amp : array yang menyimpan amplituda frekuensi natural dari worker
	 * mean_nat_freq : frekuensi natural rata-rata dari seluruh data worker
	 * mode_shape : array yang menyimpan data mode shape
	 * worker_id : array yang menyimpan address worker
	 */
	private byte num_worker;
	private byte max_num_worker = 5;
	private float mean_nat_freq;
	private float []nat_freq = new float[max_num_worker];
	private float []max_amp = new float[max_num_worker];
	private float []mode_shape = new float[max_num_worker];
	private int []worker_id = new int[max_num_worker];
	private int id4coord = 0xAF01;
	
	/* 
	 * Variable status
	 * 
	 * all_data : menyimpan status apakah semua data berhasil diterima dari worker
	 * coordinator_received : menyimpan status apakah data berhasil diterima koordinator
	 * 
	 */
	private int all_data;
	private int []worker_receive = new int[max_num_worker];
	private int coordinator_received = ~SEND_SUCCESS;

	/** 
	 * Melakukan inisialisai pin dan variable identitas dari sensor node
	 * 
	 * @param
	 */
	private void headInit() throws Exception {
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
		 * Inisialisasi modul radio 
		 */
		System.out.println("Radio(Init)");
		
		/*
		 * Inisialisasi data identitas node dan mengambil data dari flash
		 */
		byte count = 0;
		byte[] data = new byte[8];		
		Flash flash = Node.getInstance().getFlash();
		flash.open();
		
		/* 
		 * jika SW button ditekan maka sistem akan meminta untuk menyimpan data baru
		 */
		if (button.isPressed())
		{
			/* menerima data dari console pc */
			Console console = new Console();
			System.out.println("Setup Head");
			String id_s = console.readLine("Insert ID (0-255): ");
			String channel_s = console.readLine("Insert channel: ");
			String pan_id_s = console.readLine("Insert pan id : ");
			String num_worker_s = console.readLine("Insert number of worker : ");
			
			/* menghapus block flash agar data dapat ditimpa dengan bersih */
			flash.eraseBlock(0xAFE0);
			
			/* 
			 * menyimpan data address head (sendiri) 
			 * address head pasti dimulai dengan 0xAF.. 
			 */
			id = (0xAF << 8) | Byte.parseByte(id_s);		
			data = int2ByteArray(id); 
			flash.write(0xAFE0, data, 0, 4); 	
			
			/* menyimpan data channel */
		    common_channel = Integer.parseInt(channel_s);
		    data = int2ByteArray(common_channel); 
			flash.write(0xAFE4, data, 0, 4);
			
			/* menyimpan data pan id */
		    common_pan_id = Integer.parseInt(pan_id_s);
		    data = int2ByteArray(common_pan_id); 
			flash.write(0xAFE8, data, 0, 4);
			
			/* menyimpan data jumlah worker yang dimiliki head */
		    num_worker = Byte.parseByte(num_worker_s);
		    data[0] = num_worker; 
			flash.write(0xAFEC, data, 0, 1);
		    
			/* menyimpan address setiap worker */
			count = 0;
			while (count < num_worker) {
				String worker_s = console.readLine("worker id (0-255): ");
				worker_id[count] = (0xAE << 8) | Byte.parseByte(worker_s);
				data = int2ByteArray(worker_id[count]); 
				flash.write((0xAFED + 4*count), data, 0, 4);
				int test = 0xAFED + 4*count;
				System.out.println(worker_id[count] + " "+ test);
				count++;
			}
		}

		/* membaca data inisialisasi yang ada pada flash */
		flash.read(0xAFE0, data, 0, 4);
		id = byteArray2IntFlash(data);
		
		flash.read(0xAFE4, data, 0, 4);
		common_channel = byteArray2IntFlash(data);
		
		flash.read(0xAFE8, data, 0, 4);
		common_pan_id = byteArray2IntFlash(data);
		
		flash.read(0xAFEC, data, 0, 1);
		num_worker = data[0];
		
		
		while (count < num_worker) {
			flash.read((0xAFED + 4*count), data, 0, 4);
			worker_id[count] = byteArray2IntFlash(data);
			count++;
		}
		
		flash.close();

		/* menampilkan data yang sudah disimpan */
		System.out.println("id " + Integer.toHexString(id));
		System.out.println("channel " + common_channel);
		System.out.println("pan " + Integer.toHexString(common_pan_id));
		System.out.println("num_worker " + num_worker);
		
		count = 0;
		while (count < num_worker) {
			System.out.println(Integer.toHexString(worker_id[count]));
			count++;
		}
	}
	
	/** 
	 * Melakukan kalkulasi amplituda getaran jembatan dan membuat mode shape
	 * 
	 * @param freq array data frekuensi natural
	 * @param mag array data magnituda percepatan 
	 * @param displacement array output yang berisi magnituda perpindahan 
	 */
	private void calculateModeShape(float []freq, float []mag, float []displacement){
		for (byte i = 0; i < freq.length; i ++) {
			float omega = 2.0f * 3.14f * freq[i];
	        displacement[i] = mag[i] /(omega * omega);
		}
        
	}

	/** 
	 * Melakukan kalkulasi frekuensi natural rata-rata
	 * 
	 * @param nat_freq frekuensi natural dari masing-masing worker
	 * @return frekuensi natural rata-rata
	 */
	private float meanNatFreq(float[] nat_freq) {
		float sum = 0;
		int not_nan = 0;
		for (int i = 0; i < nat_freq.length; i++) {
			if(!Float.isNaN(nat_freq[i])){
				sum = sum + nat_freq[i];
				not_nan = not_nan + 1;
			}
		}
		
		return (sum/not_nan);
	}
	
	/** 
	 * Menyimpan data magnituda dan frekuensi natural dari setiap worker
	 * 
	 * @param data Frame yang diterima dari modul radio 
	 * @param amp array untuk menyimpan amplituda maksimum 
	 * @param freq array untuk menyimpan frekuensi natural 
	 * @param worker_addr array address worker
	 * @param num_worker jumlah worker yang aktif
	 * @param all_data array status yang menandakan bahwa data sudah diterima dari worker
	 */
	public int saveData(Frame data, float []amp, float []freq, int []worker_addr, byte num_worker, int all_data) {
		int addr = (int) data.getSrcAddr();
		
		for (byte i = 0; i < num_worker; i++) {
			if (addr == worker_addr[i]) {
				amp[i] = byteArray2Float(data.getPayload(0, 4));
				freq[i] = byteArray2Float(data.getPayload(4, 4));	
				all_data |= 1 << i;
			}
		}
		
		return all_data;
	}
	
	/** 
	 * Menyiapkan data untuk dikirimkan ke sink. data yang akan dikirimkan adalah mode shape dan frekuensi natural rata-rata
	 * data NaN akan diselipkan di awal dan akhir sebagai delimiter
	 * 
	 * @param mode array mode shape 
	 * @param mean_freq frekuensi natural rata-rata
	 */
	public float[] prepareDataToSink(float []mode, float mean_freq) {
		float []data_to_sink = new float[mode.length + 3];
		
		/* menyiapkan data NaN*/
		byte[] nan0 = new byte[4];
		nan0[0] = (byte) 0x7f;
		nan0[1] = (byte) 0xc0; 
		nan0[2] =(byte) 0x01;
		nan0[3] = (byte) 0x1;
		
		byte[] nan1 = new byte[4];
		nan1[0] = (byte) 0x7f;
		nan1[1] = (byte) 0xc0; 
		nan1[2] =(byte) 0x00;
		nan1[3] = (byte) 0x01;
		data_to_sink[0] = byteArray2Float(nan1);
		
		byte[] head_id = int2ByteArray(id);
		byte[] head_pan = int2ByteArray(common_pan_id);
		byte[] source = new byte[4];
		source[0] = head_pan[2];
		source[1] = head_pan[3];
		source[2] = head_id[2];
		source[3] = head_id[3];
		data_to_sink[1] = byteArray2Float(source);
		
		for (byte i = 0; i < mode.length; i++){
			if (mode[i] != (float) 0x7fc00001 && mode[i] != (float)0x7fc00002) {
				data_to_sink[i+2] = mode[i];
			}
			else {
				data_to_sink[i+2] = byteArray2Float(nan0);
			}
		}
		
		
		if (mean_freq != (float) 0x7fc00001 && mean_freq != (float) 0x7fc00002) {
			data_to_sink[mode.length + 2] = mean_freq;
		}
		else {
			data_to_sink[mode.length + 2] = byteArray2Float(nan0);
		}
//		byte[] nan2 = new byte[4];
//		nan2[0] = (byte) 0x7f;
//		nan2[1] = (byte) 0xc0; 
//		nan2[2] =(byte) 0x00;
//		nan2[3] = (byte) 0x02;
//		data_to_sink[mode.length + 2] = byteArray2Float(nan2);
				
		return data_to_sink;
	}
	
	/** 
	 * Melakukan pengecekan terhadap data yang sudah diterima dari worker. 
	 * 
	 * @param all_data variable status yang menandakan bahwa data sudah diterima dari worker
	 * @param num_worker jumlah worker yang aktif
	 * @return True jika semua data sudah diterima dengan benar 
	 */
	public boolean isAllReceived(int all_data, byte num_worker) {
		int check = 0;
		for (byte i = 0; i < num_worker; i++) {	
			check |= 1 << i;
		}
		
		if((all_data & check) == check) {
			return true;
		}
		else {
			return false;
		}
	}
	
	/** 
	 * Melakukan reset terhadap status data sudah dikirimkan ke worker  
	 */
	public void resetWorkerStatus() {
		for (byte i = 0; i < num_worker; i++) {	
			worker_receive[i] = ~SEND_SUCCESS;
		}
	}
	
	/** 
	 * Melakukan pengecekan terhadap data yang sudah dikirimkan ke worker. 
	 * 
	 * @return True jika semua data sudah berhasil dikirimkan
	 */
	public boolean checkWorkerStatus() {
		boolean isOK = true;
		for (byte i = 0; i < num_worker; i++) {	
			if (worker_receive[i] != SEND_SUCCESS) {
				isOK = false;
			}
		}
		return isOK;
	}
	
	/** 
	 * Menghapus seluruh data mode_shape dan frekuensi natural lalu menggantikannya dengan nilai NaN 
	 * 
	 * @param nat_freq array yang berisi frekuensi natural
	 * @param mode_shape array yang berisi mode_shape
	 * @param mean_nat_freq variable yang menyimpan nilai frekuensi natural rata-rata
	 */
	private void resetValue(float[] nat_freq, float []mode_shape, float mean_nat_freq) {
		
		byte[] nan0 = new byte[4];
		nan0[0] = (byte) 0x7f;
		nan0[1] = (byte) 0xc0; 
		nan0[2] =(byte) 0x01;
		nan0[3] = (byte) 0x1;
				
		for (int i = 0; i < max_num_worker; i
				++) {
			nat_freq[i] = byteArray2Float(nan0);
			mode_shape[i] = byteArray2Float(nan0);
		}		
		
		mean_nat_freq = byteArray2Float(nan0);
	}
	
	
	public static void main(String[] args) throws Exception {
		
		/* Melakukan inisialisasi awal yang tidak dapat dilakukan dalam fungsi */
		ClusterHead head = new ClusterHead();
		head.headInit();
		head.received_data = null;
		NativeTimer timer = NativeTimer.getInstance(0);
		timer = head.timerInit(timer);
		final Shuttle shuttle = Shuttle.getInstance();
		final AT86RF231 radio = RadioInit.initRadio();
		final Button button = shuttle.getButton();
		LED red = shuttle.getLED(Shuttle.LED_RED);
		LED orange = shuttle.getLED(Shuttle.LED_ORANGE);
		red.open();
		orange.open();
		button.open();	
		head.status = 0x00;
		long timer_start = 0;
		long timer_end = 0;
		
		/* Infinite Loop */
		while (true) {
			/* State sleep, hanya radio yang aktif */
			if (head.status == 0x00) {		
				/* Menerima perintah jika ada kiriman dari coordinator */
				head.receiveData(radio, red, head.common_channel, head.coordinator_pan_id, head.id4coord);
				/* jika perintah yang diberikan adalah wake */
				if (head.received_data != null) {
					System.out.println(Arrays.toString(head.received_data.getPayload()));
					if (byteArray2Int(head.received_data.getPayload()) == 0xBA17) {
//					if(button.isPressed()) {	
						head.status = 0x01;
						timer_start = head.counter;
						head.resetWorkerStatus();
						System.out.println("me wakey");
						orange.off();
					}	
				}	
					
				/* jika tidak terdapat perintah untuk bangun */
				else {
					System.out.println("slp");
					orange.on();
					/* set cpu dalam mode standby selama 6 detik, jika ada kiriman radio maka sleep akan ter-interrupt */
					CPUHelper.setPowerState(CPUConstants.V_POWER_STATE_STANDBY, 6000);
					System.out.println("me see");
				}
			}
			/* state send wake */
			else if (head.status == 0x01) {		
				/* memberikan pesan wake ke worker node */
				head.message = 0xBA17;	
				if (timer_end - timer_start < 15000 && !head.checkWorkerStatus()) {
					for (byte count = 0; count < head.num_worker; count++) {
						head.sendData(radio, red, int2ByteArray(head.message), head.common_channel, head.common_pan_id, head.id, head.worker_id[count]);					
						head.worker_receive[count] = head.sendData(radio, red, int2ByteArray(head.message), head.common_channel, head.common_pan_id, head.id, head.worker_id[count]);					
					}
				}
				else {
					head.status = 0x02;
					head.all_data = 0;
					head.resetWorkerStatus();
					timer_start = head.counter;

				}
				timer_end = head.counter;
			}
			/* state receive data */
			else if (head.status == 0x02) {
				System.out.println("waiting!");	
				
//				head.message = 0xAB17;
				
				/* menerima data dan menyimpannya dalam array */
				if (!head.isAllReceived(head.all_data, head.num_worker) && (timer_end - timer_start < 60000)) {	
					head.receiveData(radio, red, head.common_channel, head.common_pan_id, head.id);
					if (head.received_data != null) {				
						head.all_data = head.saveData(head.received_data, head.max_amp, head.nat_freq, head.worker_id, head.num_worker, head.all_data);
						System.out.println("received!" + Arrays.toString(head.received_data.getPayload()));
//						head.sendData(radio, red, int2ByteArray(head.message), head.common_channel, head.common_pan_id, head.id, (int)head.received_data.getSrcAddr());
					}
				}
				
				/* melakukan kalkulasi frekuensi natural rata-rata dan mode shape jika seluruh data sudah diterima atau lebih dari timeout */
				else {
					String str = new String();
					String str2 = new String();
					str = Arrays.toString(head.max_amp);
					str2 = Arrays.toString(head.nat_freq);
					String hex_addr = Arrays.toString(head.worker_id);
					System.out.println(hex_addr + " " + str + " " + str2);
	
					//calculate modeshape, meanfreq, meanmaxamp
					head.mean_nat_freq = head.meanNatFreq(head.nat_freq);
					head.calculateModeShape(head.nat_freq, head.max_amp, head.mode_shape);
					head.coordinator_received = ~head.SEND_SUCCESS;
					timer_start = head.counter;
					head.status = 0x03;
				}
				
				timer_end = head.counter;
			}
			/* state send data ke coordinator (sink)*/
			else if (head.status == 0x03) {	
				
				//dummy data
//				head.mode_shape[0] = 1;
//				head.mode_shape[1] = 2;
//				head.mode_shape[2] = 3;
//				head.mode_shape[3] = 4;
//				head.mode_shape[4] = 5;
//				head.mean_nat_freq = 3;
				
				/* menyiapkan data untuk dikirimkan*/
				float []data_to_sink = head.prepareDataToSink(head.mode_shape, head.mean_nat_freq);			
				
				/* kembali ke state sleep jika data sudah diterima coordinator atau lebih dari timeout */
				if (timer_end - timer_start > 15000 || head.coordinator_received == head.SEND_SUCCESS) {
					head.status = 0x00;
					System.out.println("me sleepy");
					head.resetValue(head.nat_freq, head.mode_shape, head.mean_nat_freq);
					head.coordinator_received = ~head.SEND_SUCCESS;
					//dummy
//					timer_start = head.counter;
//					Thread.sleep(500);
				}
				
				/* mencoba untuk mengirimkan data ke sink */
				else {	
					System.out.println(Arrays.toString(data_to_sink));
					head.coordinator_received = head.sendData(radio, red, FloatArray2ByteArray(data_to_sink), head.common_channel, head.coordinator_pan_id, head.id, head.coordinator_id);										
				}
				
				timer_end = head.counter;
			}
		}
	}
}

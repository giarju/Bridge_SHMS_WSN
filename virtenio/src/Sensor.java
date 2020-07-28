/**
 * 
 */

/**
 * @author Gian Arjuna
 *
 */
 

import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.led.LED;
import com.virtenio.driver.timer.NativeTimer;
import com.virtenio.driver.timer.Timer;
import com.virtenio.preon32.shuttle.Shuttle;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.vm.event.AsyncEvent;
import com.virtenio.vm.event.AsyncEventHandler;
import com.virtenio.vm.event.AsyncEvents;
import com.virtenio.driver.button.Button;
import com.virtenio.driver.cpu.CPU;
import com.virtenio.driver.cpu.CPUException;
import com.virtenio.driver.cpu.NativeCPU;
import com.virtenio.preon32.cpu.CPUConstants;
import com.virtenio.preon32.cpu.CPUHelper;

public class Sensor extends Nodes 
{
	protected float max_amp;
	protected float natural_freq;
	protected long counter;
	protected Frame received_data = null;
	protected int SEND_SUCCESS = 0;  //AT86RF231.TRAC_SUCCESS = 0 
	
	public NativeTimer timerInit(NativeTimer timer){
		final int eventId = 7;
		final int delta = 1;

		
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
		timer = NativeTimer.getInstance(0);
		try {
			timer.open(Timer.MODE_CONTINUOUS, eventId, delta, Timer.UNIT_MILLIS);
			timer.enable();
		} catch (Exception e) {
			e.printStackTrace();;
		}
		
		return (timer);
	}
	
	public NativeTimer timerClose(NativeTimer timer) {
		AsyncEvents events = AsyncEvents.getAsyncEvents();		
		events.clear();
		try {
			timer.close();
			System.out.println("Timer(close)");
		} catch (Exception e) {
			System.out.println("Timer error");
		}
		
		return (timer);
	}
	
	public Frame frameSetup(Frame frame, byte []values, int source_pan_id, int source_id, int dest_pan_id, int dest_id)
	{
		frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
				| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
		frame.setSrcAddr(source_id);
		frame.setSrcPanId(source_pan_id);
		frame.setDestAddr(dest_id);
		frame.setDestPanId(dest_pan_id);
		frame.setPayload(values);
		
		return frame;
	}
	
	public int sendData(AT86RF231 radio, LED yellow, byte []values, int channel, int pan_id, int source_id, int dest_id) throws Exception {
		 
		radio.setChannel(channel);
		radio.setPANId(pan_id);
		radio.setShortAddress(source_id);

		long start_time = counter;
		long last_time = 0;
		int isOK = -99;
		while (isOK != AT86RF231.TRAC_SUCCESS && (last_time - start_time < 100)) {
			try {
				Frame frame = null;
				frame = frameSetup(frame, values, pan_id, source_id, pan_id, dest_id);
				radio.setState(AT86RF231.STATE_TX_ARET_ON);
				yellow.on();
				last_time = counter;
				isOK = radio.transmitFrame0(frame);
//				System.out.println(values);
			} catch (Exception e) {
				e.printStackTrace();;
			}
		}
//		radio.close();
		yellow.off(); 
		return (isOK);
	}
	
	public void receiveData(AT86RF231 radio, LED yellow, int channel, int pan_id, int source_id) throws Exception {

		radio.setChannel(channel);
		radio.setPANId(pan_id);
		radio.setShortAddress(source_id);

//		LED orange = shuttle.getLED(Shuttle.LED_ORANGE);
//		orange.open();

		Frame frame = null;
		boolean success = false;
		try {
			frame = new Frame();
			radio.setState(AT86RF231.STATE_RX_AACK_ON);
			yellow.on();
			success = radio.waitForFrame(frame, 1000);
//			Misc.LedBlinker(orange, 100, false);
		} catch (Exception e) {
		}

		if (frame != null && success) {
			received_data = frame;
								
//			byte[] dg = frame.getPayload();
//			String str = new String(dg, 0, dg.length);
//			
//		    int addr = (int) frame.getSrcAddr();
//			String hex_addr = Integer.toHexString(addr);						
//			System.out.println(hex_addr + " : " + str);
		}
		else {
			received_data = null;
		}
		yellow.off();
//		orange = null;
	}
	
	public void receiveDataWait(int channel, int pan_id, int source_id) throws Exception {
		Shuttle shuttle = Shuttle.getInstance();

		AT86RF231 radio = RadioInit.initRadio();
		radio.setChannel(channel);
		radio.setPANId(pan_id);
		radio.setShortAddress(source_id);

		LED orange = shuttle.getLED(Shuttle.LED_ORANGE);
		orange.open();

		Frame frame = null;
		boolean success = false;
		try {
			frame = new Frame();
			radio.setState(AT86RF231.STATE_RX_AACK_ON);
			radio.waitForFrame(frame);
			Misc.LedBlinker(orange, 100, false);
		} catch (Exception e) {
		}

		if (frame != null && success) {
			received_data = frame;
		}
		else {
			received_data = null;
		}
		
	}


}

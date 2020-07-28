import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 
 */

/**
 * @author Gian Arjuna
 *
 */
public class Nodes {
	/* database and convention
	 * 
	 *  id : hex int 
	 *  	sensor : Axxxh
	 *        head : AFxxh
	 *  	sink : Bxxxh
	 *  	activator : Cxxxh
	 *  	
	 *  status : 
	 *  	sleep : 0x00
	 *  	idle : 0x01
	 *  	busy : 0x02
	 *  	ready : 0x03
	 *  
	 *  channel : 1-16
	 *  
	 *  pan id : 0x(A-E)xxx
	 *  
	 *  message :
	 *  	getid : 0x01
	 *  	wakeup : 0xBA17
	 *  	sleep : 0xAB17
	 *  
	 *  time :
	 *  xxxxx
	 *  
	 *  date :
	 *  xxxx
	 *  
	 */
	
	protected int id;
//	protected String node_type;
//	protected byte condition;
	protected byte status;
	protected int message;
	protected String time;
	protected String date;
	
	protected int common_channel;
	protected int common_pan_id;
	protected int coordinator_id = 0xAF00;
	protected int coordinator_pan_id = 0x000B;


	
	Nodes ()
	{
		id = 0x0000;
		status = 0x00;
		common_channel = 0;
		common_pan_id = 0x0000;
		coordinator_id = 0xAF00;
		coordinator_pan_id = 0x000B;
		time = "";
		date = "";
	}
	
	
	public void setupNode (int _id, int _common_channel, int _common_pan_id, byte _status, String _time, String _date)
	{
		id = _id;
		common_channel = _common_channel;
		common_pan_id = _common_pan_id;
		status = _status;
		time = _time;
		date = _date;
	}

	public void setupNode (int _id, int _common_channel, int _common_pan_id)
	{
		id = _id;
		common_channel = _common_channel;
		common_pan_id = _common_pan_id;
		status = 0x01;
	}
	
	public void changePanID(int _pan_id)
	{
		common_pan_id = _pan_id;
	}
	
	public void changeChannel(int _channel)
	{
		common_channel = _channel;
	}
//	public String updateCondition()
//	{
//		
//	}
	
	public void updateStatus(byte _status)
	{
		status = _status;
	}
	
	public void syncTime(String _time, String _date)
	{
		time = _time;
		date = _date;
	}
	
	public String getTime()
	{
		return time;
	}
	
	public String getDate()
	{
		return date;
	}
	
	public static byte[] FloatArray2ByteArray(float[] values){
	    ByteBuffer buffer = ByteBuffer.allocate(4 * values.length);

	    for (float value : values){
	        buffer.putFloat(value);
	    }

	    return buffer.array();
	}
	
	public static byte [] float2ByteArray (float value){  
		
	     return ByteBuffer.allocate(4).putFloat(value).array();
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
	
	public static byte[] int2ByteArray(int value) {
	     return  ByteBuffer.allocate(4).putInt(value).array();
	}
	
	public static int byteArray2Int(byte[] array) {
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
	     return ByteBuffer.wrap(temp).getInt();
	}
	
	public static int byteArray2IntFlash(byte[] array) {
	     return ByteBuffer.wrap(array).getInt();
	}
	
}

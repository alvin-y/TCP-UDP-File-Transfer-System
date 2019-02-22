import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.io.FileInputStream;

public class Sender{
	//Command line variables
	private static String hostAddr;
	private static int rcvPort;
	private static int sendPort;
	private static String fileName;
	private static int timeout;
	
	private static InetAddress IP;
	
	public static void main(String args[]) throws IOException{
		//check params
		if (args.length != 5) {
			System.out.println("Not enough parameters");
			return;
		}
		//handle arguments
		hostAddr = args[0];
		IP = InetAddress.getByName(hostAddr);
		rcvPort = Integer.parseInt(args[1]);
		sendPort = Integer.parseInt(args[2]);
		fileName = args[3];
		timeout = (int)(Math.ceil(Double.parseDouble(args[4]) / 1000)); //microseconds to milliseconds
		
		//Create the port to send data over
		DatagramSocket socket = new DatagramSocket(sendPort);
		
	
		//Set up file
		File file = new File(fileName);
		
		InputStream inFile = null;
		try {
			inFile = new FileInputStream(file);
		}catch(FileNotFoundException e) {
			System.err.println("File not found");
			socket.close();
			return;
		}
		byte[] fileBytes = new byte[(int)file.length()]; //fileBytes holds all of the original file
		inFile.read(fileBytes);
						
		int seqNum = 0;
		int ackSeq = 0; //set default to 0
		boolean isLastMessage = false;
		
		//Use to check if transfer is valid
		byte[] seqNumByte = new byte[4];
		DatagramPacket initRequest = new DatagramPacket(seqNumByte, seqNumByte.length);
		socket.receive(initRequest);
		//int state = ByteBuffer.wrap(seqNumByte).getInt();
		//System.out.println("ready: " + state);
		
		//start timer
		long startTime = System.currentTimeMillis();
		
		for(int i = 0; i < fileBytes.length; i = i+123) { //take chunks of 123 bytes from actual data
			seqNum++;
			//set up message and send packet
			byte[] message = new byte[128]; //message[4] holds if last message
			byte[] header = new byte[4];
			
			//check if last byte in message
			if ((i+123) >= fileBytes.length) { 
				isLastMessage = true;
				message[4] = (byte)1;
			}else {
				isLastMessage = false;
				message[4] = (byte)0;
			}
			header = ByteBuffer.allocate(4).putInt(seqNum).array();

			//put the sequence number in the header
			for(int k= 0; k < 4; k++) {
				message[k] = header[k];
			}
			
			//copy the rest of the message to remaining bytes
			if(!isLastMessage) {
				for(int j = 0; j <= 122; j++) {
					message[j+5] = fileBytes[i+j];
				}
			}else {
				for(int j = 0; j < (fileBytes.length - i); j++) {
					message[j+5] = fileBytes[i+j];
				}
			}

			//Send the completed packet
			DatagramPacket packet = new DatagramPacket(message, message.length, IP, rcvPort);
			socket.send(packet);
			System.out.println("Send message #" + seqNum);
			
			//get an ack
			boolean ackRcv = false;
			
			while(!ackRcv) {
				byte[] ack = new byte[4];
				DatagramPacket ackPacket = new DatagramPacket(ack, ack.length);
				
				//retrieve the ack packet from the receiver
				socket.setSoTimeout(timeout);
				try {
					socket.receive(ackPacket);
					ackSeq = ByteBuffer.wrap(ack).getInt();
					ackRcv = true;
				}catch(SocketTimeoutException e) {
					System.err.println("Time out.");
					ackRcv = false;
				}
				
				if((seqNum == ackSeq) && ackRcv){
					System.out.println("Successful ack");
					break;
				}else {
//					System.out.println("Received ack: " + ackSeq);
					System.out.println("Failed to receive, resend the packet #" + seqNum);
					socket.send(packet);
					ackRcv = false;
				}
			}
		}		
		//stop timer and close everything
		String end = "EOT";
		byte[] EOTMsg = end.getBytes();
		DatagramPacket EOT = new DatagramPacket(EOTMsg, EOTMsg.length, IP, rcvPort);
		socket.send(EOT);
		EOT = new DatagramPacket(EOTMsg, EOTMsg.length);
		socket.receive(EOT);
		
		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		System.out.println("The transfer took " + totalTime + " milliseconds.");
		//System.out.println("Done sending");
		
		inFile.close();
		socket.close();
	}
}

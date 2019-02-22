//imports used
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JButton;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Color;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.net.DatagramPacket;
import java.io.*;


public class Receiver extends JFrame implements ActionListener{
//public class Receiver extends JFrame{
	public static final int WIDTH = 350;
	public static final int HEIGHT = 200;
	
	private JTextField addrField;
	private JTextField ackField;
	private JTextField dataField;
	private JTextField fNameField;
	
	private JTextArea packetDisplay;
	
	private JButton reliableToggle;
	private int state = 0; //0 = reliable, 1 = unreliable
	
	public static void main(String[] args){
		//make new calculator object
		Receiver rec = new Receiver();
		//cannot manually resize
		rec.setResizable(false);
		//display
		rec.setVisible(true);
	}
	
	public Receiver() {
		setTitle("Receiver");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(WIDTH, HEIGHT);
		setLayout(new BorderLayout());
		
		//Address Port Area
		JPanel addressPanel = new JPanel();
		addressPanel.setLayout(new GridLayout(1,1));
		
		JLabel addrLabel = new JLabel("Address: ", JLabel.CENTER); //address input
		addressPanel.add(addrLabel);
		addrField = new JTextField("127.0.0.1", 30);
		addrLabel.setLabelFor(addrField);
		addressPanel.add(addrField);
			
		add(addressPanel, BorderLayout.NORTH); //add to gui
		
		
		//Labels
		JPanel labelPanel = new JPanel(new GridLayout(3,1));
		
		//Information Input Spots
		JPanel infoPanel = new JPanel(new GridLayout(3,1));

		JLabel ackLabel = new JLabel("ACK Port: ", JLabel.CENTER); //ack label
		labelPanel.add(ackLabel);
		ackField = new JTextField("4445",10);
		ackLabel.setLabelFor(ackField);
		infoPanel.add(ackField);
		
		JLabel dataLabel = new JLabel("Data Port: ", JLabel.CENTER); //data label
		labelPanel.add(dataLabel);
		dataField = new JTextField("4444",10);
		dataLabel.setLabelFor(dataField);
		infoPanel.add(dataField);
		
		JLabel fileLabel = new JLabel("Write File Name: ", JLabel.CENTER); //file name label
		labelPanel.add(fileLabel);
		fNameField = new JTextField("test2.pdf", 30);
		fileLabel.setLabelFor(fNameField);
		infoPanel.add(fNameField);
		
		//add both panels to the gui
		add(labelPanel, BorderLayout.WEST);
		add(infoPanel, BorderLayout.CENTER);
		
		
		//Buttons
		JPanel buttonPanel = new JPanel(new GridLayout(2,1));
		
		JButton transferButton = new JButton("Transfer");
		transferButton.addActionListener(this);
		buttonPanel.add(transferButton);
		
		reliableToggle = new JButton("Reliable");
		reliableToggle.addActionListener(this);
		buttonPanel.add(reliableToggle);
		
		add(buttonPanel, BorderLayout.EAST);
		
		//Return Message
		JPanel packetPanel = new JPanel();
		packetPanel.setLayout(new FlowLayout());
		packetDisplay = new JTextArea(1,25);
		packetDisplay.setBackground(Color.WHITE);
		packetDisplay.setEditable(false);
		packetPanel.add(packetDisplay);
		add(packetPanel, BorderLayout.SOUTH);
	}
	
	public void actionPerformed(ActionEvent e) {
		try {
			processAction(e);
		}catch(NullPointerException f) {
			System.err.println(f);
		}catch(IOException f2) {
			System.err.println(f2);
		}
	}
	
	public void processAction(ActionEvent e) throws IOException{
		String actionCommand = e.getActionCommand();
		if(actionCommand.equals("Reliable")) { //connect to server
			reliableToggle.setText("Unreliable");
			state = 1;
		}
		else if(actionCommand.equals("Unreliable")) { //disconnect
			reliableToggle.setText("Reliable");
			state = 0;
		}
		else if(actionCommand.equals("Transfer")) {
			transfer();
		}
	}
	
	
	public void transfer() throws IOException{
		//set up variables
		int rcvPort = Integer.parseInt(dataField.getText()); //port used to receive data from sender
		String fileName = fNameField.getText();
		String hostAddress = addrField.getText();
		InetAddress IP = InetAddress.getByName(hostAddress); //usable ip address
		int sendPort = Integer.parseInt(ackField.getText()); //port to receive acks for sender from receiver
		
		//set up socket
		DatagramSocket socket = new DatagramSocket(rcvPort); //receive packets from sender
		
		//make file and stream to file
		File file = new File(fileName); 
		FileOutputStream outFile = new FileOutputStream(file);
		
		//send first message as initializaion to start
		byte[] initMessage = new byte[4];
		initMessage = ByteBuffer.allocate(4).putInt(state).array();
		DatagramPacket initPacket = new DatagramPacket(initMessage, initMessage.length, IP, rcvPort);
		socket.send(initPacket);
				
		//start receiving/sending
		boolean lastMessage = false;
		
		int seqNum = 0;
		int lastSeqNum = 0; //last ack received.
		//drop flag
		int drop = 0;
		boolean dropping = false;

		byte[] message = new byte[128]; //message to send
		byte[] fileBytes = new byte[123]; //message without header
		
		
		while(!lastMessage) {
			if(drop == 10 && state == 1) {
				System.out.println("Dropped");
				dropping = true;
				DatagramPacket packet = new DatagramPacket(message, message.length);
				socket.receive(packet);
			}
			else if(state == 0 || dropping == false) {
				DatagramPacket packet = new DatagramPacket(message, message.length, IP, rcvPort);
				//Get the data
				socket.receive(packet);
	
				message = packet.getData();
				
				byte[] seqNumBytes = new byte[4]; //get the sequence number
				for (int i = 0; i < 4; i++) {
					seqNumBytes[i] = message[i];
				}
				seqNum = ByteBuffer.wrap(seqNumBytes).getInt(); //turn it to an int
				
				//check if its the last message; last message = -1
				if(message[4] == 1) {
					lastMessage = true;
				}
				
				packetDisplay.setText("Have received " + (seqNum+1) + " in order packets");
				
				if(seqNum == (lastSeqNum + 1)) { //check if in order
					lastSeqNum = seqNum;
					
					//copy rest of message to append to file
					for (int i = 5; i < message.length; i++) {
						fileBytes[i-5] = message[i];
					}
					outFile.write(fileBytes);
					
					//send ack
			        byte[] ackPacket = new byte[4];
			        ackPacket = ByteBuffer.allocate(4).putInt(lastSeqNum).array();
			        DatagramPacket acknowledgement = new DatagramPacket(ackPacket, ackPacket.length, IP, sendPort);
			        socket.send(acknowledgement);
			        System.out.println("Received packet: " + seqNum);
			        System.out.println("Sent ack: Sequence Number = " + lastSeqNum);
			        
				}else{ //otherwise resend the ack
			        byte[] ackPacket = new byte[4];
			        ackPacket = ByteBuffer.allocate(4).putInt(lastSeqNum).array();
			        DatagramPacket acknowledgement = new DatagramPacket(ackPacket, ackPacket.length, IP, sendPort);
			        socket.send(acknowledgement);
			        System.out.println("Resent ack: Sequence Number = " + lastSeqNum);
				}
			}
			if(dropping == true && state == 1) {
				dropping = false;
				drop = 0;
			}else {
				drop++;
			}
		}
		
		
		//End of Transmission Packets
		String end = "EOT";
		byte[] EOTMsg = end.getBytes();
		DatagramPacket EOT = new DatagramPacket(EOTMsg, EOTMsg.length);
		socket.receive(EOT);
		EOT = new DatagramPacket(EOTMsg, EOTMsg.length, IP, sendPort);
		socket.send(EOT);
		
		outFile.close();
		socket.close();
		//ackSocket.close();
	}
}

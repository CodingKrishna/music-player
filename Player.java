//Multi-threaded music player that plays a WAV file
//Producer thread reads in 10 seconds of file
//Consumer thread writes the 10 seconds to audio device
//@author  Conor Hughes

import javax.sound.sampled.*;
import java.io.*;
import java.lang.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class Player
{
    public static void main(String[] args) throws InterruptedException, UnsupportedAudioFileException, IOException, LineUnavailableException
	{
		BoundedBuffer bb = new BoundedBuffer();
		bb.readFile();
        Thread p1 = new Thread(new Producer(bb));
        Thread c1 = new Thread(new Consumer(bb));

        p1.start();
		c1.start();
		p1.join();
		c1.join();
    }
}

class BoundedBuffer extends JFrame implements KeyListener
{
	private String wavFile = "FILE_NAME_HERE"; //Enter name of wav file here eg: song.wav **Must be in same directory as player.java**
	
	private File fileIn = new File("wavFile"); 
	private AudioInputStream audioStream;
	private AudioFormat format;
	private DataLine.Info info;
	private SourceDataLine line;
	float volume = 2.0f;
	private boolean paused = false;
	private boolean muted = false;
	private boolean dataAvailable = false;
	private boolean roomAvailable = true;
	private boolean alive = true;
	private int buffersize = 282240;
	private int oneSecond = 28224;
	private int nextIn;
	private int nextOut;
	private int bytesRead;
	private int bytesWritten;
	private byte[] audioChunk;
	private JLabel l1;
	private JLabel l2;
	private JLabel l3;
	
	public void readFile() throws UnsupportedAudioFileException, IOException,  LineUnavailableException //reads in file, format and info
	{
		audioStream = AudioSystem.getAudioInputStream(fileIn);
		format = audioStream.getFormat();
		info = new DataLine.Info(SourceDataLine.class, format);
		line = (SourceDataLine) AudioSystem.getLine(info);
		line.open(format);
		line.start();
	}
		
	public BoundedBuffer() //GUI from player JFrame
	{ 
		setTitle("Music Player");
		Panel p = new Panel();
		l1 = new JLabel ("NOW PLAYING: " + fileIn, SwingConstants.CENTER);
		l2 = new JLabel("<html><br/>MUSIC PLAYER CONTROLS<br/><br/> Stop: x <br/>Higher Volume: q <br/>Lower Volume: a <br/>Pause: p <br/>Resume: r <br/>Mute: m <br/>Unmute: u<br/><br/></html>", SwingConstants.CENTER);
		l3 = new JLabel("CURRENT VOLUME: " + volume, SwingConstants.CENTER);
		p.add(l1);
		p.add(l2);
		p.add(l3);
		add(p);
		addKeyListener ( this ) ; 
		setSize (280,400 );
		setVisible(true);
		addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e)
			{
				System.exit(0);
			}
		});
	}
	
	public void keyPressed ( KeyEvent e) //checks for key presses
	{
		char control = e.getKeyChar();
		
		if(control == 'x') //ends program, returns alive = false, stops threads
		{
			l2.setText("Thanks");
			l3.setText("For");
			l3.setText("Listening");
			alive = false; //drains, stops and closes line. returns alive = false to end thread
		} 
		
		if(control == 'q') //increases volume, stops at 60.0f as it is the maximum value
		{
			FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
			if(volume == 6.0f)
			{
				l3.setText("MAX VOLUME REACHED!");
			}
			else
			{
				volume +=1.0f;
				gainControl.setValue(volume);
				l3.setText("CURRENT VOLUME: " + volume);
			}
		}
		
		if(control == 'a') //decreases volume, stops at -80.0f as it is the minimum value
		{
			FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
			
			if(volume == -80.0f)
			{
				l3.setText("MINIMUM VOLUME REACHED!"); //update label with current volume
			}
			else
			{
				volume -=1.0f;
				gainControl.setValue(volume);
				l3.setText("CURRENT VOLUME: " + volume); //update label with current volume
			}
		}
		
		if(control == 'm') //mutes line until 'u' is entered
		{
			BooleanControl muteControl = (BooleanControl) line.getControl(BooleanControl.Type.MUTE);
			muteControl.setValue(true);
			muted = true;
			l3.setText("SONG MUTED!");
		}
		
		if(control == 'u') //unmutes line
		{
			BooleanControl muteControl = (BooleanControl) line.getControl(BooleanControl.Type.MUTE);
			muteControl.setValue(false);
			muted = false;
			l3.setText("CURRENT VOLUME: " + volume); //update label with current volume
		}
		
		if(control == 'p') //pauses line, until 'r' is entered, make boolean paused = true
		{
			line.stop();
			paused = true;
			l3.setText("SONG PAUSED!");
		}
		
		if(control == 'r') //resumes line, make boolean paused = false
		{
			line.start();
			paused = false;
			if(muted == true)
			{
				l3.setText("SONG MUTED!");
			}
			else
			{
				l3.setText("CURRENT VOLUME: " + volume); //update label with current volume
			}
		}
	}
	public void keyTyped ( KeyEvent e ){}  
	public void keyReleased ( KeyEvent e ){}  
	
	public static void main (String[]args ) //creates GUI for player
	{  
		new BoundedBuffer(); 
	} 
	
	public synchronized boolean insertChunk()
	{
		while(!roomAvailable && dataAvailable)
		{
			try
			{
				wait();//waits until all of data is written (played)
			}
			catch (InterruptedException e) { }
		}

		try
		{
			audioChunk = new byte[buffersize];
		
			while(nextIn != buffersize && alive) 
			{
				if((bytesRead = audioStream.read(audioChunk, nextIn, oneSecond)) == -1 && paused == false)//reads in 10 one second chunks, if nothing is read, terminate thread
				{
					l2.setText("Thanks For Listening");
					line.drain();
					line.stop();
					line.close();
					alive = false; //drains, stops and closes line. returns alive = false to end thread
				}
				nextIn += oneSecond;
			}
			nextIn = 0;
		}
		catch (IOException e) { }
			
		dataAvailable = true; //audio data is available to be written
		roomAvailable = false; //no room for audio data to be read in
		notifyAll(); //notify threads
			
		return alive; // alive, thread will loop if true
	}
	
	public synchronized boolean removeChunk()
	{
		while(roomAvailable && !dataAvailable)
		{
			try
			{
				wait();//waits until data is available to be written
			}
			catch (InterruptedException e) { }
		}
		

		while(nextOut != buffersize && alive)
		{
			if(paused == false)
			{
				bytesWritten += line.write(audioChunk, nextOut, bytesRead); //writes 10 seconds to audio (plays)
				nextOut += oneSecond;
			}
		}
		nextOut = 0;
			
		dataAvailable = false; //audio data is not available to be written
		roomAvailable = true; //room for audio data to be read in is available
		notifyAll(); //notify threads
		
		return alive; // alive, thread will loop if true
	}
}

class Producer implements Runnable //producer thread read audio data to be written
{

    private BoundedBuffer BoundedBuffer;
	private boolean alive = true;

    public Producer(BoundedBuffer bb) 
	{
        BoundedBuffer = bb;
    }

    public void run() //loops until alive = false is return (when 'x' is entered or song ends)
	{
		while(alive) 
		{
			alive = BoundedBuffer.insertChunk();
		}
		System.exit(0);
    }
}

class Consumer extends Thread //consumer thread that writes audio to device
{
	
    private BoundedBuffer BoundedBuffer;
	private boolean alive = true;

    public Consumer(BoundedBuffer bb) 
	{
        BoundedBuffer = bb;
    }

    public void run() 
	{	
		while(alive) //loops until alive = false is return (when 'x' is entered or song ends)
		{
			alive = BoundedBuffer.removeChunk();
		}
    }
}
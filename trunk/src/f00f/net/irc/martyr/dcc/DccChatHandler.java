package f00f.net.irc.martyr.dcc;

import java.io.IOException;
import java.net.Socket;

/**
 * @see f00f.net.irc.martyr.dcc.AutoDccSetup
 */
public class DccChatHandler extends Thread
{
	private Socket socket_;
	
	/**
	 * A call to this method means that a connection has been established
	 * and chat should commence.  The default implementation closes the
	 * stream immediatly.  A better implementation would read a line
	 * from the input stream and send output to the output stream.
	 * The chat protocol is raw text.
     *
     * @param socket Socket over which chat will be handled
     * @throws IOException if connection could not be established
	 */
	protected void handleDccChat( Socket socket )
		throws IOException
	{
		socket.close();
	}
	
	
	public DccChatHandler( Socket socket )
	{
		this.socket_ = socket;
	}

	public DccChatHandler()
	{
		this(null);	
	}

	public void setSocket( Socket sock )
	{
		this.socket_ = sock;
	}

	public void run()
	{
		try
		{
			handleDccChat( socket_ );
		}
		catch( IOException ioe )
		{
			ioe.printStackTrace();
		}
	}
}


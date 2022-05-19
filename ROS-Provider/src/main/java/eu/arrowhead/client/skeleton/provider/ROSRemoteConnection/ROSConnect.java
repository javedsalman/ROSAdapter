package eu.arrowhead.client.skeleton.provider.ROSRemoteConnection;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import com.jcraft.jsch.*;

public class ROSConnect {
	
	String host, username, password;
	int port;
	
	public ROSConnect(String host, String username, int port, String password) {
		this.host=host;
		this.port= port;
		this.username= username;
		this.password= password;
	}

	public Session connect() throws JSchException {
	
			    JSch jsch=new JSch();
			    Session session=jsch.getSession(username, host, port);
			    session.setPassword(password);
			    session.setConfig("StrictHostKeyChecking", "no");
			    session.connect(30000);
			    
			    return session;	       
	}
	
	public void execute(String command) throws JSchException, IOException {
		 Session session= connect();
		 Channel channel=session.openChannel("shell");  	 
		 String data = command+"\n";
		    channel.setInputStream(new ByteArrayInputStream(data.getBytes()));
		    channel.connect(3*1000);
		    channel.setOutputStream(System.out);
		    
		    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		    channel.setOutputStream(baos);
		    String a = new String(baos.toByteArray());
		    System.out.println(a);
		   // PrintStream out = new PrintStream(channel.getOutputStream(), true); 
		    //	  out.println(data);
		    	  
		    //BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
		    //System.out.println(reader.readLine());
		    
		    //return channel;
	}
	

	public String readChannelOutput(Channel channel) throws IOException{

		byte[] buffer = new byte[1024];
        StringBuilder strBuilder = new StringBuilder();
        InputStream in = channel.getInputStream();
        String line = "";
        while (true){
            while (in.available() > 0) {
                int i = in.read(buffer, 0, 1024);
                if (i < 0) {
                    break;
                }
                strBuilder.append(new String(buffer, 0, i));
                System.out.println(line);
            }

            if(line.contains("logout")){
                break;
            }

            if (channel.isClosed()){
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (Exception ee){}
        }

        return strBuilder.toString();   

	}

	public void close() throws JSchException {
		connect().disconnect();
		System.out.println("session closed");
		
	}
}
	 
	

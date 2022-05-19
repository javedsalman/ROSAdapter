package eu.arrowhead.client.skeleton.provider.controller;

import eu.arrowhead.client.skeleton.provider.ROSRemoteConnection.ROSConnect;
import eu.arrowhead.client.skeleton.provider.ROSRemoteConnection.SSHManager;
import eu.arrowhead.common.Defaults;
import org.jose4j.json.internal.json_simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import com.jcraft.jsch.JSchException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@CrossOrigin(maxAge = Defaults.CORS_MAX_AGE, allowCredentials = Defaults.CORS_ALLOW_CREDENTIALS,
		allowedHeaders = { HttpHeaders.ORIGIN, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, HttpHeaders.AUTHORIZATION }
)

@RestController
public class ProviderController {
	//=================================================================================================
	// members


	@Value("${nvdia.address}")
	public String host;

	@Value("${nvdia.port}")
	public int port;
	
	@Value("${nvdia.username}")
	public String username;
	
	@Value("${nvdia.password}")
	public String password;
	
	public ProviderController() throws IOException, ParseException {
	}
	
	
	@GetMapping(path = "/rosservice")
	@ResponseBody
	public String getservicelist() throws IOException, ParseException, JSchException {
		SSHManager sh= new SSHManager(host, username, port, password);
		List<String> commands = new ArrayList<String>();
	    commands.add("rosservice list");

	    String output= sh.executeCommands(commands);
	    output= output.substring(0, output.indexOf("robot@robot-desktop"));
	    sh.close();
		return output;
	}
	
	@PostMapping(path = "/{uri}")
	@ResponseBody
	public void executeROSservice(@PathVariable(name ="uri") String URI) throws IOException, ParseException, JSchException {
		SSHManager sh= new SSHManager(host, username, port, password);
		List<String> commands = new ArrayList<String>();
	    commands.add("rosservice call "+URI);

	    String output= sh.executeCommands(commands);
	    output= output.substring(0, output.indexOf("robot@robot-desktop"));
	    sh.close();
	}
	
	
	@PostMapping(path = "/pickup")
	@ResponseBody
	public void pickupROSservice() throws IOException, ParseException, JSchException {
		SSHManager sh= new SSHManager(host, username, port, password);
		List<String> commands = new ArrayList<String>();
	    commands.add("rosservice call /pickup");

	    String output= sh.executeCommands(commands);
	    output= output.substring(0, output.indexOf("robot@robot-desktop"));
	    sh.close();
	}


	@RequestMapping("*")
	@ResponseBody
	public String fallbackMethod(){
		return "fallback method";
	}
}

package eu.arrowhead.client.skeleton.provider;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.text.DecimalFormat;
import java.util.*;

import eu.arrowhead.common.CommonConstants;
import eu.arrowhead.common.dto.shared.ServiceRegistryRequestDTO;
import eu.arrowhead.common.dto.shared.ServiceSecurityType;
import eu.arrowhead.common.dto.shared.SystemRequestDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.mysql.cj.Session;

import ai.aitia.arrowhead.application.library.ArrowheadService;
import ai.aitia.arrowhead.application.library.config.ApplicationInitListener;
import ai.aitia.arrowhead.application.library.util.ApplicationCommonConstants;
import eu.arrowhead.client.skeleton.provider.ROSRemoteConnection.ROSConnect;
import eu.arrowhead.client.skeleton.provider.ROSRemoteConnection.SSHManager;
import eu.arrowhead.client.skeleton.provider.security.ProviderSecurityConfig;
import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.core.CoreSystem;
import eu.arrowhead.common.exception.ArrowheadException;

@Component
public class ProviderApplicationInitListener extends ApplicationInitListener {
	
	//=================================================================================================
	// members
	
	@Autowired
	private ArrowheadService arrowheadService;
	
	@Autowired
	private ProviderSecurityConfig providerSecurityConfig;
	
	@Value(ApplicationCommonConstants.$TOKEN_SECURITY_FILTER_ENABLED_WD)
	private boolean tokenSecurityFilterEnabled;
	
	@Value(CommonConstants.$SERVER_SSL_ENABLED_WD)
	private boolean sslEnabled;

	@Value(ApplicationCommonConstants.$APPLICATION_SYSTEM_NAME)
	private String mySystemName;

	@Value(ApplicationCommonConstants.$APPLICATION_SERVER_ADDRESS_WD)
	private String mySystemAddress;

	@Value(ApplicationCommonConstants.$APPLICATION_SERVER_PORT_WD)
	private int mySystemPort;
	
	@Value("${nvdia.address}")
	public String host;

	@Value("${nvdia.port}")
	public int port;
	
	@Value("${nvdia.username}")
	public String username;
	
	@Value("${nvdia.password}")
	public String password;
	

	private final Logger logger = LogManager.getLogger(ProviderApplicationInitListener.class);
	
	int size=0;
	List<String> services= new ArrayList<String>();
	
	//=================================================================================================
	// methods
	//-------------------------------------------------------------------------------------------------
	@Override
	protected void customInit(final ContextRefreshedEvent event) {
		
		//Checking the availability of necessary core systems
		checkCoreSystemReachability(CoreSystem.SERVICEREGISTRY);
		if (tokenSecurityFilterEnabled) {
			checkCoreSystemReachability(CoreSystem.AUTHORIZATION);
			//Initialize Arrowhead Context
			arrowheadService.updateCoreServiceURIs(CoreSystem.AUTHORIZATION);
		}

		setTokenSecurityFilter();
		
		//CONNECT ROS & FETCH SERVICE LIST------------------------------
		SSHManager sh= new SSHManager(host, username, port, password);
		List<String> commands = new ArrayList<String>();
	    commands.add("rosservice list");

	    String output= sh.executeCommands(commands);
	    String serviceList=output.substring(0, output.indexOf("robot@robot-desktop"));
	    
	    System.out.println("Displaying output...\n");
	    System.out.println(serviceList);
	    
	    Scanner scanner = new Scanner(serviceList);
	    while (scanner.hasNextLine()) {
	      String line = scanner.nextLine();
	      //System.out.println(line);
	      services.add(line); 
	    }
	    
	    scanner.close();
	    sh.close();
	    
	    size=services.size();
	    
	    ServiceRegistryRequestDTO serviceRequest1 = createServiceRegistryRequest("get-rosservice-list" ,  "/rosservice", HttpMethod.GET);
	    arrowheadService.forceRegisterServiceToServiceRegistry(serviceRequest1);
	    logger.info("Registered Service with SERVICE DEFINITION: get-rosservice-list & URI: /rosservice");
	    
	    ServiceRegistryRequestDTO serviceRequest2 = createServiceRegistryRequest("ROS-Pickup" , "/pickup", HttpMethod.POST);
    	arrowheadService.forceRegisterServiceToServiceRegistry(serviceRequest2);
    	logger.info("Registered Service with SERVICE DEFINITION: ROS-Pickup & URI: /pickup");
    	
	    for(int i=0; i<size;i++) {
	    	ServiceRegistryRequestDTO serviceRequest = createServiceRegistryRequest("execute-rosservice" , services.get(i) , HttpMethod.POST);
	    	arrowheadService.forceRegisterServiceToServiceRegistry(serviceRequest);
	    	logger.info("Registered Service with SERVICE DEFINITION: execute-rosservice & URI: "+services.get(i));
	    }
	}
	
	//-------------------------------------------------------------------------------------------------
	@Override
	public void customDestroy() {
		//TODO: implement here any custom behavior on application shout down
		logger.info("Unregistering services!!");
		arrowheadService.unregisterServiceFromServiceRegistry("get-rosservice-list",  "/rosservice");
		for(int i=0; i<size;i++) {
			arrowheadService.unregisterServiceFromServiceRegistry("execute-rosservice" , services.get(i));
	    }
	}

	//=================================================================================================
	// assistant methods

	//-------------------------------------------------------------------------------------------------

	//-------------------------------------------------------------------------------------------------
	private ServiceRegistryRequestDTO createServiceRegistryRequest(final String serviceDefinition, final String serviceUri, final HttpMethod httpMethod) {
		final ServiceRegistryRequestDTO serviceRegistryRequest = new ServiceRegistryRequestDTO();
		serviceRegistryRequest.setServiceDefinition(serviceDefinition);
		final SystemRequestDTO systemRequest = new SystemRequestDTO();
		systemRequest.setSystemName(mySystemName);
		systemRequest.setAddress(mySystemAddress);
		systemRequest.setPort(mySystemPort);

		if (tokenSecurityFilterEnabled) {
			systemRequest.setAuthenticationInfo(Base64.getEncoder().encodeToString(arrowheadService.getMyPublicKey().getEncoded()));
			serviceRegistryRequest.setSecure(ServiceSecurityType.TOKEN.name());
			serviceRegistryRequest.setInterfaces(List.of("HTTP-SECURE-JSON"));
		} else if (sslEnabled) {
			systemRequest.setAuthenticationInfo(Base64.getEncoder().encodeToString(arrowheadService.getMyPublicKey().getEncoded()));
			serviceRegistryRequest.setSecure(ServiceSecurityType.CERTIFICATE.name());
			serviceRegistryRequest.setInterfaces(List.of("HTTPS-SECURE-JSON"));
			serviceRegistryRequest.setSecure(ServiceSecurityType.NOT_SECURE.name());
			serviceRegistryRequest.setInterfaces(List.of("HTTP-INSECURE-JSON"));
		}
		serviceRegistryRequest.setProviderSystem(systemRequest);
		serviceRegistryRequest.setServiceUri(serviceUri);
		serviceRegistryRequest.setMetadata(new HashMap<>());
		serviceRegistryRequest.getMetadata().put("http-method", httpMethod.name());
		return serviceRegistryRequest;
	}

	private void setTokenSecurityFilter() {
		if(!tokenSecurityFilterEnabled) {
			logger.info("TokenSecurityFilter in not active");
		} else {
			final PublicKey authorizationPublicKey = arrowheadService.queryAuthorizationPublicKey();
			if (authorizationPublicKey == null) {
				throw new ArrowheadException("Authorization public key is null");
			}
			
			KeyStore keystore;
			try {
				keystore = KeyStore.getInstance(sslProperties.getKeyStoreType());
				keystore.load(sslProperties.getKeyStore().getInputStream(), sslProperties.getKeyStorePassword().toCharArray());
			} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException ex) {
				throw new ArrowheadException(ex.getMessage());
			}			
			final PrivateKey providerPrivateKey = Utilities.getPrivateKey(keystore, sslProperties.getKeyPassword());

			providerSecurityConfig.getTokenSecurityFilter().setAuthorizationPublicKey(authorizationPublicKey);
			providerSecurityConfig.getTokenSecurityFilter().setMyPrivateKey(providerPrivateKey);
		}
	}
}

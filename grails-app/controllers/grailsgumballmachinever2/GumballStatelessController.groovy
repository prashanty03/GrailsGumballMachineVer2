package grailsgumballmachinever2

import gumball.GumballMachine

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class GumballStatelessController {

	def String machineSerialNum = "1234998871109"
	def GumballMachine gumballMachine
	def String secretKey = 's1s5qc9dcFqH8LU7WOIs48c5eM164kao'
	def hash 
	def String msg
	def hmac_sha256(String secretKey, String data) {
		//try {
		   Mac mac = Mac.getInstance("HmacSHA256")
		   SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256")
		   mac.init(secretKeySpec)
		   byte[] digest = mac.doFinal(data.getBytes())
		   return digest
		  //} catch (InvalidKeyException e) {
		   //throw new RuntimeException("Invalid key exception while converting to HMac SHA256")
		 //}
	   }
	
	def index() {
		
		String VCAP_SERVICES = System.getenv('VCAP_SERVICES')
		
		if (request.method == "GET") {

			// search db for gumball machine
			def gumball = Gumball.findBySerialNumber( machineSerialNum )
			if ( gumball )
			{
				// create a default machine
				gumballMachine = new GumballMachine(gumball.modelNumber, gumball.serialNumber)
				System.out.println(gumballMachine.getAbout())
			}
			else
			{
				flash.message = "Error! Gumball Machine Not Found!"
				render(view: "index")
			}

			// don't save in the session
			// session.machine = gumballMachine
			
			//get time
			flash.ts = System.currentTimeMillis().toString()
			// send machine state to client (instead)
			flash.state = gumballMachine.getCurrentState() ;
			flash.model = gumball.modelNumber ;
			flash.serial = gumball.serialNumber ;
			msg = flash.state+flash.model+flash.serial+flash.ts+secretKey
			hash = hmac_sha256(secretKey, msg)
			flash.hash = hash.encodeBase64();
			println("Message : "+msg)
			println("Hash : "+hash.encodeBase64())
			// report a message to user
			flash.message = gumballMachine.getAbout()

			// display view
			render(view: "index")

		}
		else if (request.method == "POST") {

			// dump out request object
			request.each { key, value ->
				println( "request: $key = $value")
			}

			// dump out params
			params?.each { key, value ->
				println( "params: $key = $value" )
			}
			
			// don't get machine from session
			// gumballMachine = session.machine

			// restore machine to client state (instead)
			def state = params?.state
			def modelNum = params?.model
			def serialNum = params?.serial
			def ts = params?.ts
			def hashOld = params?.hash
			
			
			def long tsl = Long.parseLong(ts)
			def long cts = System.currentTimeMillis()
			def dif = cts - tsl
			println("Difference : "+dif)
			println("State : "+state)
			def String msg = state+modelNum+serialNum+ts+secretKey
			def hashBytes = hmac_sha256(secretKey, msg)
			def hashNew = hashBytes.encodeBase64().toString()
			println("Old Hash : "+hashOld)
			println ("New Hash : "+hashNew)
			println("Message : "+msg)
			
			def invalidTS = ((dif/1000) > 120)
			def invalidHash = !(hashOld.toString().equalsIgnoreCase(hashNew))
			
			if(invalidTS || invalidHash ){
				
				flash.message = "Inconsistent State!!"

			}
			else{
				gumballMachine = new GumballMachine(modelNum, serialNum) ;
				
				gumballMachine.setCurrentState(state) ;
				
				System.out.println(gumballMachine.getAbout())
				
				if ( params?.event == "Insert Quarter" )
				{
					gumballMachine.insertCoin()
				}
				if ( params?.event == "Turn Crank" )
				{
					gumballMachine.crankHandle();
					
					if ( gumballMachine.getCurrentState().equals("gumball.CoinAcceptedState") )
					{
						def gumball = Gumball.findBySerialNumber( machineSerialNum )
						if ( gumball )
						{
							// gumball.lock() // pessimistic lock
							if ( gumball.countGumballs > 0)
								gumball.countGumballs-- ;
							gumball.save(flush: true); // default optimistic lock
						}
					}
					
				}
	
				// send machine state to client
				flash.state = gumballMachine.getCurrentState() ;
				flash.model = modelNum ;
				flash.serial = serialNum ;
				flash.ts = cts;
				def String msg1 = flash.state+flash.model+flash.serial+cts+secretKey
				def hashBytes1 = hmac_sha256(secretKey, msg1)
				def hashNew1 = hashBytes1.encodeBase64().toString()
				println("msg1 ##"+msg1)
				println("ts##"+cts)
				println("New ## "+hashNew1)
				flash.hash = hashNew1;
							
				// report a message to user
				flash.message = gumballMachine.getAbout()
			}
						// render view
			render(view: "index")
		}
		else {
			render(view: "/error")
		}
	}

}


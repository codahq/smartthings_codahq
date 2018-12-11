/**
 *  Copyright 2018 Ben Rimmasch
 *
 *  Based on a device handler by Ian Lindsay which is originally forked from here:
 *  https://github.com/littlegumSmartHome/opengarage.io-handler
 * 
 *  This code can be found at:
 *  https://github.com/codahq/opengarage.io-handler
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

preferences {
    input("devicekey", "text",     title: "Device Key", description: "Your OpenGarage.io device key")
    input("ipadd",     "text",     title: "IP address", description: "The IP address of your OpenGarage.io unit")
    input("port",      "text",     title: "Port",       description: "The port of your OpenGarage.io unit")
}

metadata {
    definition (name: "OpenGarage.io Handler", namespace: "codahq", author: "Ben Rimmasch") {
        capability "Door Control"
        capability "Garage Door Control"
        capability "Refresh"
        capability "Sensor"
    }

	tiles (scale: 2) {
        standardTile("door", "device.door", width: 6, height: 4) {
  			state "open", label: '${name}', action: "close", icon: "st.doors.garage.garage-open", backgroundColor: "#e54444", nextState: "closed"
  			state "closed", label: '${name}', action: "open", icon: "st.doors.garage.garage-closed", backgroundColor: "#79b821", nextState: "open"
		}
		standardTile("vehicle", "device.vehicle", width: 3, height: 2) {
            state "absent", label: "Absent", backgroundColor: "#e54444"
  			state "present", label: "Present", icon: "st.Transportation.transportation10", backgroundColor: "#79b821"
            state "na", label: "Open", icon: "st.Transportation.transportation13", backgroundColor: "#f0f066"
		}
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 3, height: 2) {
			state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
		}

		main("door")
		details(["door", "vehicle", "refresh"])
	}
    simulator {
        // simulator metadata
    }
}

def isDebug() {
    return false
}

def installed() {
    initialize()
}

def updated() {
	if (!state.initialized) {
    	initialize()
    }
    try {
    	if (ipadd != null && port != null && !isDebug()) {
    		device.deviceNetworkId = getHexHostAddress()
        	log.info "Device Network ID set to: ${device.deviceNetworkId}"
    	}
    	else {
    		log.warn "IP and port must be configured in the device's preferences in the IDE."
    	}
    }
    catch (Exception e) {
    	log.warn "Couldn't set Device Network ID: ${e}"
    }
    refresh()
}

def initialize() {
	log.info "Initialize triggered"
    // initialize state
    state.pollingInterval = state.pollingInterval != null ? state.pollingInterval : 5  //time in minutes
    state.garageMotionTime = state.garageMotionTime != null ? state.garageMotionTime : 25  //time in seconds
    state.doorStatus =  state.doorStatus != null ? state.doorStatus : 1 // 1 means open, 0 means closed
    state.vehicleStatus = state.vehicleStatus != null ? state.vehicleStatus : 2 // 0 means absent, 1 means present, 2 means na because door is open
    state.opening = state.opening != null ? state.opening : 0
    state.closing = state.closing != null ? state.closing : 0
    state.initialized = 1
    log.info "This device's Device Network Id must match the MAC address of the OpenGarage as seen under http://<ip>:<port>/jc. It should be all uppercase and without any octet separators (000A959D6816).  Current value i: ${device.deviceNetworkId}"
    log.info "Receiving local POST on ${device.hub?.getDataValue('localIP')}:${device.hub?.getDataValue('localSrvPortTCP')}"
  	refresh()
}

def open() {
    log.info "Executing 'open'"
    state.opening = 1
    api("getstatus", [])
}

def close() {
	log.info "Executing 'close'"
    state.closing = 1
    api("getstatus", [])
}

def refresh() {
	log.info "Executing 'refresh'"
    unschedule()
    state.updatedDate = now();
    api("getstatus", [])
    customPolling()
}

def customPolling() {
	if (!isConfigured()) {
    	log.info "Polling canceled. Please configure the device!"
        return
    }
	double timesSinceContact = (now() - state.updatedDate).abs() / 60000  //time since last update in minutes
    if (isDebug()) log.debug "Polling started.  timesSinceContact: ${timesSinceContact}"
    if (timesSinceContact > state.pollingInterval) {
    	if (isDebug()) log.debug "Polling interval exceeded"
        refresh()
    }    
    runIn(state.pollingInterval * 60, customPolling)  //time in seconds
}

def api(method, args = [], success = {}) {
    def methods = [
        "getstatus": [gdipadd: "${ipadd}", gdport:"${port}", gdpath:"/jc", gdtype: "GET"],
        "openclose": [gdipadd: "${ipadd}", gdport:"${port}", gdpath:"/cc?dkey=${devicekey}&click=1", gdtype: "GET"]
    ]

    def request = methods.getAt(method)
    if (isDebug()) log.debug "About to do doRequest with values $request.gdipadd, $request.gdport, $request.gdpath, $request.gdtype, $success"
    return doRequest(request.gdipadd, request.gdport, request.gdpath, request.gdtype, success)
}

private doRequest(gdipadd, gdport, gdpath, gdtype, success) {
    if (!isConfigured()) {
    	log.info "Request canceled. Please configure the device!"
        return
    }

    def hexHostPort = getHexHostAddress()
    
    if (isDebug()) log.debug "Hex Host:Port is : ${hexHostPort}"   
    if (isDebug()) log.debug "DNI is ${device.deviceNetworkId}"
	if (isDebug()) log.debug "And just for good measture: ${getHostAddress()}"

	if (isDebug()) log.debug "Path is: ${gdpath}"
    
    def headers = [:] 
    headers.put("HOST", "${gdipadd}:${gdport}")
  
	try {
  		if (isDebug()) log.debug "About to create HubAction"
		def hubAction = new physicalgraph.device.HubAction(
        	[
                method: gdtype,
                path: gdpath,
                headers: headers
            ]
            ,"${hexHostPort}"
    	)
    	if (isDebug()) log.debug "After HubAction"
        return sendHubCommand(hubAction)
    }
    catch (Exception e) 
    {
        log.debug "Hit exception in doRequest: ${hubAction}"
        log.debug e
    }  
}

def parse(description) {

	def events = []
	try {
        def msg = parseLanMessage(description)

        if (isDebug()) log.debug "Start of parse: ${msg}"

        def headersAsString = msg.header // => headers as a string
        def headerMap = msg.headers      // => headers as a Map
        def body = msg.body              // => request body as a string
        def status = msg.status          // => http status code of the response
        //def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
        //def xml = msg.xml                // => any XML included in response body, as a document tree structure
        //def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)

        def slurper = new groovy.json.JsonSlurper()
        def json = slurper.parseText(msg.body)

        if (isDebug()) log.debug json       
        if (isDebug()) log.debug "Before state.doorStatus: ${state.doorStatus}"

        // open / close event
        if(json.result){
            if(state.doorStatus){
                log.info "Door open so closing"
                state.doorStatus = 0
                events << createEvent(name: "door", value: "closed", descriptionText: "${device.name} closed")
                events << createEvent(name: "vehicle", value: "na", descriptionText: "${device.name}'s vehicle updated to 'na' while door is moving", displayed: false)
                runIn(state.garageMotionTime, refresh)
            } else {
                log.info "Door closed so opening"
                state.doorStatus = 1
                events << createEvent(name: "door", value: "open", descriptionText: "${device.name} opened")
                events << createEvent(name: "vehicle", value: "na", descriptionText: "${device.name}'s vehicle updated to 'na' while door is moving", displayed: false)
            }
         }
        //status update request
        if (json.mac) {
        	if (device.deviceNetworkId != json.mac.replaceAll(":", "")) {
        		device.deviceNetworkId = json.mac.replaceAll(":", "")
                log.info "Updated Device Network ID to MAC: ${device.deviceNetworkId}"
            }
			def action = json.door ? "open" : "closed"
            if (state.doorStatus != json.door) {
                state.doorStatus = json.door
                log.info "Door is ${action}. Refreshing state"
                events << createEvent(name: "door", value: action, descriptionText: "${device.name} updated to ${action}")
            } 
            else {
                if (isDebug()) log.debug "Door state already in sync.  No change necessary"
                events << createEvent(name: "door", value: action, descriptionText: "${device.name} already synchronized", displayed: false, isStateChange: false)
            }

            if (isDebug()) log.debug "and closing/opening is ${state.closing}/${state.opening}"

            if (json.door) {	        	
                if (state.closing == 1) {
                    state.closing = 0
                    log.info "Door can close"
                    sendHubCommand(api("openclose", []))
                }
                if (state.opening == 1) {
                    state.opening = 0
                    log.info "Door is already opened"
                }
            } 
            else {
                if (state.opening == 1) {
                    state.opening = 0
                    log.info "Door can open"
                    sendHubCommand(api("openclose", []))
                }
                if (state.closing == 1) {
                    state.closing = 0
                    log.info "Door is already closed"
                }
            }
            
            def present
            switch (json.vehicle) {
            	case 0:
                	present = "absent"
                    break
                case 1:
                	present = "present"
                    break
                default:
                	present = "na"
                    break
            }
            if (state.vehicleStatus != json.vehicle) {
                state.vehicleStatus = json.vehicle
                log.info "Vehicle is ${present}. Refreshing state"
                events << createEvent(name: "vehicle", value: present, descriptionText: "${device.name}'s vehicle updated to ${present}")
            } 
            else {
                if (isDebug()) log.debug "Vehicle state already in sync.  No change necessary"
                events << createEvent(name: "vehicle", value: present, descriptionText: "${device.name}'s vehicle already synchronized", displayed: false, isStateChange: false)
            }

        }
        if (json.refresh) {
        	log.info "Update needed.  Doing refresh!"
            sendHubCommand(api("getstatus", []))
        }

        if (isDebug()) log.debug "after state.doorStatus: ${state.doorStatus}"
    }
    catch (Exception e)
    {
        log.debug "Hit exception in parse"
        log.debug e
    }

    return events
}


/*General Helper Methods*/
private isConfigured() {
	if (!state.pollingInterval || !state.garageMotionTime) {
    	initialize()
    }    
	return ipadd && port && devicekey
}


/*To Hex Helper Methods*/
private String convertIPToHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    if (isDebug()) log.debug "IP address entered is ${ipAddress} and the converted hex code is ${hex}"
    return hex.toUpperCase()
}
private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
    if (isDebug()) log.debug "Port entered is ${port} and the converted hex port is ${hexport}"
    return hexport.toUpperCase()
}


/*Out of Hex Help Methods*/
//private Integer convertHexToInt(hex) {
//    if (isDebug()) log.debug "Convert hex to int: ${hex}"
//	return Integer.parseInt(hex,16)
//}
//private String convertHexToIP(hex) {
//	if (isDebug()) log.debug("Convert hex to ip: $hex") //	a0 00 01 6
//	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
//}
//private getHostAddress() {
//	def parts = device.deviceNetworkId.split(":")
//    if (isDebug()) log.debug "Device Network ID: $device.deviceNetworkId"
//	def ip = convertHexToIP(parts[0])
//	def port = convertHexToInt(parts[1])
//	return ip + ":" + port
//}
private getHostAddress() {
	return "${ipadd}:${port}"
}

private getHexHostAddress() {
	def hosthex = convertIPToHex(ipadd)
    def porthex = convertPortToHex(port)
    if (porthex.length() < 4) {
    	porthex = "00" + porthex
    }
    if (isDebug()) log.debug "Hosthex is : $hosthex"
    if (isDebug()) log.debug "Port in Hex is $porthex"
    return "${hosthex}:${porthex}"
}
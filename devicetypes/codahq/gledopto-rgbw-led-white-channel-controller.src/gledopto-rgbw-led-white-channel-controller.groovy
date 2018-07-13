/**
 *  Gledopto RGBW LED White Channel Controller
 *
 *
 *
 *  Copyright 2018 Ben Rimmasch
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

metadata {
    definition (name: "Gledopto RGBW LED White Channel Controller", namespace: "codahq", author: "Ben Rimmasch") {
        /*
        capability "Contact Sensor"
        capability "Sensor"
        capability "Health Check"

        command "open"
        command "close"
        */

        capability "Switch Level"
        capability "Actuator"
        capability "Switch"
        capability "Sensor"
      
        command "on"
        command "off"
        command "setLevel"
        command "toggle"
    }

    tiles {
        /*
        standardTile("contact", "device.contact", width: 2, height: 2) {
            state("closed", label:'${name}', icon:"st.contact.contact.closed", backgroundColor:"#00A0DC", action: "open")
            state("open", label:'${name}', icon:"st.contact.contact.open", backgroundColor:"#e86d13", action: "close")
        }
        standardTile("freezerDoor", "device.contact", width: 2, height: 2, decoration: "flat") {
            state("closed", label:'Freezer', icon:"st.contact.contact.closed", backgroundColor:"#00A0DC")
            state("open", label:'Freezer', icon:"st.contact.contact.open", backgroundColor:"#e86d13")
        }
        standardTile("mainDoor", "device.contact", width: 2, height: 2, decoration: "flat") {
            state("closed", label:'Fridge', icon:"st.contact.contact.closed", backgroundColor:"#00A0DC")
            state("open", label:'Fridge', icon:"st.contact.contact.open", backgroundColor:"#e86d13")
        }
        standardTile("control", "device.contact", width: 1, height: 1, decoration: "flat") {
            state("closed", label:'${name}', icon:"st.contact.contact.closed", action: "open")
            state("open", label:'${name}', icon:"st.contact.contact.open", action: "close")
        }
        main "contact"
        details "contact"
        */


        multiAttributeTile(name: "whiteChannel", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
        	tileAttribute("device.whiteChannel", key: "PRIMARY_CONTROL") {
				attributeState "off", label: "Off", action: "on", icon: "st.illuminance.illuminance.dark", backgroundColor: "#cccccc"
            	attributeState "on", label: "On", action: "off", icon: "st.illuminance.illuminance.bright", backgroundColor: "#000000"
			}
            
            tileAttribute ("device.whiteChannelLevel", key: "SLIDER_CONTROL") {
            	attributeState "whiteChannelLevel", action: "setLevel"
            }            
        }

        standardTile("whiteChannelIcon", "device.whiteChannel", height: 1, width: 1, inactiveLabel: false, decoration: "flat", canChangeIcon: false) {
            state "off", label:"WW/W", action:"on", icon:"st.illuminance.illuminance.dark", backgroundColor:"#cccccc"
            state "on", label:"WW/W", action:"off", icon:"st.illuminance.illuminance.bright", backgroundColor:"#000000"
        }
        controlTile("whiteChannelSliderControl", "device.whiteChannelLevel", "slider", height: 1, width: 4, inactiveLabel: false) {
            state "whiteChannelLevel", action:"setLevel"
        }
        valueTile("whiteChannelValueTile", "device.whiteChannelLevel", height: 1, width: 1) {
            state "whiteChannelLevel", label:'${currentValue}%'
        } 
        
        main "whiteChannelIcon"
        details(["whiteChannel", "whiteChannelIcon", "whiteChannelSliderControl", "whiteChannelValueTile"])
        

    }
}

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
    sendEvent(name: "whiteChannel", value: "off")
}

def on(onTime = null) {
    log.debug "on()"
    sendEvent(name: "whiteChannel", value: "on")
    parent.white1On(onTime)
}

def off() {
    log.debug "off()"
    sendEvent(name: "whiteChannel", value: "off")
    parent.white1Off()
}

// adding duration to enable transition time adjustments
def setLevel(value, duration = 21) {
    log.debug "setLevel: ${value}"

    if (value == 0) {
        sendEvent(name: "whiteChannel", value: "off")
    }
    else if (device.currentValue("whiteChannel") == "off") {
        sendEvent(name: "whiteChannel", value: "on")
    }
    sendEvent(name: "whiteChannelLevel", value: value)

	parent.setWhite1Level(value, duration)
}
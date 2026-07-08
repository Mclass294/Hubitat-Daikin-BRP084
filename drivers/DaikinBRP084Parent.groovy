/*
 * Copyright 2026 Neil McLaren
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Version history:
 * 0.9.0 - Release engineering: HPM metadata, import URLs, logging polish and repository preparation.
 * 0.8.1 - Add per-child creation preferences.
 * 0.8.0 - Add optional child sensor devices mirrored from parent attributes.
 * 0.7.0 - Add native DSIoT swing mode write support.
 * 0.6.0 - Add native DSIoT thermostat fan mode write support.
 * 0.5.1 - Add Switch capability and power on before HVAC mode writes while off.
 * 0.5.0 - Add native DSIoT thermostat mode and heat/cool setpoint write support.
 * 0.4.0 - Add native DSIoT power on/off write support.
 * 0.3.1 - Rename driver and replace Hubitat-incompatible chained map indexing.
 * 0.3.0 - Add Hubitat Thermostat capability using read-only parsed status attributes.
 * 0.2.0 - Flatten protocol helper class into Hubitat driver methods.
 * 0.1.0 - Initial read-only Daikin BRP084 DSIoT driver.
 */

import groovy.json.JsonOutput

metadata {
    definition(
        name: "Daikin BRP084 Parent",
        namespace: "mclass",
        author: "Neil McLaren",
        importUrl: "https://raw.githubusercontent.com/Mclass294/Hubitat-Daikin-BRP084/main/DaikinBRP084Parent.groovy"
    ) {
        capability "Initialize"
        capability "Refresh"
        capability "Sensor"
        capability "Switch"
        capability "Thermostat"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"

        attribute "airConditionerStatus", "string"
        attribute "availability", "string"
        attribute "currentFanMode", "string"
        attribute "currentSwingMode", "string"
        attribute "daikinMode", "string"
        attribute "driverVersion", "string"
        attribute "energyToday", "number"
        attribute "lastRefresh", "string"
        attribute "lastResponseCode", "number"
        attribute "outsideTemperature", "number"
        attribute "runtimeToday", "number"
        attribute "supportedFanModes", "string"
        attribute "supportedSwingModes", "string"

        command "on"
        command "setDaikinFanMode", [
            [
                name: "Fan Mode",
                type: "ENUM",
                constraints: ["Auto", "Quiet", "Level 1", "Level 2", "Level 3", "Level 4", "Level 5"]
            ]
        ]
        command "setSwingMode", [
            [
                name: "Swing Mode",
                type: "ENUM",
                constraints: ["off", "vertical", "horizontal", "both"]
            ]
        ]
        command "swingOff"
        command "swingVertical"
        command "swingHorizontal"
        command "swingBoth"
    }

    preferences {
        input name: "ipAddress",
            type: "text",
            title: "Daikin adapter IP address",
            required: true
        input name: "requestTimeout",
            type: "number",
            title: "HTTP timeout, seconds",
            defaultValue: 10,
            range: "1..30",
            required: true
        input name: "refreshInterval",
            type: "enum",
            title: "Refresh interval",
            options: ["disabled": "Disabled", "1": "1 minute", "5": "5 minutes", "10": "10 minutes", "15": "15 minutes", "30": "30 minutes"],
            defaultValue: "1",
            required: true
        input name: "enableDebugLogging",
            type: "bool",
            title: "Enable debug logging",
            defaultValue: true
        input name: "enableTraceLogging",
            type: "bool",
            title: "Enable trace logging for raw protocol payloads",
            defaultValue: false
        input name: "logResponseSummary",
            type: "bool",
            title: "Log parsed status summary",
            defaultValue: true
        input name: "createChildDevices",
            type: "bool",
            title: "Create Child Devices",
            defaultValue: false
        input name: "createIndoorTemperatureChild",
            type: "bool",
            title: "Create Indoor Temperature Child",
            defaultValue: true
        input name: "createOutdoorTemperatureChild",
            type: "bool",
            title: "Create Outdoor Temperature Child",
            defaultValue: true
        input name: "createHumidityChild",
            type: "bool",
            title: "Create Humidity Child",
            defaultValue: true
        input name: "createRuntimeTodayChild",
            type: "bool",
            title: "Create Runtime Today Child",
            defaultValue: false
        input name: "createEnergyTodayChild",
            type: "bool",
            title: "Create Energy Today Child",
            defaultValue: false
    }
}

void installed() {
    logInfo "Installed"
    initializeLastManualFanMode()
    ensureChildDevices()
    sendStaticAttributes()
    initialize()
}

void updated() {
    logInfo "Updated"
    unschedule()
    initializeLastManualFanMode()
    ensureChildDevices()
    if (enableDebugLogging) {
        runIn(1800, "disableDebugLogging")
    }
    sendStaticAttributes()
    initialize()
}

void initialize() {
    if (!ipAddress) {
        logWarn "IP address is not configured"
        sendEvent(name: "availability", value: "configurationPending")
        return
    }

    configureRefreshSchedule()
    refresh()
}

void refresh() {
    if (!ipAddress) {
        logWarn "Refresh skipped because IP address is not configured"
        sendEvent(name: "availability", value: "configurationPending")
        return
    }

    Map requestBody = buildStatusRequest()
    Map params = [
        uri: "http://${ipAddress}/dsiot/multireq",
        contentType: "application/json",
        requestContentType: "application/json",
        body: JsonOutput.toJson(requestBody),
        timeout: safeTimeout()
    ]

    logDebug "Refreshing Daikin BRP084 status from ${ipAddress}"
    logTrace "Status request: ${JsonOutput.prettyPrint(JsonOutput.toJson(requestBody))}"

    try {
        httpPost(params) { response ->
            Integer status = response?.status as Integer
            sendEventIfChanged("lastResponseCode", status)

            if (status != 200) {
                markUnavailable("HTTP ${status}")
                return
            }

            Map parsed = parseStatusResponse(response.data)
            applyParsedStatus(parsed)
        }
    } catch (Exception e) {
        markUnavailable(e.message ?: e.toString())
    }
}

void applyParsedStatus(Map parsed) {
    String nowText = new Date().format("yyyy-MM-dd HH:mm:ss", location?.timeZone ?: TimeZone.getDefault())

    sendEventIfChanged("availability", "available")
    sendEventIfChanged("lastRefresh", nowText)
    sendEventIfChanged("airConditionerStatus", parsed.powerOn == null ? "unknown" : (parsed.powerOn ? "on" : "off"))
    sendEventIfChanged("daikinMode", parsed.thermostatMode ?: "unknown")
    sendEventIfChanged("thermostatMode", thermostatModeFromDaikinMode(parsed.thermostatMode))
    sendEventIfChanged("thermostatOperatingState", thermostatOperatingStateFromStatus(parsed))

    sendNumberEventIfPresent("temperature", parsed.currentTemperature, "C")
    sendNumberEventIfPresent("humidity", parsed.currentHumidity, "%")
    sendNumberEventIfPresent("outsideTemperature", parsed.outsideTemperature, "C")
    sendNumberEventIfPresent("thermostatSetpoint", parsed.thermostatSetpoint, "C")
    sendNumberEventIfPresent("coolingSetpoint", parsed.coolingSetpoint, "C")
    sendNumberEventIfPresent("heatingSetpoint", parsed.heatingSetpoint, "C")
    sendNumberEventIfPresent("energyToday", parsed.energyToday, null)
    sendNumberEventIfPresent("runtimeToday", parsed.runtimeToday, null)
    updateChildren(parsed)

    if (parsed.fanMode) {
        sendEventIfChanged("currentFanMode", parsed.fanMode)
        sendEventIfChanged("thermostatFanMode", thermostatFanModeFromDaikinFanMode(parsed.fanMode))
    }
    if (parsed.swingMode) {
        sendEventIfChanged("currentSwingMode", parsed.swingMode)
    }

    if (logResponseSummary) {
        logInfo "Status: ${parsed.thermostatMode ?: "unknown"}, power ${formatPowerState(parsed.powerOn)}, " +
            "indoor ${formatMaybe(parsed.currentTemperature)} C, target ${formatMaybe(parsed.thermostatSetpoint)} C, " +
            "fan ${parsed.fanMode ?: "unknown"}, swing ${parsed.swingMode ?: "unknown"}"
    }
    logTrace "Parsed status: ${parsed}"
}

void markUnavailable(String reason) {
    sendEventIfChanged("availability", "unavailable")
    logWarn "Daikin BRP084 refresh failed: ${reason}"
}

void configureRefreshSchedule() {
    String interval = "${refreshInterval ?: "1"}"
    if (interval == "disabled") {
        logDebug "Automatic refresh disabled"
        return
    }

    Integer minutes = interval.toInteger()
    if (minutes == 1) {
        runEvery1Minute("refresh")
    } else if (minutes == 5) {
        runEvery5Minutes("refresh")
    } else if (minutes == 10) {
        runEvery10Minutes("refresh")
    } else if (minutes == 15) {
        runEvery15Minutes("refresh")
    } else if (minutes == 30) {
        runEvery30Minutes("refresh")
    } else {
        runEvery1Minute("refresh")
    }
    logDebug "Automatic refresh scheduled every ${minutes} minute(s)"
}

void sendStaticAttributes() {
    sendEventIfChanged("driverVersion", driverVersion())
    sendEventIfChanged("supportedThermostatModes", JsonOutput.toJson(supportedThermostatModes()))
    sendEventIfChanged("supportedThermostatFanModes", JsonOutput.toJson(supportedThermostatFanModes()))
    sendEventIfChanged("supportedFanModes", JsonOutput.toJson(supportedFanModes()))
    sendEventIfChanged("supportedSwingModes", JsonOutput.toJson(supportedSwingModes()))
}

void sendNumberEventIfPresent(String name, Object value, String unit) {
    if (value == null) {
        return
    }
    Map event = [name: name, value: value]
    if (unit) {
        event.unit = unit
    }
    sendEventIfChanged(event)
}

void sendEventIfChanged(String name, Object value) {
    sendEventIfChanged([name: name, value: value])
}

void sendEventIfChanged(Map event) {
    String name = event.name as String
    Object oldValue = device.currentValue(name)
    Object newValue = event.value
    if ("${oldValue}" != "${newValue}") {
        sendEvent(event)
    }
}

Integer safeTimeout() {
    Integer timeout = (requestTimeout ?: 10) as Integer
    return Math.max(1, Math.min(30, timeout))
}

String formatMaybe(Object value) {
    return value == null ? "unknown" : "${value}"
}

String formatPowerState(Object value) {
    return value == null ? "unknown" : (value ? "on" : "off")
}

void auto() {
    setThermostatMode("auto")
}

void cool() {
    setThermostatMode("cool")
}

void emergencyHeat() {
    rejectReadOnlyThermostatCommand("emergencyHeat")
}

void fanAuto() {
    setThermostatFanMode("auto")
}

void fanCirculate() {
    rejectReadOnlyThermostatCommand("fanCirculate")
}

void fanOn() {
    // Hubitat Thermostat capability only defines Auto and On.
    // On restores the previously selected manual Daikin fan speed,
    // defaulting to Level 3 if no previous manual speed exists.
    setThermostatFanMode(state.lastManualFanMode ?: "level3")
}

void heat() {
    setThermostatMode("heat")
}

void on() {
    setPowerState(true)
}

void off() {
    setPowerState(false)
}

void setCoolingSetpoint(BigDecimal degrees) {
    setTemperatureSetpoint("p_02", degrees)
}

void setHeatingSetpoint(BigDecimal degrees) {
    setTemperatureSetpoint("p_03", degrees)
}

void setSchedule(String scheduleJson) {
    rejectReadOnlyThermostatCommand("setSchedule")
}

void setThermostatFanMode(String mode) {
    if (!mode) {
        logWarn "Thermostat fan mode write ignored because mode is blank"
        return
    }

    String hvacMode = device.currentValue("daikinMode") as String
    if (hvacMode == "dry") {
        logWarn "Fan speed cannot be changed while HVAC mode is Dry."
        return
    }

    String parameter = fanSpeedAttrByMode().get(hvacMode)
    if (!parameter) {
        logWarn "Fan speed cannot be changed while HVAC mode is ${hvacMode ?: "unknown"}."
        return
    }

    String fanMode = normalizeThermostatFanMode(mode)
    String value = thermostatFanModeWriteValue(fanMode)
    if (!value) {
        logWarn "Unsupported thermostat fan mode write requested: ${mode}"
        return
    }

    Boolean success = sendWriteRequest(indoorStatusPath(), ["e_1002", "e_3001"], parameter, value)
    if (success) {
        if (fanMode != "auto") {
            state.lastManualFanMode = fanMode
        }
        refresh()
    }
}

void setDaikinFanMode(String mode) {
    setThermostatFanMode(mode)
}

void setSwingMode(String mode) {
    if (!mode) {
        logWarn "Swing mode write ignored because mode is blank"
        return
    }

    String swingMode = mode.toString().toLowerCase()
    Map swingValues = swingWriteValues().get(swingMode)
    if (!swingValues) {
        logWarn "Unsupported swing mode write requested: ${mode}"
        return
    }

    String hvacMode = device.currentValue("daikinMode") as String
    List attrs = swingAttrsByMode().get(hvacMode)
    if (!attrs) {
        logWarn "Swing mode cannot be changed while HVAC mode is ${hvacMode ?: "unknown"}."
        return
    }

    Boolean verticalSuccess = sendWriteRequest(indoorStatusPath(), ["e_1002", "e_3001"], attrs.get(0), swingValues.get("vertical"))
    if (!verticalSuccess) {
        logWarn "Swing mode write aborted because vertical swing write failed"
        return
    }

    Boolean horizontalSuccess = sendWriteRequest(indoorStatusPath(), ["e_1002", "e_3001"], attrs.get(1), swingValues.get("horizontal"))
    if (!horizontalSuccess) {
        logWarn "Swing mode write aborted because horizontal swing write failed"
        return
    }

    refresh()
}

void swingOff() {
    setSwingMode("off")
}

void swingVertical() {
    setSwingMode("vertical")
}

void swingHorizontal() {
    setSwingMode("horizontal")
}

void swingBoth() {
    setSwingMode("both")
}

void setThermostatMode(String mode) {
    if (!mode) {
        logWarn "Thermostat mode write ignored because mode is blank"
        return
    }

    String normalizedMode = mode.toString().toLowerCase()
    if (normalizedMode == "off") {
        off()
        return
    }

    String value = thermostatModeWriteValue(normalizedMode)
    if (!value) {
        logWarn "Unsupported thermostat mode write requested: ${mode}"
        return
    }

    // Daikin BRP084C44 firmware ignores mode writes while the unit is off,
    // so power on first before sending the requested HVAC mode.
    if (device.currentValue("airConditionerStatus") == "off") {
        Boolean powerSuccess = sendWriteRequest(indoorStatusPath(), ["e_1002", "e_A002"], "p_01", "01")
        if (!powerSuccess) {
            logWarn "Thermostat mode write aborted because power on failed"
            return
        }
        pauseExecution(500)
    }

    Boolean success = sendWriteRequest(indoorStatusPath(), ["e_1002", "e_3001"], "p_01", value)
    if (success) {
        refresh()
    }
}

void rejectReadOnlyThermostatCommand(String commandName) {
    logWarn "Thermostat command ${commandName} ignored because this driver version is read-only"
}

void setPowerState(Boolean enabled) {
    String value = enabled ? "01" : "00"
    Boolean success = sendWriteRequest(indoorStatusPath(), ["e_1002", "e_A002"], "p_01", value)
    if (success) {
        refresh()
    }
}

void initializeLastManualFanMode() {
    if (!state.lastManualFanMode) {
        state.lastManualFanMode = "level3"
    }
}

void disableDebugLogging() {
    device.updateSetting("enableDebugLogging", [value: "false", type: "bool"])
    logInfo "Debug logging disabled"
}

void logInfo(String message) {
    log.info "${device.displayName}: ${message}"
}

void logWarn(String message) {
    log.warn "${device.displayName}: ${message}"
}

void logDebug(String message) {
    if (enableDebugLogging) {
        log.debug "${device.displayName}: ${message}"
    }
}

void logTrace(String message) {
    if (enableTraceLogging) {
        log.trace "${device.displayName}: ${message}"
    }
}

String driverVersion() {
    return "0.9.0"
}

String indoorStatusPath() {
    return "/dsiot/edge/adr_0100.dgc_status"
}

String outdoorStatusPath() {
    return "/dsiot/edge/adr_0200.dgc_status"
}

String weekPowerPath() {
    return "/dsiot/edge/adr_0100.i_power.week_power"
}

Map childSensorDefinitions() {
    return [
        "indoor": [
            label: "Indoor Temperature",
            driver: "Daikin BRP084 Temperature Child",
            attribute: "temperature",
            unit: "C",
            enabledSetting: "createIndoorTemperatureChild"
        ],
        "outdoor": [
            label: "Outdoor Temperature",
            driver: "Daikin BRP084 Temperature Child",
            attribute: "temperature",
            unit: "C",
            enabledSetting: "createOutdoorTemperatureChild"
        ],
        "humidity": [
            label: "Humidity",
            driver: "Daikin BRP084 Humidity Child",
            attribute: "humidity",
            unit: "%",
            enabledSetting: "createHumidityChild"
        ],
        "runtime": [
            label: "Runtime Today",
            driver: "Daikin BRP084 Measurement Child",
            attribute: "runtimeToday",
            unit: null,
            enabledSetting: "createRuntimeTodayChild"
        ],
        "energy": [
            label: "Energy Today",
            driver: "Daikin BRP084 Measurement Child",
            attribute: "energyToday",
            unit: null,
            enabledSetting: "createEnergyTodayChild"
        ]
    ]
}

String childDeviceNetworkId(String suffix) {
    return "${device.deviceNetworkId}-${suffix}"
}

void ensureChildDevices() {
    if (!createChildDevices) {
        return
    }
    childSensorDefinitions().each { suffix, childDef ->
        if (childCreationEnabled(childDef) && !getChildDevice(childDeviceNetworkId(suffix))) {
            createChildSensor(suffix, childDef)
        }
    }
}

Boolean childCreationEnabled(Map childDef) {
    return settings.get(childDef.enabledSetting) != false
}

void createChildSensor(String suffix, Map childDef) {
    try {
        String childLabel = "${device.displayName} - ${childDef.label}"
        addChildDevice("mclass", childDef.driver, childDeviceNetworkId(suffix), [
            name: childLabel,
            label: childLabel,
            isComponent: false
        ])
        logInfo "Created child device: ${childLabel}"
    } catch (Exception e) {
        logWarn "Unable to create child device."
        logWarn "Please install the Daikin BRP084 child drivers first."
        logDebug "Child device creation failed for ${suffix}: ${e.message ?: e.toString()}"
    }
}

void updateChildren(Map parsed) {
    updateChildAttribute("indoor", parsed.currentTemperature)
    updateChildAttribute("outdoor", parsed.outsideTemperature)
    updateChildAttribute("humidity", parsed.currentHumidity)
    updateChildAttribute("runtime", parsed.runtimeToday)
    updateChildAttribute("energy", parsed.energyToday)
}

void updateChildAttribute(String suffix, Object value) {
    if (value == null) {
        return
    }

    Map childDef = childSensorDefinitions().get(suffix)
    if (!childDef) {
        return
    }

    def child = getChildDevice(childDeviceNetworkId(suffix))
    if (!child && createChildDevices && childCreationEnabled(childDef)) {
        createChildSensor(suffix, childDef)
        child = getChildDevice(childDeviceNetworkId(suffix))
    }
    if (!child) {
        return
    }

    Map event = [
        name: childDef.attribute,
        value: value
    ]
    if (childDef.unit) {
        event.unit = childDef.unit
    }
    child.sendEvent(event)
}

List supportedThermostatModes() {
    return ["off", "heat", "cool", "auto"]
}

List supportedFanModes() {
    return ["Quiet", "Auto", "Level 1", "Level 2", "Level 3", "Level 4", "Level 5"]
}

List supportedThermostatFanModes() {
    return ["auto", "on"]
}

List supportedSwingModes() {
    return ["off", "both", "vertical", "horizontal"]
}

String thermostatFanModeFromDaikinFanMode(String fanMode) {
    return daikinFanModeToThermostatFanMode().get(fanMode) ?: "auto"
}

String thermostatModeFromDaikinMode(String daikinMode) {
    if (!daikinMode) {
        return "off"
    }
    if (daikinMode in ["off", "heat", "cool", "auto"]) {
        return daikinMode
    }
    return "auto"
}

String thermostatOperatingStateFromStatus(Map parsed) {
    if (parsed.powerOn == false || parsed.thermostatMode == "off") {
        return "idle"
    }
    if (parsed.thermostatMode == "cool") {
        return "cooling"
    }
    if (parsed.thermostatMode == "heat") {
        return "heating"
    }
    if (parsed.thermostatMode == "fan only") {
        return "fan only"
    }
    return "idle"
}

Map modeByCode() {
    return [
        "0300": "auto",
        "0200": "cool",
        "0100": "heat",
        "0000": "fan only",
        "0500": "dry"
    ]
}

Map thermostatModeWriteValues() {
    return [
        "heat": "0100",
        "cool": "0200",
        "auto": "0300",
        "fan": "0000",
        "fan only": "0000",
        "dry": "0500"
    ]
}

Map thermostatFanModeWriteValues() {
    return [
        "auto": "0A00",
        "quiet": "0B00",
        "level1": "0300",
        "level2": "0400",
        "level3": "0500",
        "level4": "0600",
        "level5": "0700"
    ]
}

Map swingWriteValues() {
    return [
        "off": [
            vertical: "000000",
            horizontal: "000000"
        ],
        "vertical": [
            vertical: "0F0000",
            horizontal: "000000"
        ],
        "horizontal": [
            vertical: "000000",
            horizontal: "0F0000"
        ],
        "both": [
            vertical: "0F0000",
            horizontal: "0F0000"
        ]
    ]
}

Map thermostatFanModeAliases() {
    return [
        "auto": "auto",
        "on": "level3",
        "quiet": "quiet",
        "level 1": "level1",
        "level1": "level1",
        "level 2": "level2",
        "level2": "level2",
        "level 3": "level3",
        "level3": "level3",
        "level 4": "level4",
        "level4": "level4",
        "level 5": "level5",
        "level5": "level5"
    ]
}

Map daikinFanModeToThermostatFanMode() {
    return [
        "Auto": "auto",
        "Quiet": "quiet",
        "Level 1": "level1",
        "Level 2": "level2",
        "Level 3": "level3",
        "Level 4": "level4",
        "Level 5": "level5"
    ]
}

String thermostatModeWriteValue(String mode) {
    return thermostatModeWriteValues().get(mode)
}

String normalizeThermostatFanMode(String mode) {
    return thermostatFanModeAliases().get(mode.toString().toLowerCase())
}

String thermostatFanModeWriteValue(String mode) {
    return thermostatFanModeWriteValues().get(mode)
}

void setTemperatureSetpoint(String parameter, BigDecimal degrees) {
    if (degrees == null) {
        logWarn "Temperature setpoint write ignored because value is blank"
        return
    }

    String value = encodeSetpointTemperature(degrees)
    Boolean success = sendWriteRequest(indoorStatusPath(), ["e_1002", "e_3001"], parameter, value)
    if (success) {
        refresh()
    }
}

String encodeSetpointTemperature(BigDecimal degrees) {
    Integer encodedValue = (degrees * 2).setScale(0, BigDecimal.ROUND_HALF_UP) as Integer
    return Integer.toHexString(encodedValue).padLeft(2, "0")
}

Map fanModeByCode() {
    return [
        "0A00": "Auto",
        "0B00": "Quiet",
        "0300": "Level 1",
        "0400": "Level 2",
        "0500": "Level 3",
        "0600": "Level 4",
        "0700": "Level 5"
    ]
}

Map targetTempAttrByMode() {
    return [
        "cool": "p_02",
        "heat": "p_03",
        "auto": "p_1D"
    ]
}

Map fanSpeedAttrByMode() {
    return [
        "auto": "p_26",
        "cool": "p_09",
        "heat": "p_0A",
        "fan only": "p_28"
    ]
}

Map swingAttrsByMode() {
    return [
        "auto": ["p_20", "p_21"],
        "cool": ["p_05", "p_06"],
        "heat": ["p_07", "p_08"],
        "fan only": ["p_24", "p_25"],
        "dry": ["p_22", "p_23"]
    ]
}

Map buildStatusRequest() {
    return [
        requests: [
            [op: 2, to: "${indoorStatusPath()}?filter=pv,pt,md"],
            [op: 2, to: "${outdoorStatusPath()}?filter=pv,pt,md"],
            [op: 2, to: "${weekPowerPath()}?filter=pv,pt,md"]
        ]
    ]
}

Map buildWriteRequest(String target, List path, String parameter, String value) {
    Map payload = [
        requests: [
            [
                op: 3,
                pc: [
                    pn: "dgc_status",
                    pch: []
                ],
                to: target
            ]
        ]
    ]

    List children = payload.get("requests").get(0).get("pc").get("pch")
    path.each { pathElement ->
        Map child = [
            pn: pathElement,
            pch: []
        ]
        children << child
        children = child.get("pch")
    }
    children << [
        pn: parameter,
        pv: value
    ]

    return payload
}

Boolean sendWriteRequest(String target, List path, String parameter, String value) {
    if (!ipAddress) {
        logWarn "Write skipped because IP address is not configured"
        sendEvent(name: "availability", value: "configurationPending")
        return false
    }

    Map requestBody = buildWriteRequest(target, path, parameter, value)
    Map params = [
        uri: "http://${ipAddress}/dsiot/multireq",
        contentType: "application/json",
        requestContentType: "application/json",
        body: JsonOutput.toJson(requestBody),
        timeout: safeTimeout()
    ]

    logDebug "Sending Daikin BRP084 write to ${ipAddress}: target=${target}, parameter=${parameter}, value=${value}"
    logTrace "Write request JSON: ${JsonOutput.prettyPrint(JsonOutput.toJson(requestBody))}"

    try {
        Boolean success = false
        httpPut(params) { response ->
            Integer status = response?.status as Integer
            sendEventIfChanged("lastResponseCode", status)
            logDebug "Write HTTP status: ${status}"
            logTrace "Write response JSON: ${JsonOutput.prettyPrint(JsonOutput.toJson(response.data))}"

            if (status != 200) {
                logWarn "Daikin BRP084 write failed: HTTP ${status}"
                success = false
                return
            }

            Integer returnCode = dsiotReturnCode(response.data)
            logDebug "Write DSIoT return code: ${returnCode}"
            if (returnCode == 2004) {
                success = true
            } else {
                logWarn "Daikin BRP084 write rejected: rsc=${returnCode}"
                success = false
            }
        }
        return success
    } catch (Exception e) {
        logWarn "Daikin BRP084 write failed: ${e.message ?: e.toString()}"
        return false
    }
}

Integer dsiotReturnCode(Object responseData) {
    Map data = responseData as Map
    Object responses = data?.responses
    if (!(responses instanceof List) || responses.size() == 0) {
        return null
    }
    Object firstResponse = responses.get(0)
    if (!(firstResponse instanceof Map)) {
        return null
    }
    return firstResponse.get("rsc") as Integer
}

Map parseStatusResponse(Object responseData) {
    Map data = responseData as Map
    String powerCode = tryFindValueByPn(data, indoorStatusPath(), ["dgc_status", "e_1002", "e_A002", "p_01"])
    Boolean powerOn = powerCode == null ? null : powerCode != "00"

    String modeCode = tryFindValueByPn(data, indoorStatusPath(), ["dgc_status", "e_1002", "e_3001", "p_01"])
    String thermostatMode = powerOn == false ? "off" : modeByCode().get(modeCode)

    BigDecimal coolingSetpoint = decodeTemperature(tryFindValueByPn(data, indoorStatusPath(), ["dgc_status", "e_1002", "e_3001", "p_02"]), 2)
    BigDecimal heatingSetpoint = decodeTemperature(tryFindValueByPn(data, indoorStatusPath(), ["dgc_status", "e_1002", "e_3001", "p_03"]), 2)
    String targetAttr = targetTempAttrByMode().get(thermostatMode)
    BigDecimal thermostatSetpoint = targetAttr ?
        decodeTemperature(tryFindValueByPn(data, indoorStatusPath(), ["dgc_status", "e_1002", "e_3001", targetAttr]), 2) :
        coolingSetpoint

    String fanAttr = fanSpeedAttrByMode().get(thermostatMode)
    String fanCode = fanAttr ? tryFindValueByPn(data, indoorStatusPath(), ["dgc_status", "e_1002", "e_3001", fanAttr]) : null
    String fanMode = fanAttr ? fanModeByCode().get(fanCode) : "Auto"

    return [
        powerOn: powerOn,
        thermostatMode: thermostatMode,
        currentTemperature: decodeTemperature(tryFindValueByPn(data, indoorStatusPath(), ["dgc_status", "e_1002", "e_A00B", "p_01"]), 1),
        currentHumidity: decodeInteger(tryFindValueByPn(data, indoorStatusPath(), ["dgc_status", "e_1002", "e_A00B", "p_02"])),
        outsideTemperature: decodeTemperature(tryFindValueByPn(data, outdoorStatusPath(), ["dgc_status", "e_1003", "e_A00D", "p_01"]), 2),
        thermostatSetpoint: thermostatSetpoint,
        coolingSetpoint: coolingSetpoint,
        heatingSetpoint: heatingSetpoint,
        fanMode: fanMode,
        swingMode: thermostatMode == "off" ? "off" : parseSwingMode(data, thermostatMode),
        energyToday: parseEnergyToday(data),
        runtimeToday: tryFindValueByPn(data, weekPowerPath(), ["week_power", "today_runtime"])
    ]
}

String parseSwingMode(Map data, String thermostatMode) {
    List attrs = swingAttrsByMode().get(thermostatMode)
    if (!attrs) {
        return null
    }

    String verticalCode = tryFindValueByPn(data, indoorStatusPath(), ["dgc_status", "e_1002", "e_3001", attrs.get(0)])
    String horizontalCode = tryFindValueByPn(data, indoorStatusPath(), ["dgc_status", "e_1002", "e_3001", attrs.get(1)])
    Boolean vertical = containsActiveSwingMarker(verticalCode)
    Boolean horizontal = containsActiveSwingMarker(horizontalCode)

    if (vertical && horizontal) {
        return "both"
    }
    if (horizontal) {
        return "horizontal"
    }
    if (vertical) {
        return "vertical"
    }
    return "off"
}

Boolean containsActiveSwingMarker(String value) {
    return value != null && value.toUpperCase().contains("F")
}

Object parseEnergyToday(Map data) {
    Object datas = tryFindValueByPn(data, weekPowerPath(), ["week_power", "datas"])
    if (datas instanceof List && datas) {
        return datas.get(datas.size() - 1)
    }
    return null
}

Object tryFindValueByPn(Map data, String fromPath, List names) {
    try {
        return findValueByPn(data, fromPath, names)
    } catch (Exception ignored) {
        return null
    }
}

Object findValueByPn(Map data, String fromPath, List names) {
    List nodes = []
    data.responses?.each { Map response ->
        if (response.fr == fromPath && response.pc != null) {
            nodes << response.pc
        }
    }

    names.eachWithIndex { String name, Integer index ->
        Map found = nodes.find { Map node -> node.pn == name } as Map
        if (!found) {
            throw new IllegalArgumentException("Protocol node ${name} not found at ${fromPath}")
        }
        if (index == names.size() - 1) {
            nodes = [found]
        } else {
            nodes = found.pch ?: []
        }
    }

    return nodes.get(0)?.pv
}

BigDecimal decodeTemperature(Object rawValue, Integer divisor) {
    if (rawValue == null) {
        return null
    }
    String hex = rawValue.toString()
    if (hex.length() < 2) {
        return null
    }

    Integer value = Integer.parseInt(hex.substring(0, 2), 16)
    if (value >= 128) {
        value -= 256
    }
    return (value as BigDecimal) / divisor
}

Integer decodeInteger(Object rawValue) {
    if (rawValue == null) {
        return null
    }
    return Integer.parseInt(rawValue.toString(), 16)
}

/*
 * Copyright 2026 Neil McLaren
 *
 * Licensed under the Apache License, Version 2.0
 */
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
        importUrl: ""
    ) {
        capability "Initialize"
        capability "Refresh"
        capability "Sensor"
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
    }
}

void installed() {
    logInfo "Installed"
    sendStaticAttributes()
    initialize()
}

void updated() {
    logInfo "Updated"
    unschedule()
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
    rejectReadOnlyThermostatCommand("auto")
}

void cool() {
    rejectReadOnlyThermostatCommand("cool")
}

void emergencyHeat() {
    rejectReadOnlyThermostatCommand("emergencyHeat")
}

void fanAuto() {
    rejectReadOnlyThermostatCommand("fanAuto")
}

void fanCirculate() {
    rejectReadOnlyThermostatCommand("fanCirculate")
}

void fanOn() {
    rejectReadOnlyThermostatCommand("fanOn")
}

void heat() {
    rejectReadOnlyThermostatCommand("heat")
}

void off() {
    rejectReadOnlyThermostatCommand("off")
}

void setCoolingSetpoint(BigDecimal degrees) {
    rejectReadOnlyThermostatCommand("setCoolingSetpoint(${degrees})")
}

void setHeatingSetpoint(BigDecimal degrees) {
    rejectReadOnlyThermostatCommand("setHeatingSetpoint(${degrees})")
}

void setSchedule(String scheduleJson) {
    rejectReadOnlyThermostatCommand("setSchedule")
}

void setThermostatFanMode(String mode) {
    rejectReadOnlyThermostatCommand("setThermostatFanMode(${mode})")
}

void setThermostatMode(String mode) {
    rejectReadOnlyThermostatCommand("setThermostatMode(${mode})")
}

void rejectReadOnlyThermostatCommand(String commandName) {
    logWarn "Thermostat command ${commandName} ignored because this driver version is read-only"
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
    return "0.3.1"
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
    return fanMode == "Auto" ? "auto" : "on"
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

# Changelog

## v1.0.0

# Added
  - Initial public release
  - Native DSIoT local communications
  - Power control
  - HVAC mode control
  - Heating setpoint
  - Cooling setpoint
  - Fan mode
  - Swing mode
  - Indoor temperature
  - Outdoor temperature
  - Humidity
  - Runtime Today
  - Energy Today
  - Optional child devices
  - Automatic refresh after write
  - Hubitat Package Manager support
# Tested
  Validated using:
  - Daikin Alira X FTXM71WVMA
  - BRP084C44
  - Firmware 3.12.3
  - Hubitat C8
# Notes
  Hubitat Package Manager's Install from URL expects a packageManifest.json URL.

  Attempting to install repository.json directly will display "Install null" because a repository definition is not itself a package manifest.

## v0.9.0
- Prepared public beta release metadata, import URLs, HPM package files and documentation.
- Moved raw protocol payload logging to trace while keeping concise debug diagnostics.

## v0.8.1
- Added per-child creation preferences for optional child sensors.

## v0.8.0
- Added optional child devices for indoor temperature, outdoor temperature, humidity, runtime today and energy today.

## v0.7.0
- Added native DSIoT swing mode writes and swing convenience commands.

## v0.6.0
- Added native DSIoT fan mode writes and Daikin fan mode command support.

## v0.5.1
- Added Switch capability and power-on-before-mode-write behavior for firmware compatibility.
- Changed setpoint hex encoding to lowercase.

## v0.5.0
- Added native DSIoT thermostat mode and heating/cooling setpoint writes.

## v0.4.0
- Added native DSIoT power on/off write support.

## v0.3.1
- Renamed driver to Daikin BRP084 Parent under namespace `mclass`.
- Improved Hubitat Groovy parser compatibility.

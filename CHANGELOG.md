# Changelog

## v1.0.1

# Changed
  - Normalized numeric parent and child events so Rule Machine receives numeric values, not strings containing units.
  - Added one-shot upgrade repair for existing parent numeric states that may have been stored as strings.
  - Added units to numeric events as event metadata only.
  - Added a dedicated Energy Child with EnergyMeter metadata and standard `energy` event mirroring for Energy Today as a compatibility-driven child-driver change.
  - Added a warning when an existing Energy Today child still uses the v1.0.0 Measurement Child driver and must be manually recreated for EnergyMeter/Home Page Charts support.
  - Kept Runtime Today as a custom numeric `runtimeToday` measurement because Hubitat does not provide a dedicated runtime-today capability.
  - Replaced driver version reporting with a single `DRIVER_VERSION` constant.
  - Improved logging for communication failures, invalid JSON responses and unexpected DSIoT response shapes.

# Compatibility
  - No DSIoT request paths, write payloads, polling behavior, refresh behavior or thermostat commands were changed.
  - The optional child mirror pattern remains unchanged.

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
# Packaging

 Corrected a JSON formatting issue in packageManifest.json following the initial  publication of v1.0.0. This affected HPM package validation only and did not affect the driver code or functionality.

# Notes
  Hubitat Package Manager's Install from URL expects a packageManifest.json URL.

  Attempting to install repository.json directly will display "Install null" because a repository definition is not itself a package manifest.

## v1.0.1
- Note that an existing Energy Today child must be deleted and recreated to gain the new EnergyMeter capability.
- Any affected Rule Machine triggers may need to be deleted and recreated.

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

# Daikin BRP084 v1.0.1 Attribute & Compatibility Audit

## Attribute Audit

| Attribute | Driver | Declared Type | sendEvent Type | Unit | Capability | OK |
|---|---|---:|---|---|---|---|
| airConditionerStatus | Parent | string | String | none | custom | Yes |
| availability | Parent | string | String | none | custom | Yes |
| currentFanMode | Parent | string | String | none | custom | Yes |
| currentSwingMode | Parent | string | String | none | custom | Yes |
| daikinMode | Parent | string | String | none | custom | Yes |
| driverVersion | Parent | string | String | none | custom | Yes |
| energyToday | Parent | number | BigDecimal | kWh | custom | Yes |
| humidity | Parent | number | Integer/BigDecimal | % | RelativeHumidityMeasurement | Yes |
| lastRefresh | Parent | string | String | none | custom | Yes |
| lastResponseCode | Parent | number | Integer | none | custom | Yes |
| outsideTemperature | Parent | number | BigDecimal | °C | custom | Yes |
| runtimeToday | Parent | number | BigDecimal | min | custom | Yes |
| switch | Parent | enum/string | String | none | Switch | Yes |
| temperature | Parent | number | BigDecimal | °C | TemperatureMeasurement | Yes |
| thermostatMode | Parent | enum/string | String | none | Thermostat | Yes |
| thermostatOperatingState | Parent | enum/string | String | none | Thermostat | Yes |
| thermostatFanMode | Parent | enum/string | String | none | Thermostat | Yes |
| thermostatSetpoint | Parent | number | BigDecimal | °C | Thermostat | Yes |
| coolingSetpoint | Parent | number | BigDecimal | °C | Thermostat | Yes |
| heatingSetpoint | Parent | number | BigDecimal | °C | Thermostat | Yes |
| supportedFanModes | Parent | string | JSON String | none | custom | Yes |
| supportedSwingModes | Parent | string | JSON String | none | custom | Yes |
| supportedThermostatModes | Parent | JSON/string | JSON String | none | Thermostat | Yes |
| supportedThermostatFanModes | Parent | JSON/string | JSON String | none | Thermostat | Yes |
| temperature | Temperature Child | number | BigDecimal | °C | TemperatureMeasurement | Yes |
| humidity | Humidity Child | number | Integer/BigDecimal | % | RelativeHumidityMeasurement | Yes |
| runtimeToday | Measurement Child | number | BigDecimal | min | custom | Yes |
| energyToday | Measurement Child | number | BigDecimal | kWh | custom/backward compatible | Yes |
| energy | Energy Child | number | BigDecimal | kWh | EnergyMeter | Yes |
| energyToday | Energy Child | number | BigDecimal | kWh | custom | Yes |

## Changes Made

- Added `@Field static final String DRIVER_VERSION = "1.0.1"` and routed `driverVersion()` through it.
- Normalized parent numeric events through `numericEventValue()` before calling `sendEvent`.
- Added one-shot parent numeric-state repair during `updated()` or the first successful refresh after upgrade so existing string-backed numeric states are re-emitted with numeric types without forcing normal polling events.
- Normalized child numeric mirror events before calling `child.sendEvent`.
- Moved units into event metadata only; no numeric event value includes a unit suffix.
- Changed temperature units from `C` to `°C`, and added explicit `%`, `kWh` and `min` unit metadata.
- Added a passive `switch` event mirror from parsed power state because the parent declares `Switch`.
- Added a dedicated `Daikin BRP084 Energy Child` with `EnergyMeter`, `energy` and `energyToday` as a compatibility-driven child-driver change.
- Added detection and clear warning when an existing Energy Today child is still using `Daikin BRP084 Measurement Child`.
- Kept `runtimeToday` as a custom numeric attribute on the measurement child.
- Added standard `energy` mirroring for Energy Today while retaining `energyToday` for backward compatibility.
- Improved refresh/write diagnostics for communication failures, invalid JSON and unexpected DSIoT response shapes.
- Updated HPM package metadata, README install list and CHANGELOG for v1.0.1.

## Hubitat Limitations

- Hubitat does not provide a standard "runtime today" capability, so `runtimeToday` must remain a custom numeric attribute.
- Home Page Charts support for custom numeric attributes is platform dependent; standard temperature, humidity and energy capabilities are the reliable chart-selection path.
- Existing Energy Today child devices created with the old Measurement Child driver will not automatically change driver type. They can still receive numeric `energyToday`; to expose `EnergyMeter`, the child may need to be recreated or manually changed to `Daikin BRP084 Energy Child`.
- This environment cannot run the Hubitat driver runtime, so verification here is static source and metadata validation only.

## Scope Confirmation

No DSIoT request paths, write payloads, HVAC commands, polling schedule, refresh behavior or thermostat command behavior were changed. The optional child mirror pattern remains unchanged; Energy Today now uses a dedicated child driver so `EnergyMeter` is only advertised where it is applicable.

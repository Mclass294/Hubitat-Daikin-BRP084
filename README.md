# Daikin BRP084 Local Controller

The Daikin BRP084 Local Controller provides native local control of Daikin air conditioners equipped with the BRP084 Wi-Fi adapter using the DSIoT protocol. No cloud services, Home Assistant or third-party integrations are required.

## Supported Hardware

- Daikin BRP084 series local network adapters using the DSIoT local protocol.
- Daikin split systems exposed by the BRP084 DSIoT API.

## Tested Hardware

- Daikin FTXM71WVMA Alira X
- Adapter: BRP084C44
- Firmware: 3.12.3

## Features

- Local DSIoT read/write over HTTP.
- Power on/off.
- Hubitat Thermostat capability.
- Heat, cool, auto and off mode control.
- Heating and cooling setpoints.
- Fan auto/on plus Daikin-specific fan speeds.
- Swing off, vertical, horizontal and both.
- Indoor temperature, outdoor temperature and humidity.
- Runtime today and energy today.
- Optional child devices with per-child creation preferences.
- Debug logging for high-level activity and trace logging for raw protocol payloads.

## Installation

### HPM Installation

HPM package metadata is included for public beta preparation. Add the repository to Hubitat Package Manager when the GitHub repository is published:

```text
https://raw.githubusercontent.com/Mclass294/Hubitat-Daikin-BRP084/main/repository.json
```

### Manual Installation

Install these drivers in Hubitat **Drivers Code**:

1. `DaikinBRP084Parent.groovy`
2. `DaikinBRP084TemperatureChild.groovy`
3. `DaikinBRP084HumidityChild.groovy`
4. `DaikinBRP084MeasurementChild.groovy`

Then create a virtual device using driver type **Daikin BRP084 Parent**.

## Configuration

1. Open the Daikin BRP084 Parent device.
2. Enter the BRP084 adapter IP address.
3. Choose the refresh interval.
4. Enable debug or trace logging only when troubleshooting.
5. Save preferences.
6. Run **Initialize** or **Refresh**.

## Child Devices

The parent remains the only device that polls or writes to the Daikin adapter. Child devices are passive mirrors updated by the parent after refresh.

Enable **Create Child Devices** to allow child creation, then choose individual child preferences:

- Create Indoor Temperature Child
- Create Outdoor Temperature Child
- Create Humidity Child
- Create Runtime Today Child
- Create Energy Today Child

Disabling a child preference does not delete existing child devices. Existing children remain user-managed.

## Known Limitations

- Only BRP084C44 firmware 3.12.3 has been hardware-tested.
- Mode writes are ignored by the adapter while the indoor unit is off, so the driver powers on before non-off mode changes.
- Dry mode fan speed is controlled automatically by the unit and cannot be changed.
- The driver depends on the local DSIoT endpoint being reachable from the Hubitat hub.

## Troubleshooting

- Confirm the Hubitat hub can reach the adapter IP address.
- Check that the adapter responds on `http://<ip>/dsiot/multireq`.
- Use **Refresh** and review device events for updated state.
- Enable debug logging for concise request status and write return codes.
- Enable trace logging only when raw JSON payloads are needed.
- If child creation fails, install all child drivers first.

## Version History

- 1.0.0
  First public release.
- 0.9.0
  Release engineering and HPM packaging.
- 0.8.1
  Selectable child creation.
- 0.8.0
  Child device support.
- 0.7.0
  Swing control.
- 0.6.0
  Fan control.
- 0.5.1
  HVAC mode refinement.
- 0.5.0
  Heating and cooling setpoints.
- 0.4.0
  Power control.

## Acknowledgements

The Home Assistant local Daikin work was used only as a protocol reference. This package is a native Hubitat implementation and does not require Home Assistant at runtime.

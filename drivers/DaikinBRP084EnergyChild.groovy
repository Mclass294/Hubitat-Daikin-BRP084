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
 * 1.0.1 - Add EnergyMeter child for Home Page Charts compatibility.
 */

metadata {
    definition(
        name: "Daikin BRP084 Energy Child",
        namespace: "mclass",
        author: "Neil McLaren",
        importUrl: "https://raw.githubusercontent.com/Mclass294/Hubitat-Daikin-BRP084/main/drivers/DaikinBRP084EnergyChild.groovy"
    ) {
        capability "Sensor"
        capability "EnergyMeter"

        attribute "energy", "number"
        attribute "energyToday", "number"
    }
}

# Photopletysmo
Android Application for estimation of hearth rate via photopletysmogram, recorded by mobilephone camera. The app was developed as a simple demonstration of the capabilities of mobile phones and their sensors. Application can print measured data via Brother: QL-820NWB printer.

# Photoplethysmogram
"A photoplethysmogram (PPG) is an optically obtained plethysmogram that can be used to detect blood volume changes in the microvascular bed of tissue. A PPG is often obtained by using a pulse oximeter which illuminates the skin and measures changes in light absorption. A conventional pulse oximeter monitors the perfusion of blood to the dermis and subcutaneous tissue of the skin." - [Wikipedia](https://en.wikipedia.org/wiki/Photoplethysmogram)

# Download
Latest version of APK is [there](https://github.com/fbme-ictm/mobile-photoplethysmography/releases/latest/download/app-release.apk) and previous releases you can find at this [link](https://github.com/fbme-ictm/mobile-photoplethysmography/releases).

# Build
You need your own **key.properties** and **hw-key.jks** and you must put these files to the root folder.

key.properties
    
    storePassword=YOUR_PASSWORD
    keyPassword=YOUR_PASSWORD
    keyAlias=YOUR_ALIAS

to build run:

    ./gradlew assembleRelease

# HOUND Raspberry Pi Setup & Deployment Guide

This folder contains all files required to run the HOUND Safe Command Agent on Raspberry Pi (Zero / 3B+ / 4B).

## Quick Setup Instructions on Raspberry Pi:

1. Insert this SD card into your Raspberry Pi or copy this directory to `/home/pi/hound_pi`.
2. Open a terminal on the Pi and navigate to this folder:
   ```bash
   cd /home/pi/hound_pi
   ```
3. Make the setup script executable and run it:
   ```bash
   chmod +x setup.sh run.sh
   ./setup.sh
   ```
4. Verify the web dashboard is running on port 8765 and the TCP control listener is on 8766:
   ```bash
   sudo systemctl status hound-pi.service
   ```
5. The service is enabled on boot after setup. Reboot once to confirm:
   ```bash
   sudo reboot
   ```
6. Test the web server locally:
   ```bash
   python3 -m hound_pi.server --web-port 8765 --tcp-port 8766
   ```

## Hardware SPI Wiring (Raspberry Pi -> Arduino Uno):
- RPi MOSI (GPIO 10, Pin 19) <-> Arduino MOSI (Pin 11)
- RPi MISO (GPIO 9, Pin 21)  <-> Arduino MISO (Pin 12)
- RPi SCLK (GPIO 11, Pin 23) <-> Arduino SCK  (Pin 13)
- RPi CE0  (GPIO 8, Pin 24)  <-> Arduino SS   (Pin 10)
- RPi GND  (Pin 6 / 9 / 14)  <-> Arduino GND

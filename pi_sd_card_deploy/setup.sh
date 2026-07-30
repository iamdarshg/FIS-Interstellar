#!/usr/bin/env bash
set -e
echo "=== Installing HOUND Pi Safe Agent ==="
sudo apt-get update && sudo apt-get install -y python3-pip python3-spidev python3-gpiozero git

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
USER_NAME="$(whoami)"
INSTALL_DIR="/home/${USER_NAME}/hound_pi"
REPO_URL="https://github.com/iamdarshg/FIS-Interstellar.git"

echo "=== Installing repository to ${INSTALL_DIR} ==="
if [ -d "${INSTALL_DIR}/.git" ]; then
    cd "${INSTALL_DIR}"
    git pull --ff-only origin main
else
    sudo rm -rf "${INSTALL_DIR}"
    git clone --branch main "${REPO_URL}" "${INSTALL_DIR}"
fi

sudo chown -R "${USER_NAME}:${USER_NAME}" "${INSTALL_DIR}" 2>/dev/null || true

cd "${INSTALL_DIR}"
python3 -m pip install --break-system-packages -e . || python3 -m pip install -e .

echo "=== Enabling SPI Interface ==="
if [ -f "/boot/firmware/config.txt" ]; then
    if ! grep -q "^dtparam=spi=on" /boot/firmware/config.txt; then
        echo "dtparam=spi=on" | sudo tee -a /boot/firmware/config.txt
    fi
elif [ -f "/boot/config.txt" ]; then
    if ! grep -q "^dtparam=spi=on" /boot/config.txt; then
        echo "dtparam=spi=on" | sudo tee -a /boot/config.txt
    fi
fi

echo "=== Installing hound-pi.service ==="
if [ -f "${SCRIPT_DIR}/hound-pi.service" ]; then
    sudo sed \
        -e "s|__HOUND_USER__|${USER_NAME}|g" \
        -e "s|__HOUND_DIR__|${INSTALL_DIR}|g" \
        "${SCRIPT_DIR}/hound-pi.service" | sudo tee /etc/systemd/system/hound-pi.service >/dev/null
    sudo systemctl daemon-reload
    sudo systemctl enable --now hound-pi.service
    echo "=== Service installed and started ==="
fi

echo "=== HOUND Pi Agent Setup Complete! ==="

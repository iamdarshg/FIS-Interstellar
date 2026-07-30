#!/bin/bash
# HOUND Pi First-Boot Automated Setup & Systemd Installer
set -e

echo "=== Running HOUND Pi First-Boot Provisioning ==="
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
USER_NAME="$(whoami)"
INSTALL_DIR="/home/${USER_NAME}/hound_pi"
REPO_URL="https://github.com/iamdarshg/FIS-Interstellar.git"

echo "=== Installing repository to ${INSTALL_DIR} ==="
if [ -d "${INSTALL_DIR}/.git" ]; then
    cd "${INSTALL_DIR}"
    git pull --ff-only origin main
else
    rm -rf "${INSTALL_DIR}"
    git clone --branch main "${REPO_URL}" "${INSTALL_DIR}"
fi

# Enable SPI interface in Raspberry Pi boot config
if [ -f /boot/firmware/config.txt ]; then
    if ! grep -q "^dtparam=spi=on" /boot/firmware/config.txt; then
        echo "dtparam=spi=on" >> /boot/firmware/config.txt
    fi
elif [ -f /boot/config.txt ]; then
    if ! grep -q "^dtparam=spi=on" /boot/config.txt; then
        echo "dtparam=spi=on" >> /boot/config.txt
    fi
fi

sudo chown -R "${USER_NAME}:${USER_NAME}" "${INSTALL_DIR}" 2>/dev/null || true

cd "${INSTALL_DIR}"
echo "=== Installing python package in ${INSTALL_DIR} ==="
python3 -m pip install --break-system-packages -e . || python3 -m pip install -e .

if [ -f "${SCRIPT_DIR}/hound-pi.service" ]; then
    echo "=== Installing systemd auto-start service ==="
    sudo sed \
        -e "s|__HOUND_USER__|${USER_NAME}|g" \
        -e "s|__HOUND_DIR__|${INSTALL_DIR}|g" \
        "${SCRIPT_DIR}/hound-pi.service" | sudo tee /etc/systemd/system/hound-pi.service >/dev/null
    systemctl daemon-reload
    systemctl enable --now hound-pi.service
    echo "=== HOUND Pi Service is ACTIVE & ENABLED ON BOOT! ==="
fi

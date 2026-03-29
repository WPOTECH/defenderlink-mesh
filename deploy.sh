#!/usr/bin/env bash
set -euo pipefail

VM_HOST="wpotech@192.168.4.94"
VM_SSH_KEY="$HOME/.ssh/defenderlink_deploy"
VM_SSH="ssh -i $VM_SSH_KEY -o StrictHostKeyChecking=no"
VM_SCP="scp -i $VM_SSH_KEY -o StrictHostKeyChecking=no"
DEB="build/defenderlink-mesh_1.0.0_amd64.deb"

echo "=== [1/5] Pulling latest from GitHub ==="
git pull origin main

echo "=== [2/5] Building ==="
chmod +x packaging/build-deb.sh
./packaging/build-deb.sh

echo "=== [3/5] Installing on Polaris ==="
sudo apt remove defenderlink-mesh -y 2>/dev/null || true
sudo apt install -y ./$DEB
sudo mkdir -p /opt/defenderlink/logs
sudo chown -R root:root /var/lib/defenderlink
sudo systemctl start defenderlink-mesh

echo "=== [4/5] Copying .deb to VM ==="
$VM_SCP $DEB $VM_HOST:~/defenderlink-mesh.deb

echo "=== [5/5] Installing on VM ==="
$VM_SSH $VM_HOST "
    sudo apt remove defenderlink-mesh -y 2>/dev/null || true &&
    sudo apt install -y ~/defenderlink-mesh.deb &&
    sudo mkdir -p /opt/defenderlink/logs &&
    sudo chown -R root:root /var/lib/defenderlink &&
    sudo systemctl start defenderlink-mesh &&
    echo 'VM install complete'
"

echo ""
echo "=== Deploy complete ==="
echo "Polaris: http://192.168.5.104:8443"
echo "VM:      http://192.168.4.94:8443"
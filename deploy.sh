#!/usr/bin/env bash
set -euo pipefail

VM_HOST="wpotech@192.168.4.94"
DEB="build/defenderlink-mesh_1.0.0_amd64.deb"

echo "=== [1/5] Pulling latest from GitHub ==="
git pull origin master

echo "=== [2/5] Building ==="
./packaging/build-deb.sh

echo "=== [3/5] Installing on Polaris ==="
sudo apt remove defenderlink-mesh -y 2>/dev/null || true
sudo apt install -y ./$DEB
sudo chown -R root:root /var/lib/defenderlink
sudo systemctl start defenderlink-mesh

echo "=== [4/5] Copying .deb to VM ==="
scp $DEB $VM_HOST:~/

echo "=== [5/5] Installing on VM ==="
ssh $VM_HOST "sudo apt remove defenderlink-mesh -y 2>/dev/null || true && \
              sudo apt install -y ~/defenderlink-mesh_1.0.0_amd64.deb && \
              sudo systemctl start defenderlink-mesh"

echo ""
echo "=== Deploy complete ==="
echo "Polaris: http://192.168.5.104:8443"
echo "VM:      http://192.168.4.94:8443"

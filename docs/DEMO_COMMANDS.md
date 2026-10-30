# Perintah Demo

Jalankan di Azure VM dari folder project.

## 1. Periksa worker
```bash
curl -sS http://127.0.0.1:8001/health
curl -sS http://127.0.0.1:8002/health
```

Jika worker belum berjalan, buka dua terminal SSH terpisah dan jalankan:

Terminal 1:
```bash
cd ~/cloud-task-scheduling
WORKER_ID=worker-1 PORT=8001 python3 worker/worker.py
```

Terminal 2:
```bash
cd ~/cloud-task-scheduling
WORKER_ID=worker-2 PORT=8002 python3 worker/worker.py
```

Catatan: jika file `worker.py` di VM berbeda dengan paket ini, gunakan file asli VM yang sudah diuji.

## 2. Compile dan jalankan scheduler
```bash
cd ~/cloud-task-scheduling
javac scheduler/CostGreedyScheduler.java
java -cp scheduler CostGreedyScheduler 6 100000 | tee results/live-demo.txt
```

## 3. Periksa guest VM
```bash
sudo virsh list --all
for vm in host1-vm1 host1-vm2 host2-vm1 host2-vm2; do
  echo "===== $vm ====="
  sudo virsh domifaddr "$vm"
done
```

Jangan mengklaim guest aktif hanya karena status domain `running`; pastikan guest mendapatkan IP dan dapat diakses.

## 4. Simpan bukti
```bash
{
  date
  hostname
  lscpu | head
  free -h
  sudo virsh list --all
  for port in 8001 8002; do
    echo "===== port $port ====="
    curl --max-time 3 -sS "http://127.0.0.1:$port/health" || true
    echo
  done
  echo "===== scheduler output ====="
  cat results/live-demo.txt 2>/dev/null || true
} 2>&1 | tee results/live-evidence.txt
```

# Laporan Implementasi — Cloud Task Scheduling dengan CostGreedy

## 1. Tujuan
Mendemonstrasikan heuristic scheduling yang memilih worker berdasarkan estimasi biaya untuk menjalankan task komputasi. Scheduler memeriksa kesehatan worker, memilih worker dengan estimasi biaya terendah, mengirim task melalui HTTP, lalu mencatat hasil eksekusi.

## 2. Arsitektur
1. **Scheduler (Java)** memeriksa endpoint `/health` setiap worker.
2. **Heuristic CostGreedy** menghitung estimasi biaya dari jumlah iterasi dan parameter biaya worker.
3. **Worker (Python HTTP server)** menerima `POST /run`, menjalankan loop hash SHA-256, lalu mengembalikan status, durasi, dan checksum.
4. **Evidence/logs** menyimpan hasil task dan ringkasan lingkungan.

## 3. Heuristic
Untuk task dengan `I` iterasi dan worker `w`, estimasi biaya:
`estimatedCost(w) = (I / 1,000,000) × costPerMillionIterations(w)`

Parameter ilustratif:
- worker-1: 0.10 unit per juta iterasi
- worker-2: 0.08 unit per juta iterasi

CostGreedy memilih worker sehat dengan estimasi biaya paling rendah. Karena `worker-2` memiliki parameter lebih rendah, semua task pada eksperimen yang tercatat diarahkan ke `worker-2`. Ini menunjukkan keputusan berdasarkan parameter biaya, tetapi belum membuktikan bahwa worker-2 lebih murah dalam billing Azure nyata.

## 4. Hasil eksperimen yang tercatat
- Jumlah task: 6
- Iterasi per task: 100.000
- Status: keenam task `completed`
- Worker terpilih: `worker-2` untuk seluruh task
- Estimasi biaya per task: 0.008 assumed units
- Waktu batch yang tercatat: sekitar 0.270–0.297 detik pada dua log yang tersedia
- Setiap task mengembalikan SHA-256 checksum.

Perbedaan waktu 0.270 dan 0.297 detik berasal dari dua catatan run yang berbeda. Jangan gabungkan keduanya seolah satu pengukuran.

## 5. Bukti infrastruktur terakhir
Output `demo-evidence.txt` pada 2026-10-26 menunjukkan:
- Host: `azurevm-agent`
- VM Azure: 2 vCPU; RAM terlihat sekitar 7.8 GiB
- `virsh list --all` hanya menampilkan `host1-vm1` berstatus `running`
- IP yang ditemukan: `192.168.122.79/24`

Status tersebut belum membuktikan empat guest VM siap. Catatan sebelumnya menyebut domain guest lain dibuat, tetapi pada bukti terakhir hanya `host1-vm1` tampil. Periksa ulang sebelum menyatakan empat VM aktif.

## 6. Keterbatasan dan integritas klaim
- Harga worker adalah asumsi ilustratif, bukan data harga Azure.
- Semua task pada hasil ini diarahkan ke worker-2; belum ada eksperimen pembanding yang cukup untuk menyimpulkan CostGreedy mengungguli algoritma lain.
- Belum tersedia baseline seperti Round Robin atau Random untuk perbandingan terkontrol.
- Bukti yang ada tidak menunjukkan dua host/hypervisor terpisah. Nama guest `host1-*` dan `host2-*` hanyalah label dan tidak mengubah fakta bahwa guest dibuat di satu VM Azure induk.
- Belum ada bukti bahwa worker berjalan di guest VM. Hasil task yang tersedia menunjukkan worker HTTP lokal di Azure VM induk.

## 7. Kesimpulan
Eksperimen berhasil menunjukkan alur nyata: scheduler Java melakukan health check, memilih worker berdasarkan parameter biaya, mengirim enam task lewat HTTP, dan menerima hasil komputasi. Namun, persyaratan infrastruktur multi-host/multi-VM belum dapat dinyatakan terpenuhi berdasarkan bukti yang tersedia.

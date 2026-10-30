# Naskah Presentasi Singkat (± 3–5 menit)

## Pembukaan
“Implementasi ini mendemonstrasikan heuristic CostGreedy untuk penjadwalan task komputasi. Scheduler memeriksa worker yang sehat, menghitung estimasi biaya, lalu mengirim task ke worker dengan estimasi biaya paling rendah.”

## Cara kerja
“Scheduler ditulis dalam Java. Worker ditulis dalam Python dan menyediakan endpoint HTTP `/health` serta `/run`. Setiap task menjalankan sejumlah iterasi hashing SHA-256. Worker mengembalikan status, waktu eksekusi, dan checksum agar hasilnya dapat diverifikasi.”

## Heuristic
“Biaya dihitung dari jumlah iterasi dibagi satu juta, dikalikan parameter biaya per juta iterasi. Pada konfigurasi demo, worker-1 diberi parameter 0,10 dan worker-2 0,08. Karena worker-2 memiliki estimasi lebih rendah, scheduler memilih worker-2.”

## Hasil
“Pada hasil yang tercatat, enam task masing-masing dengan 100 ribu iterasi selesai. Semua task berstatus `completed`, dan masing-masing mengembalikan checksum SHA-256. Waktu batch pada dua run tercatat sekitar 0,270 dan 0,297 detik.”

## Keterbatasan yang perlu disampaikan
“Angka biaya masih merupakan parameter ilustratif, bukan harga aktual Azure. Hasil ini membuktikan alur scheduler-worker, tetapi belum membuktikan penghematan biaya nyata atau keunggulan terhadap baseline. Bukti infrastruktur terakhir juga belum menunjukkan dua host terpisah dan empat guest VM yang semuanya siap.”

## Jika ditanya apakah memenuhi syarat multi-host
“Implementasi task scheduling dan komunikasi worker sudah berjalan, tetapi saya belum dapat mengklaim dua host terpisah terpenuhi. Bukti terakhir menunjukkan guest berada di satu Azure VM induk. Saya akan membedakan capaian implementasi algoritma dari keterbatasan topologi infrastrukturnya.”

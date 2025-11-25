## Alur Program

1. **App dibuka** > MainActivity request izin CAMERA
2. **Izin ditolak** > Tampilkan PermissionDeniedScreen dengan tombol request ulang
3. **Izin granted** > Tampilkan CameraScreen
4. **CameraScreen muncul** > CameraPreview dibuat, bind camera provider ke lifecycle
5. **Preview ready** > Setup ImageCapture, set target rotation
6. **User klik "Ambil Foto"** > Trigger takePhoto()
7. **Foto diambil** > Simpan ke MediaStore dengan timestamp
8. **Berhasil disimpan** > Toast muncul, thumbnail update, foto tersimpan di Pictures/KameraKu


## Penjelasan Kode

### MainActivity
Activity utama yang handle permission dan routing ke screen yang sesuai.

### PermissionDeniedScreen
Screen dengan pesan jelas dan tombol request izin ulang.

### CameraScreen
Screen utama dengan preview kamera, tombol ambil foto, dan thumbnail.

### CameraPreview
Composable yang ngewrap PreviewView (bridge View ke Compose).

### bindPreview
Setup camera provider dan preview dengan aspect ratio 16:9.

### bindWithImageCapture
Setup ImageCapture use case untuk ambil foto.

### outputOptions
Buat metadata MediaStore (filename, MIME type, folder path).

### takePhoto
Trigger capture foto > simpan ke MediaStore > callback update thumbnail.

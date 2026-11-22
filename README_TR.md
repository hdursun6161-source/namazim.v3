# Namazım (Android 16 / SDK 36)

## Özellikler
- **Android 16 (API 36)** uyumlu Jetpack Compose arayüzü (Koyu Yeşil & Altın tema)
- **Diyanet Hesaplama Yöntemi** (`method=13`) ile otomatik konuma göre 5 vakit namaz saatleri
- **Ekran Kapalıyken Çalışan Alarm** (`AlarmManager.setAlarmClock` + `ForegroundService` + tam ekran uyandırma)
- **Kıble Pusulası** (Manyetik sensör + Kâbe açısı hesaplama)
- **Gelişmiş Ezan & Ses Ayarları**:
  - Tam Ezan (3–4 dk), Kısa Ezan (30–60 sn) veya telefondan **MP3 / M4A** seçimi
  - Her namaz vakti (İmsak, Öğle, İkindi, Akşam, Yatsı) için ayrı ses atama
  - Ses önizleme/test butonu, bildirim sesi ve titreşim açma/kapatma

## GitHub Üzerinden Tek Tıkla APK Alma (Android Studio Gerekmez)
1. GitHub'da **yeni ve boş bir depo** (örneğin `namazim-v3`) açın.
2. ZIP içindeki **tüm klasör ve dosyaları** (`app`, `.github`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`) doğrudan bu yeni deponun ana dizinine yükleyin.
3. Üstteki **Actions (Eylemler)** sekmesine girin; **Build Namazim APK** iş akışı otomatik çalışacaktır.
4. Yeşil tik çıktığında sayfanın en altındaki **Artifacts** bölümünden **Namazim-Android16-APK** dosyasını indirip telefonunuza kurun.

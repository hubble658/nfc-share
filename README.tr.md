<h5><a href="./README.md">[English]</a> · [Türkçe]</h5>

# NFC Share

Telefonunu başka bir telefona yaklaştırarak NFC ile **link**, **WiFi ağı** (şifresiyle birlikte) ya da **kişi** gönder. Karşı telefonda uygulama yüklü olması gerekmez.

NFC Share telefonunu bir NFC etiketi gibi davrandırır. Karşı telefon onu bir NFC çıkartması ya da kartı gibi okur:

- Link kendi uygulamasında açılır. YouTube linki YouTube'da, Maps linki Maps'te açılır.
- WiFi ağı için "bağlan" penceresi çıkar.
- Kişi için "kişi ekle" ekranı açılır.

## Özellikler

- **Link ve yazı:** Ne yapıştırırsan yapıştır türü otomatik tanınır: YouTube, Instagram, Google Maps ya da herhangi bir link. Maps linkleri gerçek konum olarak gider.
- **WiFi:** Bağlı olduğun ağı seç, şifresiyle birlikte paylaş. Karşı telefon tek dokunuşla ağa bağlanır, tıpkı WiFi QR kodu okutmak gibi.
- **Kişi:** Rehberinden birini seç; adı, numarası ve e-postası kişi kartı olarak gider.
- **Paylaştığın her şey kaydedilir:** Kayıtlı bir öğeye dokunarak tekrar paylaşabilirsin. Yanındaki çöp kutusu ikonuyla silebilir, basılı tutarak düzenleyebilir ya da kopyalayabilirsin.
- **Süre sınırı:** Paylaşım 20 sn, 1 dk ya da 5 dk sonra kendiliğinden durur; istersen süresiz de yapabilirsin. Geri sayım ve okunma sayacı neler olduğunu gösterir.
- **Ana ekran widget'ları:** Biri seçtiğin kayıtlı öğeyi tek dokunuşla paylaşır, diğeri en son kopyaladığın şeyi.
- **Paylaş menüsü:** Herhangi bir uygulamanın (YouTube, Chrome, Instagram…) "Paylaş" menüsünden NFC Share'i seç, paylaşım hemen başlar.
- **Metin alma:** Android, NFC ile gelen düz yazıyı kopyalanamaz şekilde gösterir. Karşı telefonda da NFC Share yüklüyse yazı "Metni Kopyala" butonuyla açılır.
- **Türkçe ve İngilizce.**

## Gizlilik

**NFC Share hiçbir veri toplamaz.** Uygulamanın internet izni yoktur, yani hiçbir yere veri gönderemez. Kaydettiğin her şey (linkler, WiFi şifreleri, kişiler) sadece telefonunda saklanır. Veriler telefondan yalnızca NFC ile çıkar, o da sen telefonu başka bir cihaza yaklaştırdığında.

### İzinler neden isteniyor

| İzin | Neden |
|---|---|
| Konum | Android, bağlı olunan WiFi ağının adını sadece konum izni olan uygulamalara gösterir. NFC Share konumunu okumaz ve kaydetmez. |
| Kişiler | Sadece "Kişilerden Seç" dediğinde, seçtiğin kişinin adını, numarasını ve e-postasını doldurmak için kullanılır. |
| NFC | Seçtiğin şeyi yaklaştırdığın telefona göndermek için. |
| Shizuku (isteğe bağlı) | Aşağıya bak. |

## Kayıtlı tüm WiFi şifrelerini aktarma (isteğe bağlı)

Android, normal uygulamaların kayıtlı WiFi şifrelerini okumasına izin vermez. Bir ağı ilk kez paylaşırken şifresini bir kez yazarsın, NFC Share onu hatırlar.

Kayıtlı ağların hepsini tek seferde aktarmak için [Shizuku](https://shizuku.rikka.app/) kullanabilirsin. Shizuku, ADB'nin (USB hata ayıklama) yetkileriyle çalışır, root gerektirmez.

1. Shizuku'yu kablosuz hata ayıklama ile bir kez başlat.
2. NFC Share'de **Ayarlar → Shizuku ile toplu aktarma** bölümüne gir.
3. Bir kez içe aktardıktan sonra Shizuku silinebilir.

Android 11 ya da üstü gerekir.

## Bilmekte fayda var

- **İki telefonun da ekran kilidi açık olmalı.** Android, güvenlik gereği kilitli telefonda NFC okumaz.
- **NFC açık olmalı.** NFC kapalıysa uygulama uyarır ve NFC ayarlarına götürür.
- **iPhone:** Linkleri okur. WiFi ve kişi sadece karşı telefon Android ise çalışır. (iPhone ile henüz test edilmedi.)
- **Bazı Samsung'larda "hangi uygulama?" sorusu:** Bazı yeni Samsung modellerinde NFC'ye aynı şekilde cevap veren yerleşik bir servis var. Android hangi uygulamanın kullanılacağını sorarsa NFC Share'i seç. Uygulama ekranda açıkken otomatik olarak seçilir.

## Derleme

JDK 17 ve Android SDK (platform 35) yeterli, Android Studio gerekmez.

```bash
./gradlew :app:assemblePhoneDebug      # app/build/outputs/apk/phone/debug/
./gradlew :app:assemblePhoneRelease    # signing/signing.properties gerekir (aşağıya bak)
```

Release sürümleri `signing/signing.properties` dosyasıyla imzalanır. Bu dosya repoda yoktur:

```properties
app.keystore.file=signing/anahtarin.jks
app.keystore.password=...
app.key.alias=...
app.key.password=...
```

## Teşekkür ve lisans

NFC etiketi gibi davranma işini **LuigiVampa92**'nın [ndef-emulator](https://github.com/LuigiVampa92/ndef-emulator) kütüphanesi yapar. Bu kütüphanenin değiştirilmiş kopyası, orijinal belgeleriyle birlikte [`ndefemulation/`](./ndefemulation) klasöründedir. Nelerin değiştirildiği [NOTICE](./NOTICE) dosyasında yazar.

[Apache Lisansı 2.0](./LICENSE.md) ile lisanslanmıştır.

# KutuDepom

Kotlin ile Android 8.0 ve üzeri için çevrimdışı envanter uygulaması.

- Yalnızca kutu adı zorunludur; oda, dolap ve raf isteğe bağlıdır. Mevcut kutular aynı kuralla düzenlenebilir.
- Ürün ekleme/düzenleme/silme, sade ürün kartları ve kutular arasında taşıma. Stok/adet alanları gösterilmez; eski kayıtların sayısal verileri korunur.
- Ürün, not ve konum içinde Türkçe büyük/küçük harf uyumlu arama.
- Galeriden ürün fotoğrafı seçme/kaldırma; fotoğraflar cihazın uygulama alanına kopyalanır (en fazla 15 MB).
- QR etiket oluşturma, PNG paylaşma/yazdırma ve kamera ile kutu içeriğini açma.
- SQLite yerel veritabanı; ürün ve belge bütünlüğü, dolu kutu silme koruması ve onaylı silme.
- Tüm uygulama ekranlarında Material 3 bileşenleri, açık/koyu tema ve tutarlı form/kart/dialog tasarımı.
- Kullanım Kılavuzu: PDF, DOC/DOCX, XLS/XLSX, PPT/PPTX, ODT/ODS/ODP, TXT, CSV, RTF, EPUB ve Markdown belgeleri. Çoklu seçim, özel cihaz içi depolama, listeleme, açma ve kaldırma (belge başına en fazla 50 MB).

İlk kutuyu yalnızca adıyla oluşturabilir, isteğe bağlı konum bilgileri ekleyebilirsiniz. Ana ekran başlığı, yeni kutu/QR/aramanın kontrolleri sabittir; yalnızca kutu listesi kayar. Kutu ekranında Ürün Ekle, QR Etiketi ve Kutu İşlemleri sabit kalır; ürünler kalan yüksekliği kullanan bağımsız RecyclerView listesinde yalnızca adlarıyla gösterilir. Kutu düzenleme ve onaylı silme Kutu İşlemleri menüsündedir. Açıklamalar ürün detayında eksiksiz korunur. Arama yazdıkça ürün adı, not ve konum içinde çalışır; arama metni temizlendiğinde kutu listesi geri gelir. Ayrı Ara düğmesi yoktur. Kamera izni yalnızca QR taraması için gerekir. İnternet bağlantısı gerekmez.

Önceki sürümün `boxes` ve `items` kayıtları veritabanı sürüm 3 geçişinde korunur; kutu/ürün kimlikleri ve `KUTUDEPOM:17` gibi eski QR etiketleri geçerlidir. QR etiketi yalnızca cihazdaki kutu kimliğini taşır; başka cihaza ürün verisi aktarmak veya bulut senkronizasyonu bu sürümün kapsamı dışındadır.

## Derleme ve APK

JDK 17, Android SDK 35 ve Gradle 8.9 kullanılır. Android Studio ile projeyi açın veya:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest # bağlı cihaz / emülatör gerekir
```

GitHub Actions `main` güncellemelerinde, pull request'lerde ve elle çalıştırıldığında debug APK, birim testi ve lint üretir. **Actions → başarılı Android APK çalıştırması → Artifacts → KutuDepom-APK** içinden APK'yı indirin. Debug APK yolu: `app/build/outputs/apk/debug/app-debug.apk`.

Android 10 (API 29, 320 × 569 dp küçük ekran) ve Android 15 (API 35, yaklaşık 411 × 891 dp büyük ekran) emülatör testleri 100 kutulu ana ekranı, 100 ürünlü kutuyu, kaydırma sırasında kontrollerin yerinde kalmasını, uzun Türkçe açıklamaların detayda/taslakta/kayıtta korunmasını ve gerçek ekran klavyesi açıkken form alanlarına erişimi doğrular. Ayrıca sürüm 1/2 → 3 geçişlerini, fotoğraf/not/kimliklerin korunmasını, belge işlemlerinin atomikliğini, isteğe bağlı konumları, arama durumlarını, ürün kartlarını, belge kopyalama/açma/kaldırma/taşıma/silme akışlarını ve ekran yeniden oluşturulmasında taslakların korunmasını denetler. Uygulamayı kaldırmak/verisini temizlemek yerel kayıtları siler. Debug APK geliştirme imzası kullanır.

## Kullanım kılavuzları

Ürün ekleme veya düzenleme ekranında **Kullanım Kılavuzu → Belge ekle** ile bir veya birden fazla belge seçin. Belge kopyalandıktan sonra özgün dosya veya sağlayıcı bağlantısı gerekli değildir. **Kaydet** ürün ve belge değişikliklerini birlikte uygular; geri dönmek yeni belge taslaklarını temizler. **Aç** cihazdaki uygun belge görüntüleyiciyi kullanır; uygun uygulama yoksa bilgi mesajı gösterilir. **Kaldır** onay ister ve kaldırma Kaydet ile uygulanır. Ürün silindiğinde kayıtlı belgeler ve dosyaları da temizlenir; ürün taşındığında belgeler üründe kalır.

Sürüm 3 geçişi mevcut tabloları silmez; yalnızca ilişkili belge tablosunu ekler. Belgeler FileProvider üzerinden geçici salt okuma yetkisiyle açılır.

## Arayüz (0.4.0)

Kartlar 16 sp Material 3 metin, 14 dp köşe ve kompakt boşluklar kullanır. Kutunun gerçek adı ve isteğe bağlı konumu ayrı gösterilir. Paket ikonlarının yeşil/mavi/mor/turuncu pastel rengi kalıcı kutu ID’sinden seçilir; sıralama değişince renk değişmez. Yeni Kutu yeşil, QR Kod Tara mor ikonlu düğmelerdir. Açık/koyu tema için ayrı, kontrastlı paletler vardır; formların tüm normal/konum alanları aynı 16 sp metin ve çerçeve biçimine sahiptir. Listeler geri dönüştürülen kartlarla gösterilir; fotoğraflar ve kullanım kılavuzları ürün detayında kullanılmaya devam eder. Formlar klavye ve sistem çubuklarının boşluklarına uyarlanır ve kaydırılabilir. Bu arayüz güncellemesi veritabanı sürümünü değiştirmez; mevcut SQLite kayıtları, notlar, dosyalar ve QR kimlikleri korunur.

## Manuel ZIP yedekleri (0.5.0)

Ana ekrandaki **Yedekle** sistem dosya kaydetme ekranını açar. Tarih/saat içeren ZIP dosyasında SQL ile oluşturulan tutarlı SQLite anlık görüntüsü, ilişkili fotoğraflar ve tüm kullanım kılavuzu belgeleri ile sürüm/boyut/SHA-256 bilgili manifest bulunur. WAL dosyasındaki kayıtlar SQL üzerinden kopyalanır. İşlem dosya yazımı tamamlanmadan başarılı sayılmaz. İnternet veya genel depolama izni gerekmez. Android otomatik bulut yedeklemesi ve cihaz aktarımı kapalıdır; bu özellik yalnızca kullanıcının başlattığı manuel işlemleri yapar.

**İçe Aktar** seçilen ZIP'i önce özel geçici alanda doğrular; mevcut veriye dokunmadan açık onay ister. Onaydan sonra yeni dosyalar benzersiz adlarla hazırlanıp diske yazılır; kutu/ürün/belge kayıtları tek SQLite işlemiyle değiştirilir. Hata olursa işlem geri alınır ve yalnızca yeni hazırlanmış dosyalar temizlenir. Kutu/ürün/belge kimlikleri ve otomatik kimlik sırası korunur; QR etiketleri geçerlidir. Açıklama, konum, fotoğraf ve belge içeriği korunur. Veritabanı sürümü hâlâ 3'tür.

Yedek biçimi sürüm 1'dir. CRC, dosya boyutu, SHA-256, gerekli dosyalar, SQLite bütünlüğü ve ilişkiler kontrol edilir. Mutlak/üst dizin/backslash/yinelenen yollar kabul edilmez. Toplam ZIP/açılmış içerik sınırı 512 MB, dosya sayısı 5000; veritabanı 64 MB, fotoğraf 15 MB, belge 50 MB ile sınırlıdır. ZIP yedekleri şifreli değildir; dosyayı güvendiğiniz bir konumda saklayın.

### Mevcut uygulamanın üzerine kurulum ve imza

Paket kimliği `com.kutudepom.app` korunur, sürüm kodu 6'dır. Üzerine kurulum için **kurulu APK ile aynı özel imza anahtarı** gerekir. Önceki Actions sürümleri geçici debug anahtarını saklamadığı için 0.3 ve 0.4 APK sertifikaları farklıdır; özel anahtar APK'dan geri çıkarılamaz. Uygulamayı kaldırmak çözüm değildir ve yerel verileri riske atar.

Özgün anahtar mevcutsa depo Actions secrets alanında `KUTUDEPOM_KEYSTORE_BASE64`, `KUTUDEPOM_STORE_PASSWORD`, `KUTUDEPOM_KEY_ALIAS`, `KUTUDEPOM_KEY_PASSWORD` tanımlanır. Anahtar depoya veya artifact'e yüklenmez. CI sertifikayı önceki 0.4.0 APK'sının SHA-256 değeriyle kontrol eder. Başka özgün bir sürüm için beklenen sertifika `KUTUDEPOM_EXPECTED_SIGNER_SHA256` Actions variable olarak belirtilebilir. Eşleşen anahtarla `KutuDepom-APK` üretilir; anahtar yoksa yalnızca **KutuDepom-TEST-APK** yayımlanır ve bu dosya 0.4 üzerine kurulabilir güncelleme olarak sunulmaz.

## Launcher ikonu (0.5.1)

Kullanıcının gönderdiği turuncu kutu görseli, transparan foreground ve maskeyi dolduran mavi gradient background ile adaptive icon olarak kullanılır. Foreground 108 dp katmanda merkezlenir; görünür şekil 66 dp çapındaki güvenli alanın içinde kalır. mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi için 108/162/216/324/432 px foreground ve 48/72/96/144/192 px klasik/yuvarlak launcher kaynakları vardır. API 26 adaptive XML, API 33 monochrome vektörü; manifest hem icon hem roundIcon belirtir. Monochrome, tema rengini launcher'dan alır. One UI maskesi için daire ve yuvarlatılmış kare önizlemeleri kontrol edildi; fiziksel Samsung cihazında doğrulama bu ortamda yapılamadı. Paket kimliği ve veritabanı şeması değişmez.

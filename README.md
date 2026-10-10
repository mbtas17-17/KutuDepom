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

İlk kutuyu yalnızca adıyla oluşturabilir, isteğe bağlı konum bilgileri ekleyebilirsiniz. Ürün listesi kutu işlem bölümünden ayrıdır. Boş aramada Ara düğmesi pasiftir; sonuç ekranından yeniden arama yapılabilir. Kamera izni yalnızca QR taraması için gerekir. İnternet bağlantısı gerekmez.

Önceki sürümün `boxes` ve `items` kayıtları veritabanı sürüm 3 geçişinde korunur; kutu/ürün kimlikleri ve `KUTUDEPOM:17` gibi eski QR etiketleri geçerlidir. QR etiketi yalnızca cihazdaki kutu kimliğini taşır; başka cihaza ürün verisi aktarmak veya bulut senkronizasyonu bu sürümün kapsamı dışındadır.

## Derleme ve APK

JDK 17, Android SDK 35 ve Gradle 8.9 kullanılır. Android Studio ile projeyi açın veya:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest # bağlı cihaz / emülatör gerekir
```

GitHub Actions `main` güncellemelerinde, pull request'lerde ve elle çalıştırıldığında debug APK, birim testi ve lint üretir. **Actions → başarılı Android APK çalıştırması → Artifacts → KutuDepom-APK** içinden APK'yı indirin. Debug APK yolu: `app/build/outputs/apk/debug/app-debug.apk`.

Gerçek cihaz/emülatör testleri sürüm 1/2 → 3 geçişlerini, fotoğraf/not/kimliklerin korunmasını, belge işlemlerinin atomikliğini, isteğe bağlı konumları, arama durumlarını, ürün kartlarını, belge kopyalama/açma/kaldırma/taşıma/silme akışlarını ve ekran yeniden oluşturulmasında taslakların korunmasını denetler. Uygulamayı kaldırmak/verisini temizlemek yerel kayıtları siler. Debug APK geliştirme imzası kullanır.

## Kullanım kılavuzları

Ürün ekleme veya düzenleme ekranında **Kullanım Kılavuzu → Belge ekle** ile bir veya birden fazla belge seçin. Belge kopyalandıktan sonra özgün dosya veya sağlayıcı bağlantısı gerekli değildir. **Kaydet** ürün ve belge değişikliklerini birlikte uygular; geri dönmek yeni belge taslaklarını temizler. **Aç** cihazdaki uygun belge görüntüleyiciyi kullanır; uygun uygulama yoksa bilgi mesajı gösterilir. **Kaldır** onay ister ve kaldırma Kaydet ile uygulanır. Ürün silindiğinde kayıtlı belgeler ve dosyaları da temizlenir; ürün taşındığında belgeler üründe kalır.

Sürüm 3 geçişi mevcut tabloları silmez; yalnızca ilişkili belge tablosunu ekler. Belgeler FileProvider üzerinden geçici salt okuma yetkisiyle açılır.

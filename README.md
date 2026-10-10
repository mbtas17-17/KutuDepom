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

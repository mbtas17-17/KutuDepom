# KutuDepom

Kotlin ile Android 8.0 ve üzeri için çevrimdışı envanter uygulaması.

- Oda → dolap → raf → kutu gezinmesi; kutu oluştururken mevcut konum adlarını seçme, konum düzenleme ve boş kutu silme.
- Ürün ekleme/düzenleme/silme, kutular arasında taşıma, stok artırma/azaltma.
- Ürün, not ve konum içinde Türkçe büyük/küçük harf uyumlu arama.
- Galeriden ürün fotoğrafı seçme/kaldırma; fotoğraflar cihazın uygulama alanına kopyalanır (en fazla 15 MB).
- QR etiket oluşturma, PNG paylaşma/yazdırma ve kamera ile kutu içeriğini açma.
- SQLite yerel veritabanı; stok sınırları ve dolu kutu silme koruması.

İlk kutuyu oda, dolap ve rafıyla birlikte oluşturun. Konumlar ekranından bu hiyerarşide gezinebilir, kutunun içinden ürün ekleyebilirsiniz. Kamera izni yalnızca QR taraması için gerekir. İnternet bağlantısı gerekmez.

Önceki sürümün `boxes` ve `items` kayıtları veritabanı sürüm 2 geçişinde korunur; kutu/ürün kimlikleri ve `KUTUDEPOM:17` gibi eski QR etiketleri geçerlidir. QR etiketi yalnızca cihazdaki kutu kimliğini taşır; başka cihaza ürün verisi aktarmak veya bulut senkronizasyonu bu sürümün kapsamı dışındadır.

## Derleme ve APK

JDK 17, Android SDK 35 ve Gradle 8.9 kullanılır. Android Studio ile projeyi açın veya:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest # bağlı cihaz / emülatör gerekir
```

GitHub Actions `main` güncellemelerinde, pull request'lerde ve elle çalıştırıldığında debug APK, birim testi ve lint üretir. **Actions → başarılı Android APK çalıştırması → Artifacts → KutuDepom-APK** içinden APK'yı indirin. Debug APK yolu: `app/build/outputs/apk/debug/app-debug.apk`.

Gerçek cihaz/emülatör veritabanı testi eski sürümden geçişi, kayıtların korunmasını, stok sınırlarını ve ilişki kurallarını denetler. Uygulamayı kaldırmak/verisini temizlemek yerel kayıtları siler. Debug APK geliştirme imzası kullanır.

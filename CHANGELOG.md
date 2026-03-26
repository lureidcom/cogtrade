# CogTrade Changelog

## [1.2.0] - 2026-03-26 - Book GUI Update

### 🎉 Major Features

- **CogTrade Book GUI**
  - Unified kitap temalı arayüz — `L` tuşu veya Market bloğuna sağ tık ile açılır
  - Pazar, Sandık (Depot), Trade Post ve Takas sayfaları tek kitapta
  - NineSliceRenderer ile artifact'sız, temiz doku render
  - Sabit screen-pixel içerik boyutları — tüm çözünürlüklerde tutarlı görünüm

- **BookTrade — Oyuncu-Oyuncu Takas Sistemi**
  - Kitap GUI içinden doğrudan eşya takası
  - Gerçek zamanlı teklif / kabul / reddet mekanizması
  - `TradeOfferReceivedPacket` / `TradeOfferClearedPacket` ile sunucu→client bildirim paketi sistemi
  - `ClientTradeOffers` önbelleği ile anlık teklif durumu takibi

- **Market Fiyat Tablosu**
  - 100+ item için kesin referans fiyat tanımları
  - Mineraller (Diamond, Netherite, Emerald, Iron, Gold, Coal, Copper, Lapis, Redstone…)
  - Nether & End malzemeleri (Beacon, Elytra, Totem, Dragon Egg, Nether Star…)
  - Yiyecekler, zırh, alet, silah kategorileri
  - Enchant'lı item desteği (`EnchantmentLevelEntry` entegrasyonu)
  - Kategori bazlı otomatik fiyat tahmini

### ✨ GUI Yenileme (DepotScreen & TradePostScreen)

- **Metin gölgesi sorunu tamamen giderildi**
  - Tüm `drawCenteredTextWithShadow` çağrıları kaldırıldı (başlıklar dahil)
  - `ctx.drawText(..., false)` ile net ve okunaklı metin her çözünürlükte
- **Trade Post popup**
  - "Yeni İlan Ekle" butonu tıklanınca açılan modal pencere
  - Item ikonu, fiyat girişi, otomatik kategori tespiti (kullanıcıya gösterilmez)
  - Popup yüksekliği dinamik hesaplanır — taşma sorunu giderildi
- **Depot sağ sayfa**
  - Item detayı, sandık sayısı ve ilan durumu düzgün hizalı
- **Ölçekleme**
  - Sabit screen-pixel içerik boyutları sayesinde her pencere boyutunda tutarlı görünüm

### 🧱 Market Bloğu

- Özel yüzey dokuları eklendi (ön, arka, yan, üst, alt)
- `market_block.json` blockstate/model güncellendi

### 🔧 Teknik Değişiklikler

- `CogTradeClient`: `O` tuşu `/market` kısayolu kaldırıldı → `L` tuşu Book GUI açıyor
- `ClientMarketData` önbellek sistemi eklendi (market verilerini client'ta tutar)
- `AdminCommand`'a ek yönetici komutları eklendi
- `DirectTradeManager` küçük iyileştirmeler

---

## [1.1.0] - 2026-03-13 - Direct Trade Update

### 🎉 Major Features
- **Direct Player-to-Player Trading System**
  - Live trading interface between two players
  - Real-time offer synchronization
  - Safe item and coin exchange
  - Mutual confirmation required (both players must ready up)
  - Command: `/trade offer <player>` to initiate

### ✨ Enhancements
- **Modern Trade GUI**
  - Completely redesigned 450×300px interface
  - Color-coded sections (green for your offer, yellow for partner)
  - Contextual status messages showing trade state

- **Advanced Item Quantity Control**
  - Left-click: Move entire stack
  - Right-click: Move single item (1x)
  - Middle-click: Open quantity editor for precise control
  - Smart stack merging for compatible items

- **Improved Command Structure**
  - `/trade offer <player>` — Send trade request (with autocomplete)
  - `/trade accept` / `/trade reject` / `/trade cancel`

### 🔒 Safety & Stability
- Fixed item loss when cancelling trades
- Safe item recovery system for offline players (pending return queue)
- Synchronized finalization prevents race conditions
- All trades validated server-side

---

## [1.0.0] - 2026-03-11 - Initial Alpha Release

### Features
- **Trade Depot Block** — Link chests, create player shops
- **Trade Post Block** — Public advertisement system
- **Server Market** — Admin-controlled item marketplace
- **Economy System** — Coin-based currency (⬡), `/balance`, `/pay`
- **Database Integration** — SQLite for persistent data

### Known Issues (Alpha)
- Limited polish on some GUIs
- Potential edge cases in complex scenarios

---

## Upcoming Features (Planned)
- Trade history viewer
- Advanced filtering for Trade Posts
- Bulk item operations
- Enhanced security features
- Performance optimizations

---

**Note**: This is an ALPHA release. Features are experimental and may contain bugs. Always backup your world before use!

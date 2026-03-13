# CogTrade Changelog

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
  - Gradient effects and professional styling
  - Clear visual distinction between editable and read-only areas
  - Contextual status messages showing trade state

- **Advanced Item Quantity Control**
  - Left-click: Move entire stack
  - Right-click: Move single item (1x)
  - Middle-click: Open quantity editor for precise control
  - Smart stack merging for compatible items
  - Full support for NBT, enchantments, custom names

- **Improved Command Structure**
  - `/trade offer <player>` - Send trade request (with player autocomplete)
  - `/trade accept` - Accept incoming request
  - `/trade reject` - Reject incoming request
  - `/trade cancel` - Cancel active trade
  - Player names now have autocomplete suggestions

### 🔒 Safety & Stability
- **Critical Bug Fixes**
  - Fixed item loss when cancelling trades (inventory sync issue)
  - Added `inventory.markDirty()` calls for proper synchronization

- **Safe Item Recovery System**
  - Items no longer dropped into world when player offline
  - New pending return queue for offline players
  - Items automatically returned when player rejoins
  - No more risk of permanent item loss

- **Enhanced Trade Safety**
  - Synchronized finalization prevents race conditions
  - Ready states auto-reset on any offer change
  - Session validation prevents stale operations
  - Disconnect handling preserves items safely
  - Double-finalization protection

- **Server Authority**
  - All trades validated server-side
  - Client-reported counts verified
  - Stack operations checked for validity
  - Session consistency enforced

### 🎨 GUI Improvements
- Modern panel design with better spacing
- Visual feedback for hover states
- Improved coin input fields with placeholders
- Better button styling and labeling
- Usage hints displayed inline
- Professional color palette matching Market/Depot GUIs

### 🛠 Technical Improvements
- Added `PendingItemReturn` system for safe offline recovery
- Enhanced `DirectTradeSession` with partial stack methods
- Extended packet protocol with click type support
- Improved synchronization between client and server
- Better edge case handling (full inventory, invalid operations, etc.)

### 📝 Documentation
- Updated command documentation
- Added inline usage hints in GUI
- Improved error messages

---

## [1.0.0] - 2026-03-11 - Initial Alpha Release

### Features
- **Trade Depot Block**
  - Link chests to create player shops
  - Sell items directly from linked storage
  - Automatic inventory management

- **Trade Post Block**
  - Public advertisement system
  - Buyers can locate and visit shops
  - Visual rendering with trade listings

- **Server Market**
  - Admin-controlled item marketplace
  - Buy and sell items with coins
  - Dynamic stock management
  - Transaction logging

- **Economy System**
  - Coin-based currency (⬡)
  - `/balance` and `/pay` commands
  - Daily earning/spending tracking
  - HUD display for balance

- **Database Integration**
  - SQLite for persistent data
  - Transaction history
  - Shop listings storage
  - Player economy tracking

### Known Issues (Alpha)
- Limited polish on some GUIs
- Potential edge cases in complex scenarios
- Experimental features may have bugs

---

## Upcoming Features (Planned)
- Trade history viewer
- Advanced filtering for Trade Posts
- Bulk item operations
- Trade templates/presets
- Enhanced security features
- Performance optimizations

---

**Note**: This is an ALPHA release. Features are experimental and may contain bugs. Always backup your world before use!

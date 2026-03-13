# CogTrade

> ⚠️ **ALPHA v1.1.0** — This mod is in early development. Bugs, data loss, and breaking changes are expected. Always back up your world before use.

A player-driven economy mod for **Minecraft 1.20.1 (Fabric)**.
CogTrade adds a server-side market, player trade shops, direct player-to-player trading, and an in-game currency system — all stored in a local SQLite database.

---

## Features

### 🆕 Direct Player Trading (v1.1.0)
- **Live Trade System** — Trade items and coins directly with other players in real-time
- **Modern GUI** — Professional 450×300px interface with color-coded sections
- **Partial Stack Support** — Move entire stacks, single items, or precise quantities
- **Safe & Secure** — Automatic item recovery, anti-dupe protection, mutual confirmation required

### Core Features
- **Server Market** — Admin-managed global shop with configurable items, prices, and stock
- **Trade Depot** — Place your own shop block; link chests as stock storage
- **Trade Post** — Public-facing counter where players can browse and buy from your depot
- **In-game Currency (⬡)** — Economy balances with daily earn/spend tracking
- **Admin Commands** — Manage market listings, player balances, and mod config in-game

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.20.1 |
| Fabric Loader | ≥ 0.16.0 |
| Fabric API | ≥ 0.92.2+1.20.1 |
| Java | ≥ 17 |

SQLite is **bundled** — no extra installation needed.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.20.1
2. Download [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the latest CogTrade `.jar` from [Modrinth](https://modrinth.com/mod/cogtrade)
4. Drop both JARs into your `mods/` folder
5. Launch the game

---

## Admin Commands

```
/ctadmin market add <item> <category> <price> <stock>
/ctadmin market remove <id>
/ctadmin market price  <id> <price>
/ctadmin market stock  <id> <amount>
/ctadmin market edit   <id> <price> <stock>

/ctadmin balance add    <player> <amount>
/ctadmin balance remove <player> <amount>
/ctadmin balance set    <player> <amount>
/ctadmin balance check  <player>

/ctadmin config
/ctadmin config set starting_balance <value>
```

Requires permission level **2** (operator).

---

## Player Commands

### Economy
```
/balance               — Check your balance
/pay <player> <amount> — Send money to a player
```

### Direct Trading (New in v1.1.0)
```
/trade offer <player>  — Send a trade request to another player
/trade accept          — Accept an incoming trade request
/trade reject          — Reject an incoming trade request
/trade cancel          — Cancel your active trade session
```

**Trade Controls:**
- **Left-click** inventory item → Add full stack to offer
- **Right-click** inventory item → Add 1 item to offer
- **Middle-click** offer slot → Open quantity editor for precise control
- **Left-click** offer slot → Return full stack to inventory
- **Right-click** offer slot → Return 1 item to inventory
- Both players must click **"HAZIR"** (Ready) to complete the trade

---

## Building from Source

```bash
git clone https://github.com/Kernel-Developer/cogtrade.git
cd cogtrade
./gradlew build
```

Output JAR will be in `build/libs/`.

---

## License

MIT — see [LICENSE](LICENSE)

---

## Links

- [Modrinth](https://modrinth.com/mod/cogtrade)
- [Issue Tracker](https://github.com/Kernel-Developer/cogtrade/issues)

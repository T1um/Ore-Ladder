# Ore Ladder 1.0.0 (Fabric 1.21.1)

Requires / Нужно: Java 21 (build only), Minecraft 1.21.1, Fabric Loader 0.16+, Fabric API 0.102.0+1.21.1.
The mod must be installed for ALL players (and on the server / LAN host). Мод нужен у ВСЕХ игроков и на сервере/хосте.

## Build / Сборка (Windows PowerShell)
    .\gradlew.bat --stop
    .\gradlew.bat build
Result / Результат: build\libs\oreladder-1.0.0.jar

## Difficulty / Сложность
On the "Create New World" screen (top-right button): Normal / Very Extreme.
На экране создания мира (кнопка справа сверху): Нормальный / Очень Экстримальный.
Dedicated server / выделенный сервер: `/ladder mode normal|extreme` (op, before anyone starts / пока никто не начал).

## Rules / Правила
Ore order / порядок руд: Coal → Copper → Iron → Gold → (Nether Gold, optional) → Redstone → Quartz → Lapis → Diamond → Emerald → Netherite.
- Normal: start 10 FPS, +10 per ore. Very Extreme: start 5 FPS, +5 per ore AND a random permanent restriction per ore (18 kinds).
- Every ore gives a random task; you can't mine the next ore until it's done. Reward: FPS. Failure: −15 FPS + new task.
- Skipping an ore: half-max-health damage and −15 FPS (min 5). Mine coal to get +5 FPS back per block.
- Skipped Nether Gold: after you mine netherite you have 5 minutes to find it.
- Final: kill the Ender Dragon within 30 min of entering the End; then 30 min to put the Dragon Egg and Elytra into the chest at 0, y, 0 (Overworld; the mod creates it).
  Fail = 5 FPS forever (only removing the mod frees you). Success = restrictions removed, unlimited FPS.

## Commands / Команды
/ladder — status · /ladder lang ru|en · /ladder swap <task> (clickable in chat) · /ladder mode normal|extreme (op) · /ladder reset (op)

## About safety / Про безопасность
The mod has no network code except two tiny packets between your own client and server (FPS cap and language code).
It reads/writes only `<world>/oreladder.json`. No files outside the world folder, no web requests, no obfuscation. Source: this repository.
Мод не обращается к интернету. Единственные пакеты — лимит FPS (сервер → клиент) и код языка (клиент → сервер). Пишет только `<мир>/oreladder.json`.

## License
MIT — see LICENSE.

## Bugs / Ошибки
Please open an Issue on GitHub and fill in the template (attach `logs/latest.log`).
Создай Issue на GitHub по шаблону и приложи `logs/latest.log` из папки .minecraft.

# Changes from the original mods

This project is a modified version of **Valkyrien Skies 2.4.11** and **VS Eureka 1.6.4**, combined into one mod. It was changed in November 2026. Source files across `src/` have been modified from the originals, as listed here.

## Porting
- Combined both mods into one Fabric mod
- Updated from Minecraft 1.20.1 to Minecraft 26.3, including the new rendering, networking and world storage code
- Added support for Sodium 0.9.3

## Fixes
- Entities stay on their spot on moving ships, drawn relative to the deck
- Mobs no longer hop on ships, and paths left behind by a moving ship are dropped
- Mobs with no AI are carried by ships
- Players on moving ships stay where they are on the server, whatever their ping
- Players stand on the deck at the helm with their hands on the wheel
- Mouse controls at the helm are no longer reversed
- Entities attached to ships can be reached, clicked and broken
- Ship terrain no longer crashes the game when terrain is drawn with multi draw indirect, which most Windows graphics cards and Vulkan use
- Survival players on moving ships are no longer pulled back by the server's movement check, the same as in 2.4.11
- Players can start gliding with an elytra from a ship
- Ships draw without them for a frame instead of crashing when something goes wrong while drawing them

## New features
- Cushions work on ships, and a cushion in front of a helm is a driver's seat
- Pause All button for every engine on a ship
- Cannon block, with a barrel that can be aimed in any direction
- Small, medium and large cannonball items with their own recipes and explosion power settings; cannons only fire cannonballs
- Crouching and right clicking a helm opens it whatever is in the player's hands
- Cannons can be wired straight to a helm or cushion and fired with a key, no wireless button needed
- Ship Info screen before assembling, with the block count and a 3D view of what would become the ship
- Blocks players place are remembered per chunk, and only those can become a ship, through the helm or VS's assembler items
- `/vs build unassemble` and `/vs build delete`, for all ships or the ones within a distance
- Engine speed depends on the number of engines, the mass and the length of the ship, with new settings for it
- Ships hold themselves level harder at high speed
- Wireless Buttons in 12 wood types
- Wiring screen in the helm, with key binds and contact points
- Automatic import of worlds made by Paper 26.x
- Welcome message with credits

## Removed
- The warning about known issues when joining, and its config option
- The engine settings `engineBoost`, `engineBoostOffset`, `engineBoostExponentialPower` and `maxSpeedFromEngines`, replaced by the new speed settings

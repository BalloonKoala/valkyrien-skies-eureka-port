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

## New features
- Cushions work on ships, and a cushion in front of a helm is a driver's seat
- Pause All button for every engine on a ship
- Cannon block and cannonball
- Wireless Buttons in 12 wood types
- Wiring screen in the helm, with key binds and contact points
- Automatic import of worlds made by Paper 26.x
- Welcome message with credits

## Removed
- The warning about known issues when joining, and its config option

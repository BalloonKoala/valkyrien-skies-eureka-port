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
- Block entities on ships, like helm wheels, cannons, chests and signs, are drawn when Entity Culling is installed
- Ships stop turning when the steering key is let go, also at high speed
- Physics only gets the terrain near ships, so worlds with few or no ships take far less work, and checks that ran for every entity or every frame skip themselves when there are no ships
- A ship loads and is sent to a player as a whole once any part of it is near them. Before, the far end of a ship longer than about 256 blocks never loaded, and the whole ship stayed frozen in the air
- The ground and water under and around every ship stay loaded for its physics, also under the far end of a long ship where no player is near, so it can't sink into hills or lose its water there
- Physics gets the terrain of every loaded chunk near a ship, not only the ticking ones within the simulation distance
- The server only looks at the chunks physics needs each tick, instead of every loaded chunk
- Big ships with a helm (over about 2000 blocks) skip VS's air drag that went over every block every physics step, which took most of the physics thread and made big ships and the people on them stutter
- Engines on big boats get their push from how much of the hull is in water, measured under the ship. VS said almost none for a big boat, so its engines hardly pushed
- Rails, and other blocks a player placed that the helm otherwise leaves behind (stone, dirt, gravel and the like), come along when a ship is assembled. Before, rails on those blocks broke off. A rail on a block that does stay behind stays behind with it

## New features
- Cushions work on ships, and a cushion in front of a helm is a driver's seat
- Pause All button for every engine on a ship
- Cannon block, with a barrel that can be aimed in any direction
- Small, medium and large cannonball items with their own recipes and explosion power settings; cannons only fire cannonballs
- Crouching and right clicking a helm opens it whatever is in the player's hands
- Crouching and left clicking a cannon aims it whatever is in the player's hand, without breaking it
- Cannons can be wired straight to a helm or cushion and fired with a key, no wireless button needed
- Detacher block: on a redstone signal or its key it lets go of what's on its red face, which becomes a ship of its own and gets pushed off half a block to 6 blocks, set with crouch and right click. Nothing happens if that part is still held on another way
- When a Detacher holds nothing, it grabs: a metal pole goes out up to 5 blocks, and another ship it hits is pulled in against the red face and becomes part of the ship until it's let go. Helms on a held ship don't work. The pole never grabs the ground
- Electric Weight block: every redstone pulse switches it, like a copper bulb. Off it weighs as much as an oak log, on as much as a block of netherite (`electricWeightOffMass`, `electricWeightOnMass`)
- Ship Info screen before assembling, with the block count and a 3D view of what would become the ship
- Blocks players place are remembered per chunk, and only those can become a ship, through the helm or VS's assembler items
- `/vs build unassemble` and `/vs build delete`, for all ships or the ones within a distance
- Engine speed depends on the number of engines and the mass of the ship: every ship can reach the same top speed, heavier ones need more engines for it
- How fast the far end of a turning ship can swing round is limited, so long ships turn slower and nobody near the ends gets flung around
- Turning takes running engines: none means no turning, every engine turns a ship faster, a heavier ship less per engine, up to `maxTurnDegrees`. Ships get up to their turning speed and stop again in about half a second, and only lean a little into a turn
- Throttle: W raises it, S lowers it to 0, waits there 0.2 seconds and then goes into reverse. It stays where it is when the keys are let go, also with nobody at the helm, and it's shown above the XP bar of whoever drives. It replaces cruise control
- Ships hold themselves level harder at high speed
- Wireless Buttons in 12 wood types
- Wiring screen in the helm, with key binds and contact points
- Automatic import of worlds made by Paper 26.x
- Welcome message with credits

## Removed
- The warning about known issues when joining, and its config option
- The engine settings `engineBoost`, `engineBoostOffset`, `engineBoostExponentialPower` and `maxSpeedFromEngines`, replaced by the new speed settings
- The settings `smallShipTopSpeed`, `smallShipLength`, `hugeShipTopSpeed` and `hugeShipLength`, replaced by one `maxShipSpeed` for every ship
- The settings `turnSpeed`, `enginePowerAngular` and `enginePowerAngularMin`, replaced by `turnDegreesPerEngine` and `maxTurnDegrees`
- Cruise control, the throttle does its job

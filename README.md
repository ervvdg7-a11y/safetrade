# SafeTrade

A source project for a two-player Paper trading GUI.

## Commands
- `/trade <player>` sends a request.
- The target accepts by running `/trade <requester>`.
- Shift-click items in your player inventory to add them to your offer.
- Click an offered item to return it to your inventory.
- Both players must accept. Any offer change resets both acceptances.
- Close/cancel/disconnect cancels the trade and returns offered items.

## Build
Requires JDK 25 and Maven. From this folder run:

```sh
mvn package
```

Copy `target/SafeTrade-1.0.0.jar` into the server's `plugins` folder and restart.

## Version
Configured for Minecraft 26.3 Paper API (`26.3.build.178-stable`) and Java 25. If your server uses a different 26.3 Paper build, update the `paper-api` version in `pom.xml` to match that build.

## Anti-dupe design / limits
The plugin cancels GUI interactions, blocks cursor/drag/number-key swaps, locks the trade before transferring items, clears the offers before delivery, and returns items on cancellation/disconnect. No plugin can honestly promise zero bugs on every server/mod/plugin combination. Test on a staging server first, especially alongside inventory, economy, or anti-cheat plugins. Report reproducible bugs with logs and steps.

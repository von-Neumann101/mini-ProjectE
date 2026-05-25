# Team EMC MVP Testing

## Build

Run:

```powershell
.\gradlew.bat build
```

## Start Client

Run:

```powershell
.\gradlew.bat runClient
```

## EMC Queries

Run these in game:

```text
/teamemc emc minecraft:cobblestone
/teamemc emc minecraft:furnace
/teamemc emc_stats
```

Expected:

- `minecraft:cobblestone` has EMC `1`.
- `minecraft:furnace` has EMC `8`.
- `emc_stats` reports manual, derived, total, iteration, and completion data.

## Conversion

- Put 1 cobblestone in the input slot and click `Convert`; Team EMC increases by 1.
- Put 3 furnaces in the input slot and click `Convert`; Team EMC increases by 24.
- The input slot is cleared after a successful conversion.
- Closing the GUI after conversion does not return converted items.

## Learned Items And Withdraw

- Converted items appear in the learned item grid.
- Left-click a learned item icon to withdraw 1 item.
- Shift + left-click a learned item icon to withdraw 64 items, or the item max stack size.
- EMC is deducted by item EMC multiplied by withdrawn count.
- Withdrawn items are default ItemStacks without NBT, enchantments, custom names, or custom data components.

## Failure Cases

- Not enough EMC prevents withdraw and does not generate items.
- Full inventory prevents withdraw and does not deduct EMC.
- Unlearned items cannot be withdrawn.
- `teamemc:transmutation_table` and `teamemc:portable_transmutation_table` cannot be withdrawn.
- Items without EMC cannot be placed into the input slot.

## Persistence

- Set a balance and learned items.
- Exit the world.
- Re-enter the world.
- `/teamemc balance` and `/teamemc learned` still show the expected account data.

## Team Sharing

- Put two players on the same scoreboard team.
- Player A converts cobblestone.
- Player B opens a transmutation table and sees the shared Team EMC and learned items.
- Player B can withdraw items learned by Player A.

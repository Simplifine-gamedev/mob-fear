# Mob Fear

A Minecraft Fabric mod that makes mobs react to player dominance. As you hunt and kill mobs, they learn to fear you!

## Features

### Kill Tracking
The mod tracks how many of each mob type you've killed. Your reputation builds up separately for each hostile mob type.

### Hesitation (5+ kills)
After killing 5 or more of a specific mob type, nearby mobs of that type become hesitant:
- Move slower (Slowness II effect)
- Attack less effectively (Weakness effect)
- Display subtle smoke particle effects

### Terror (10+ kills)
After killing 10 or more of a specific mob type, those mobs are terrified of you:
- When close (within 6 blocks), they freeze in fear (Slowness IV)
- When further away, they actively flee from you
- They refuse to target you
- Display soul particle effects showing their terror

### Low Health Vulnerability
When your health drops below 30%:
- All nearby hostile mobs sense your weakness
- They become more aggressive: 30% faster movement and 50% more damage
- They display angry villager particles
- This overrides any fear they might have of you

### Death Reset
When you die, your dominance fades:
- All kill counts reset to zero
- Mobs no longer fear you
- You must rebuild your reputation from scratch

## Mechanics

| Threshold | Effect | Visual |
|-----------|--------|--------|
| 5 kills | Hesitation (slow, weak) | Smoke particles |
| 10 kills | Flee behavior | Soul particles |
| Player < 30% HP | Mob aggression boost | Angry villager particles |
| Player death | All counts reset | - |

- Fear effects work within a 16-block radius
- Each mob type is tracked separately (killing zombies doesn't affect skeleton behavior)
- Works on all hostile mobs

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16.0+
- Fabric API
- Java 21+

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.1
2. Download and install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download `mob-fear-1.0.0.jar` from the releases
4. Place the JAR file in your `mods` folder
5. Launch Minecraft with the Fabric profile

## Building from Source

```bash
git clone https://github.com/Simplifine-gamedev/mob-fear.git
cd mob-fear
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## How It Works

The mod uses Fabric's entity events to track when you kill mobs. It stores kill counts per player (by UUID) and per mob type. Every server tick, it checks nearby hostile mobs and applies appropriate effects based on your kill count for that mob type and your current health percentage.

Status effects are used for the speed/damage modifications:
- Slowness for hesitation/freeze effects
- Weakness for reduced attack power
- Speed for fleeing behavior

Attribute modifiers are used for the aggression boost when the player is at low health.

## Tips

- Focus on one mob type to quickly build up fear
- Be careful when your health is low - mobs will sense your weakness
- Don't die if you want to maintain your dominance!
- The fear system makes farming specific mobs easier over time

## License

MIT License - Feel free to use, modify, and distribute.

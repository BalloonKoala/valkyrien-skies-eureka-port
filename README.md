# Valkyrien Skies + Eureka Unofficial Port

An unofficial Fabric port of Valkyrien Skies 2.4.11 and VS Eureka 1.6.4, combined into one mod for Minecraft 26.3, with new features. See [CHANGES.md](CHANGES.md) for everything that's different from the originals.

**This is not made or supported by the Valkyrien Skies team.** Please don't send them bug reports for this version. For help, [join the Discord](https://discord.gg/nbZ9nH3aBb).

## Credits

- [Valkyrien Skies 2](https://github.com/ValkyrienSkies/Valkyrien-Skies-2) by Tri0de and Rubydesic
- [Eureka](https://github.com/ValkyrienSkies/Eureka) by ewoudje, Tri0de, Rubydesic and millennIumAMbiguity
- With help from Copper, Anarava and the VS Discord

## License

- This project is licensed under **LGPL-3.0-only**, see [LICENSE](LICENSE) and [LICENSE-GPL-3.0.txt](LICENSE-GPL-3.0.txt).
- The Eureka code stays under **Apache-2.0**, see [LICENSE-EUREKA.txt](LICENSE-EUREKA.txt).
- VS Core and the Krunch physics engine are closed source, belong to the Valkyrien Skies team and aren't part of this source. See [libs/README.md](libs/README.md).

## Building

You need JDK 25, Gradle 9.8 and the VS Core bundle described in [libs/README.md](libs/README.md).

```
gradle build -x test
```

The mod ends up in `build/libs`.

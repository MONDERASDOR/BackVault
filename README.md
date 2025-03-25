# BackVault - Advanced Minecraft Server Optimization Plugin

![GitHub](https://img.shields.io/github/license/MONDERASDOR/BackVault)
![GitHub issues](https://img.shields.io/github/issues/MONDERASDOR/BackVault)

BackVault is a high-performance optimization plugin designed for PaperMC servers that reduces lag without compromising gameplay. It intelligently manages server resources by optimizing entities, chunks, redstone, and more behind the scenes.

## ✨ Features

### Advanced Performance Optimizations
- **Smart Chunk Management**: Unloads inactive chunks based on player activity
- **Dynamic View Distance**: Automatically adjusts render distance based on server TPS
- **Entity AI Control**: Disables AI for mobs far from players
- **Redstone Optimization**: Throttles unnecessary redstone updates

### Resource Management
- **Item Stack Merging**: Combines nearby identical items to reduce entity count
- **Tile Entity Limits**: Prevents chunk overload from excessive tile entities
- **Hopper Optimization**: Reduces hopper checks in unloaded chunks

### Intelligent Systems
- **TPS-Based Adjustments**: Automatically scales optimizations based on server health
- **Asynchronous Processing**: Handles intensive tasks off the main thread
- **Configurable Limits**: Fine-tune every optimization parameter

## 📥 Installation

1. Download the repo or use `git clone https://github.com/MONDERASDOR/BackVault`. (requires JDK and Gradle Build to build
2. After Compiling The Plugin. Place the `.jar` in your server's `plugins` folder. 
3. Restart your server
4. Customize settings in `plugins/BackVault/config.yml`

## ⚙️ Configuration

The plugin comes with sensible defaults, but you can fine-tune every aspect:

```yaml
optimizations:
  redstone-optimization: true  # Disables unnecessary redstone updates
  hopper-optimization: true    # Optimizes hopper item transfers
  merge-items: true            # Combines nearby identical items

entity-limits:
  per-chunk: 25                # Max entities per chunk
  mobs: 15                     # Max mobs per chunk
  items: 50                    # Max dropped items per chunk

view-distance:
  base: 6                      # Default view distance
  min: 4                       # Minimum when TPS is low
  max: 8                       # Maximum when TPS is high
```

## 🎯 Commands

- `/backvault reload` - Reloads the configuration (requires `backvault.reload` permission)

## 📊 Metrics Integration

BackVault supports [bStats](https://bstats.org/) metrics (opt-out available) to help track performance improvements and usage statistics.

## 🤝 Contributing

Contributions are welcome! Please open an issue or pull request on our [GitHub repository](https://github.com/MONDERASDOR/BackVault).

## 📜 License

BackVault is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

**Created with ❤️ by Sunpowder**  
[Report Issues](https://github.com/MONDERASDOR/BackVault/issues) 

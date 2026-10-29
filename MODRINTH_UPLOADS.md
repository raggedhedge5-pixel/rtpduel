# Uploading RTP Duels to Modrinth

You build ONE jar (`RTPDuels.jar`, from the GitHub Actions artifact `RTPDuels`). The same file goes on every upload
below - only the tick-boxes (loaders and game versions) differ. Your existing project is
https://modrinth.com/plugin/rtp-duels - add these as new versions on it.

Before you tick anything, run the **Test on real servers** workflow once (Actions tab). A version that shows a red cross
there should be left unticked until it is fixed; a version shown as "skipped" simply has no server download to test on.

## Upload 1 - Bukkit / Spigot / Paper (all versions)

- **Version number:** `1.1.0`
- **Loaders:** Bukkit, Spigot, Paper (add Purpur too if you like - it loads Paper plugins unchanged)
- **Game versions (75):** 1.8, 1.8.1, 1.8.2, 1.8.3, 1.8.4, 1.8.5, 1.8.6, 1.8.7, 1.8.8, 1.8.9, 1.9, 1.9.1, 1.9.2, 1.9.3, 1.9.4, 1.10, 1.10.1, 1.10.2, 1.11, 1.11.1, 1.11.2, 1.12, 1.12.1, 1.12.2, 1.13, 1.13.1, 1.13.2, 1.14, 1.14.1, 1.14.2, 1.14.3, 1.14.4, 1.15, 1.15.1, 1.15.2, 1.16, 1.16.1, 1.16.2, 1.16.3, 1.16.4, 1.16.5, 1.17, 1.17.1, 1.18, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3
- **Dependencies:** PlaceholderAPI - *optional* (only for showing the TimeSMP rank in the GUI)

## Upload 2 - Folia

Folia only exists for newer versions, so list it separately instead of ticking Folia on 1.8:

- **Version number:** `1.1.0-folia`
- **Loaders:** Folia
- **Game versions (25):** 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3
- Same file as Upload 1.

If you would rather have a single upload, tick all four loaders and all versions on one version - it works, but
the page will then claim Folia on versions that never had a Folia build.

## Changelog text you can paste

> Now runs on Bukkit, Spigot, Paper and Folia, on Minecraft 1.8 up to 26.3, with one jar (Java 8+).
> Behaviour is unchanged: same chat buttons, confirmation GUI, countdowns, land-only random spot, command lock and
> teleport protection. On Folia all actions run on the correct region threads and the teleport is asynchronous.
> On Paper the random spot is now loaded asynchronously, so far-away chunks no longer pause the server.
> The plugin is now called RTPDuels (config folder `plugins/RTPDuels/`); permissions are unchanged.

## Edits to the project page

- **Requirements:** replace "Paper 1.21+ and Java 21" with "Bukkit, Spigot, Paper or Folia - Minecraft 1.8 to 26.3 - Java 8 or newer".
- Everything else on the page (commands, permissions, features) stays accurate.

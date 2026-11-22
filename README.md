# TakeOutFilter

Paper 1.21.11 chat filter for slurs, bad words and server promotion. Obfuscation proof (leetspeak, symbols, spaced or
stretched letters, look-alike unicode). On a hit: message is blocked -> sound + image -> image disappears -> ban.

## Setup
1. Build the plugin (`mvn clean package`, or let GitHub Actions do it) and put the jar in `plugins/`.
2. Upload `TakeOutFilter-ResourcePack.zip` somewhere with a DIRECT download link (a GitHub Release asset works).
3. Paste the link into `config.yml` -> `resource-pack.url`. The sha1 is worked out automatically. Restart.
4. Players are asked to accept the pack on join (forced). Decline = kick.
5. Bans run `sentient ban %player% %duration% %reason%`, so the Sentient Anti-Cheat ban screen is shown.

## Commands (permission `takeout.admin`)
- `/takeout test` - plays the sound + image on you (no ban)
- `/takeout test ban [category]` - same, then bans YOU (default 1m). Undo with `/takeout unban <you>`
- `/takeout unban <player>` - runs the unban command
- `/takeout check <text>` - shows what a text would trigger, without punishing
- `/takeout pack [player]` - resend the resource pack
- `/takeout reload`

Permissions: `takeout.notify` (staff alerts), `takeout.bypass` (never filtered).
Set `dry-run: true` to only notify staff while you tune the lists.

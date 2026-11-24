<h2><strong>WallJump Unbound</strong></h2>

Wall jump, double jump, ledge grab, fence jump & more, on the ground, on moving **Valkyrien Skies** ships and on **Create** contraptions!

WallJump Unbound is a fork of [Wall-Jump TXF](https://github.com/jahirxtrap/walljump) (by jahirxtrap, originally by genandnic), formerly WallJumpVS. It lets you use every wall-jump move on the hulls of moving [Valkyrien Skies](https://modrinth.com/mod/valkyrien-skies) ships and on Create's moving contraptions. Cling to a ship wall and the game follows the ship as it moves and turns; cling too long and you'll slide down, just like on regular walls.

<strong>This mod adds:</strong>

<strong>Wall Cling:</strong> Jump towards a wall and hold the wall jump key (LSHIFT)

<strong>Wall Jump:</strong> While wall clinging, keep holding W and let go of LSHIFT to wall jump

<strong>Ledge Grab:</strong> Grab the top of a wall within arm's reach and hang there; a ledge hold never slides, and turning more than 90 degrees away lets go (`ledgeGrab`, server)

<strong>Double Jump:</strong> Optional mid-air jump (`doubleJumpEnabled`, off by default; or the Double Jump enchantment)

<strong>Speed Boost:</strong> Boosts your speed while sprinting or flying with elytras

<strong>Steep Assist:</strong> Walk up steps even while airborne, also jump over fences

<strong>Enchantments:</strong> Wall Jump, Double Jump and Speed Boost, each switchable (`enableEnchantments`, off by default, gates those three), and Re-cling (I to III: catch the wall you just jumped off again, once per level, before you land), on by default with its own switch (`reClingEnchantment`)

<strong>Valkyrien Skies support:</strong> All of the above works on VS ship walls, including moving and rotating ships

<strong>Create support:</strong> Cling to and wall jump off a moving contraption (a bearing's arm, a train); it carries you and turns your view with it (`[create]` section)

<strong>Any gravity:</strong> Works under Gravity Unbound's fields, in VS Genesis' space and on Ad Astra's planets: in zero gravity a cling never slides and a wall jump pushes off along the wall's normal; on the Moon you slide a sixth as fast

<strong>Wall slide sounds and cling fall damage:</strong> sliding scrapes the block you are on, and catching a wall after a long fall costs a share of the fall damage (configurable)

<strong>Block list:</strong> blacklist or whitelist blocks by id, <code>#tag</code>, <code>*wildcard</code> or <code>/regex/</code>

<strong>Servers and minigames:</strong> a host can switch the moves off for a player, or only the double jump, or grant double jumps to a player who lacks the enchantment (through Camafy's match policies: <code>walljumpunbound:wall_jump</code>, <code>walljumpunbound:double_jump</code>, <code>walljumpunbound:double_jumps</code>; Game Start's <code>wall_jump</code> and <code>double_jump</code> rules use them). The server checks the moves' messages.

Every option is explained right next to its value in <code>config/walljumpunbound.toml</code>; options marked <code>[server]</code> are sent from the server to its players.

<strong>Requirements:</strong>

- Minecraft 1.20.1 with Forge 47.4 or newer, on the server and every client
- <a href="https://www.curseforge.com/minecraft/mc-mods/camafy" target="_blank">Camafy</a> 0.1.95 or newer <strong>(Required)</strong>
- <a href="https://modrinth.com/mod/valkyrien-skies" target="_blank">Valkyrien Skies</a> <strong>(Optional)</strong>: needed only for ship wall jumping; the mod works fine without it
- <a href="https://www.curseforge.com/minecraft/mc-mods/create" target="_blank">Create</a> 6.0 <strong>(Optional)</strong>: contraption wall jumping

<strong>Credits:</strong> genandnic (original Wall-Jump!), jahirxtrap (Wall-Jump TXF)

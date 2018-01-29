Bot for Halite 2 competition (http://halite.io) written in Kotlin, employing hand-coded human strategies. It is based-off the [Kotlin starter kit](https://github.com/HaliteChallenge/Halite-II/tree/master/airesources/Kotlin).

# Design

Bot is driven by **objectives** which are assigned **scores** on every turn. The scores depend on various game statistics such as number of enemies, current turn number etc. as well as environmental stats, such as number of enemies in proximity of given planets.

The objectives also have an **assignemnt quota**, maximum number of ships that can be assigned to it. The assignment is also recomputed every turn based on similar information as above. This helps with sending more ships to attack or settle planets surrounded by enemies.

The **Commander** module then updates these objectives and assigns ships to objectives based on their proximity. Ships may be pulled off and re-assigned to different objectives every turn. 

The **Executor** then maneuvers the ships depending on their current objective. The navigation code is adapted from the original one in the starter bot.

# Strategies

 - In general, the bot will first focus on settling over attacking and swap the priorities of the two as less planets are available.
 - Planets that would keep the current "empire" in a roughly circular shape are favoured and settled first. 
 
## 4 Player game

 - the bot prefers to attack enemies that have more ships as they pose a bigger threat
 - if the bot detects it's losing, it will hide a couple of ships in corners, in hopes of securing a 2nd place
 
## 2 Player games

 - if the enemy is close enough, the bot will attempt to wipe them out immediately instead of settling

# Navigation

 - Navigation is basically the same as in the starter bot, but with collision detection against future positions of ships. This has the unexpectedly good side-effect of spreading the ships apart when they travel together. It also lets all ships to always run at top speed.
 - I attempted a more efficient, vector-based collision detection but that somehow performed worse. You can find it [here](https://github.com/macduy/halite2-bot/blob/master/src/kotlin/halite/Navigation.kt#L67)

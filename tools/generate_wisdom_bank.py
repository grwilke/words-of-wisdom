#!/usr/bin/env python3
"""Generates and validates core/src/main/resources/lessons/wisdom_bank.json.

Contains 120 curated life lessons across 10 thematic clusters, each paired with
5 thematically adjacent (yet semantically distinct) confusing decoys, 3 imagery
sparks for writer's block, and a validated witty metaphor for AI bots & sudden-death
tie-breakers.
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

STOP_WORDS = {
    "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
    "to", "of", "in", "on", "at", "by", "for", "with", "from", "as", "into",
    "and", "or", "but", "nor", "so", "yet", "if", "than", "that", "this",
    "these", "those", "it", "its", "you", "your", "yourself", "we", "our",
    "they", "their", "them", "he", "him", "his", "she", "her", "who", "whom",
    "what", "which", "when", "where", "why", "how", "can", "cannot", "cant",
    "could", "will", "would", "shall", "should", "may", "might", "must",
    "do", "does", "did", "dont", "doesnt", "didnt", "not", "no", "never",
    "always", "every", "all", "one", "ones", "only", "just", "more", "most",
    "less", "very", "too", "own", "have", "has", "had", "make", "makes",
    "made", "take", "takes", "give", "gives", "get", "gets", "go", "goes",
    "come", "comes", "see", "sees", "know", "knows", "even", "still", "much",
    "many", "some", "any", "each", "other", "another", "before", "after",
    "without", "within", "through", "over", "under", "about", "out", "up",
    "down", "off", "away", "often", "sometimes", "there", "here", "then",
    "until", "while", "because", "although", "though", "since", "unless",
    "whether", "either", "neither", "both", "such", "same", "also", "like",
    "back", "keep", "keeps", "kept", "put", "puts", "let", "lets", "still",
}


def stem(word: str) -> str:
    w = re.sub(r"[^a-z]", "", word.lower())
    if len(w) <= 3:
        return w
    for suffix in ("iness", "ingly", "edly", "ment", "ness", "tion", "sion", "able", "ible", "less", "ful", "ing", "ies", "ied", "ers", "est", "ly", "ed", "es", "s"):
        if w.endswith(suffix) and len(w) - len(suffix) >= 3:
            base = w[: -len(suffix)]
            if suffix in ("ies", "ied", "iness"):
                base += "y"
            return base
    return w


def content_stems(text: str) -> set[str]:
    words = re.findall(r"[A-Za-z']+", text.lower())
    out = set()
    for w in words:
        clean = w.replace("'", "")
        if clean in STOP_WORDS or len(clean) < 3:
            continue
        out.add(stem(clean))
    return out


# 10 Themes x 12 Lessons = 120 vivid, curated life lessons
RAW_THEMES: dict[str, list[tuple[str, str, list[str], list[str]]]] = {
    "Connection": [
        (
            "Nobody can be happy alone",
            "A single scarecrow in the field only smiles when he sees the crows",
            ["a lone scarecrow", "a solo violin", "an empty dinner table"],
            [
                "True peace begins when you enjoy your own quiet company",
                "Good friends show up fastest when the skies turn gray",
                "Distance slowly reveals who genuinely belongs in your life",
                "Listening closely is the rarest gift you can offer someone",
                "Shared memories outlast every treasure money can buy",
            ],
        ),
        (
            "True friends show up when times get hard",
            "Fair-weather umbrellas fold up the second the real storm hits the porch",
            ["umbrellas in rain", "campfires at night", "boats in a storm"],
            [
                "Nobody can be happy alone",
                "Forgiving an old friend lifts a heavy weight off your chest",
                "A small circle of loyal allies beats a crowded room of strangers",
                "You become just like the five people sitting closest to you",
                "Honest criticism from a companion beats flattery from a crowd",
            ],
        ),
        (
            "You become like the company you keep",
            "A white pebble resting in blackberry jam eventually turns deep purple",
            ["pebbles in ink", "apples in a basket", "birds flying together"],
            [
                "Good listeners learn far more than loud talkers ever do",
                "Kindness toward strangers quietly circles back to your doorstep",
                "Even the strongest bridge needs two solid banks to stand",
                "Old friendships survive long silences without losing warmth",
                "Choosing your circle carefully protects your future self",
            ],
        ),
        (
            "Listening teaches more than talking ever will",
            "A closed teacup catches no rain while a wide bowl fills to the brim",
            ["teacups in rain", "quiet owls", "tuned radios"],
            [
                "Speaking the truth gently opens doors that shouting slams shut",
                "Words spoken in anger cannot be gathered back like spilled feathers",
                "Silence between close friends feels warmer than nervous chatter",
                "Every person carries a story you know nothing about",
                "Asking curious questions turns strangers into allies",
            ],
        ),
        (
            "A shared burden feels half as heavy",
            "Four ants strolling under one giant crumb turn a boulder into lunch",
            ["ants with a crumb", "two oxen yoked", "rowing a canoe"],
            [
                "Asking for help is a sign of strength rather than weakness",
                "Trying to please everyone leaves nobody truly satisfied",
                "Small daily favors build the sturdiest bridges between neighbors",
                "Gratitude turns an ordinary meal into a royal feast",
                "Working beside someone reveals their character faster than lunch",
            ],
        ),
        (
            "Distance reveals who truly cares about you",
            "When the bakery moves across town, you learn who actually craves your bread",
            ["a bakery moving", "tides going out", "migrating birds"],
            [
                "Staying too close to comfort keeps you from seeing the world",
                "Time heals old wounds only if you stop picking at them",
                "True friends show up when times get hard",
                "Letting go of one-sided bonds makes room for warmer ones",
                "Home is built by the people inside it rather than the walls",
            ],
        ),
        (
            "Trying to please everyone leaves you exhausted",
            "A compass spinning to point toward every hiker in the woods loses north",
            ["a spinning compass", "a chameleon on plaid", "a waiter with twelve trays"],
            [
                "Saying no kindly protects the promises you already made",
                "Nobody can be happy alone",
                "Comparing your journey to your neighbor steals your joy",
                "Staying authentic attracts the people meant for you",
                "Even the sweetest peach cannot win over someone who hates fruit",
            ],
        ),
        (
            "Words spoken in anger are impossible to take back",
            "Once you squeeze the whole tube of toothpaste onto the counter, no spoon puts it back",
            ["toothpaste squeezed out", "nails in a wooden fence", "feathers in the wind"],
            [
                "Forgiving someone sets your own heart free before theirs",
                "Pausing for one deep breath prevents a week of regret",
                "Listening teaches more than talking ever will",
                "Honesty without warmth lands like a brick instead of a gift",
                "Small misunderstandings grow into walls when nobody knocks",
            ],
        ),
        (
            "Every stranger is fighting a hidden battle",
            "Even the calmest swan gliding across the pond kicks furiously beneath the surface",
            ["a swan paddling", "icebergs at sea", "closed suitcases"],
            [
                "First impressions rarely tell the second chapter of a person",
                "Patience with irritable people often melts their armor",
                "You can judge character by how someone treats people with no power",
                "Kindness costs nothing yet stays remembered for decades",
                "Walking a mile in worn boots softens harsh opinions",
            ],
        ),
        (
            "Family is chosen by love as much as blood",
            "A grafted cherry branch blooms just as bright as the roots beneath it",
            ["a grafted tree branch", "a patchwork quilt", "birds sharing a nest"],
            [
                "Home feels warmest wherever someone leaves the porch light on",
                "Old traditions stay alive when each generation adds a spark",
                "Blood ties still need regular watering to keep growing",
                "Nobody can be happy alone",
                "Shared laughter heals rivalries faster than apologies",
            ],
        ),
        (
            "Asking for help is a sign of strength",
            "Only a stubborn captain refuses a tugboat while drifting straight into the reef",
            ["tugboats and ships", "a climber's rope", "borrowing a lantern"],
            [
                "A shared burden feels half as heavy",
                "Learning from someone else's map saves miles of blistered feet",
                "Pride builds tall walls that keep rescuers outside",
                "True independence still knows when to accept a spare oar",
                "Admitting what you do not know starts real wisdom",
            ],
        ),
        (
            "Love grows deeper through tiny daily habits",
            "A brick cathedral rises from humble trowels of mortar, never from one grand speech",
            ["trowels of mortar", "watering a bonsai", "tending hearth embers"],
            [
                "Grand gestures fade fast when everyday respect is missing",
                "Patience keeps small sparks from burning out too soon",
                "Showing up consistently matters more than dazzling once a year",
                "Listening teaches more than talking ever will",
                "Forgiveness keeps little pebbles from turning into boulders",
            ],
        ),
    ],
    "Patience": [
        (
            "Good things take time to grow",
            "Yanking on a carrot top every afternoon only guarantees a very short salad",
            ["pulling a sprout", "baking sourdough", "aging violins"],
            [
                "Rushing through work forces you to build everything twice",
                "Starting small today beats waiting for a mythical perfect Monday",
                "Quiet consistency outperforms bursts of frantic energy",
                "The sweetest fruit hangs highest on the orchard branch",
                "Every master once stumbled through their clumsy first week",
            ],
        ),
        (
            "Rushing a job usually means doing it twice",
            "A painter who slaps on wet varnish in a hurricane winds up sanding off pine needles",
            ["wet paint in wind", "half-baked bread", "tangled shoelaces"],
            [
                "Good things take time to grow",
                "Measuring carefully before cutting saves both timber and temper",
                "Shortcuts through the swamp often take longer than the paved road",
                "Slow steady steps finish marathons while sprinters gasp at mile two",
                "Rest is part of the craft rather than the enemy of progress",
            ],
        ),
        (
            "Storms always pass eventually",
            "No cloud in history has ever signed a permanent lease on the sky",
            ["clouds renting the sky", "winter melting to spring", "waves calming at dusk"],
            [
                "Hard seasons teach skills that sunny afternoons never demand",
                "Worrying about tomorrow ruins the sunshine sitting in your lap today",
                "Night always feels coldest right before the horizon turns pink",
                "Waiting calmly beats panicking inside a leaky rowboat",
                "Time softens sharp grief into bearable memory",
            ],
        ),
        (
            "Small daily steps beat giant rare leaps",
            "A dripping faucet hollows granite faster than a single thunderclap ever could",
            ["water carving stone", "a tortoise climbing stairs", "stacking pennies"],
            [
                "Good things take time to grow",
                "Showing up on boring Tuesdays builds extraordinary results",
                "Perfectionism stalls more dreams than laziness ever did",
                "Direction matters far more than raw running speed",
                "One quiet hour of practice beats ten hours of daydreaming",
            ],
        ),
        (
            "Timing matters as much as effort",
            "Planting watermelon seeds during a January blizzard wastes a perfectly good shovel",
            ["planting in snow", "surfing a wave", "flipping a pancake"],
            [
                "Waiting for the right moment is not the same as procrastinating",
                "Luck favors the sailor who already mended their sails",
                "Opportunities knock softly and rarely ring the doorbell twice",
                "Rushing a job usually means doing it twice",
                "Knowing when to pause saves you from charging down a blind alley",
            ],
        ),
        (
            "Worrying today never fixes tomorrow",
            "Rocking furiously in a wooden porch chair burns sweat without leaving the veranda",
            ["a rocking chair", "paying interest twice", "carrying an umbrella indoors"],
            [
                "Storms always pass eventually",
                "Focusing on what your hands can reach calms a racing mind",
                "Most monsters we rehearse at midnight never show up at noon",
                "Planning calmly beats rehearsing disaster on repeat",
                "Enjoying the present minute is harder than chasing the next one",
            ],
        ),
        (
            "Rest is necessary for lasting energy",
            "Even the sharpest lumberjack axe turns dull if it never visits the whetstone",
            ["sharpening an axe", "unstringing a bow", "fallow farm fields"],
            [
                "Small daily steps beat giant rare leaps",
                "Pushing past exhaustion trades tomorrow's clarity for today's haste",
                "A quiet mind spots answers that a frantic schedule misses",
                "Good things take time to grow",
                "Pacing yourself lets you enjoy the view along the climb",
            ],
        ),
        (
            "Wait until anger cools before deciding",
            "Never buy groceries while starving or sign contracts while the kettle screams",
            ["a boiling kettle", "muddy pond water settling", "cooling iron"],
            [
                "Words spoken in anger are impossible to take back",
                "Sleeping on a tough dilemma shrinks giant dragons into lizards",
                "Calm minds negotiate better outcomes than clenched fists",
                "Timing matters as much as effort",
                "Silence during a heated argument is often the wisest move",
            ],
        ),
        (
            "Shortcuts often end up taking longer",
            "Hopping the orchard fence to skip the gate usually introduces you to the guard dog",
            ["hopping a fence", "untying knots fast", "cutting across a marsh"],
            [
                "Rushing a job usually means doing it twice",
                "Solid foundations stay invisible until the first earthquake arrives",
                "Cheap bargains frequently cost double by the end of the year",
                "Easy promises hide the steepest hidden fees",
                "Learning the basics properly unlocks every trick later on",
            ],
        ),
        (
            "Time reveals what haste tries to hide",
            "Only after the snow melts do you spot every rake forgotten on the lawn",
            ["melting snow", "low tide on a beach", "developing a photograph"],
            [
                "First impressions rarely capture a person's whole character",
                "Good things take time to grow",
                "Patience lets the truth walk through the front door on its own",
                "Real friendships outlive seasonal enthusiasm",
                "Watching actions over months beats trusting speeches in one night",
            ],
        ),
        (
            "You cannot hurry someone else's healing",
            "Prying open a cocoon with tweezers ruins the wings before they ever dry",
            ["opening a cocoon", "tugging a bandage", "forcing a flower bud"],
            [
                "Storms always pass eventually",
                "Sitting quietly beside a hurting friend helps more than fixing them",
                "Everyone walks out of winter on a different calendar",
                "Good things take time to grow",
                "Forgiveness arrives on its own schedule rather than by command",
            ],
        ),
        (
            "Plant seeds today for shade tomorrow",
            "Grandmothers tuck acorns into hillside soil for picnics they will never attend",
            ["planting an oak", "digging a well", "saving lanterns for winter"],
            [
                "Small daily steps beat giant rare leaps",
                "Generosity echoes further down the road than we can see",
                "Preparing calmly during summer keeps winter from feeling cruel",
                "Good things take time to grow",
                "Legacy lives in what stays blooming after you leave the room",
            ],
        ),
    ],
    "Courage": [
        (
            "Fear shrinks the moment you face it",
            "The giant bedroom monster turns back into a bathrobe the instant you flick the lamp",
            ["a bathrobe monster", "a long shadow at dusk", "jumping into cold water"],
            [
                "Bravery means walking forward even while your knees knock",
                "Staying safe in the harbor wastes a ship built for oceans",
                "Regret hurts far longer than temporary embarrassment",
                "One bold step breaks a year of overthinking",
                "Running away from hard conversations makes them louder later",
            ],
        ),
        (
            "You miss every chance you refuse to take",
            "A fisherman who keeps his hook inside his pocket never argues about the one that got away",
            ["unthrown dice", "a folded kite", "an unmailed letter"],
            [
                "Fear shrinks the moment you face it",
                "Failing loudly teaches more than watching safely from the bleachers",
                "Waiting for guaranteed certainty means watching the train pull away",
                "Small experiments lower the price of bold dreams",
                "Luck usually meets the people who actually knocked on the door",
            ],
        ),
        (
            "Comfort zones rarely produce new discoveries",
            "A ship anchored forever in the cove stays shiny while never glimpsing a single island",
            ["a ship in harbor", "a goldfish in a bowl", "unworn hiking boots"],
            [
                "Change feels frightening right before it starts feeling liberating",
                "You miss every chance you refuse to take",
                "Leaving familiar habits reveals muscles you never knew you owned",
                "Curiosity carries you further than fear ever could",
                "Every expert started by feeling awkward in a brand new room",
            ],
        ),
        (
            "Regret lasts longer than brief embarrassment",
            "A red cheek fades before dessert, while an unasked dance haunts every wedding song",
            ["a blush vs a fossil", "an untouched diving board", "a silent microphone"],
            [
                "Fear shrinks the moment you face it",
                "Other people forget your awkward slip ten minutes after it happens",
                "Saying what you mean beats rehearsing clever speeches in the shower",
                "You miss every chance you refuse to take",
                "Laughing at your own stumble steals the sting from every mistake",
            ],
        ),
        (
            "Standing alone for right takes real grit",
            "A single salmon swimming upstream looks foolish to every twig drifting with the current",
            ["a salmon upstream", "a lighthouse in fog", "one candle in a cave"],
            [
                "Following the crowd blindly leads straight off comfortable cliffs",
                "Quiet integrity matters most when nobody is holding a camera",
                "Speaking gently works better than picking noisy fights",
                "True leaders absorb heat instead of passing blame downward",
                "Courage shows up in small daily honesty more than dramatic battles",
            ],
        ),
        (
            "Starting is the hardest part of any quest",
            "A freight locomotive burns half its coal just rolling the wheels their first inch",
            ["a locomotive starting", "a blank canvas", "stepping off the porch"],
            [
                "Small daily steps beat giant rare leaps",
                "Imperfect action today beats a flawless blueprint next year",
                "Momentum carries you once friction finally lets go",
                "You miss every chance you refuse to take",
                "Finishing what you began earns twice the pride of starting",
            ],
        ),
        (
            "Bravery means acting while still afraid",
            "A tightrope walker does not wait for butterflies to vanish; she teaches them to march in line",
            ["butterflies marching", "a trembling knight", "a sparrow in gale winds"],
            [
                "Fear shrinks the moment you face it",
                "Recklessness ignores danger while wisdom packs a parachute",
                "Confidence grows after you jump rather than before",
                "Standing alone for right takes real grit",
                "Calm preparation shrinks panic when trouble knocks",
            ],
        ),
        (
            "Failure is a teacher, not a verdict",
            "A burnt first pancake simply tells the chef that the skillet is finally hot enough",
            ["the first pancake", "a scraped knee", "a crumpled blueprint"],
            [
                "Every master once collected a drawer full of crooked sketches",
                "Falling down only turns into defeat when you decide to live on the floor",
                "Blaming the tools keeps you from fixing your grip",
                "You miss every chance you refuse to take",
                "Honest mistakes pave the shortest road to real skill",
            ],
        ),
        (
            "Big doors swing on very small hinges",
            "A tiny brass key in your coat pocket unlocks a vault taller than your house",
            ["a tiny brass key", "a pebble starting a slide", "one match lighting a hall"],
            [
                "One polite introduction can change the arc of a decade",
                "Small daily steps beat giant rare leaps",
                "Attention to minor details separates sturdy work from fragile junk",
                "Starting is the hardest part of any quest",
                "Saying yes to one strange invitation opens hidden corridors",
            ],
        ),
        (
            "Saying no protects what truly matters",
            "A gardener snips stray rosebuds so the main bloom drinks the whole summer sun",
            ["pruning rosebuds", "a bouncer at a velvet rope", "packing a light backpack"],
            [
                "Trying to please everyone leaves you exhausted",
                "Busy calendars often disguise empty priorities",
                "Clear boundaries build healthier friendships than silent resentment",
                "Chasing two rabbits at once sends both hopping into the bushes",
                "Protecting your focus multiplies the quality of every hour",
            ],
        ),
        (
            "Doubt kills more dreams than failure ever has",
            "Half the kites in the closet never tore in the wind; they simply never left the shelf",
            ["kites in a closet", "an unplayed piano", "unopened paint jars"],
            [
                "You miss every chance you refuse to take",
                "Failure is a teacher, not a verdict",
                "Comparing your rough draft to someone else's showcase drains hope",
                "Fear shrinks the moment you face it",
                "Trusting your voice gets louder each time you test it",
            ],
        ),
        (
            "Smooth seas never made a skilled sailor",
            "A kite only climbs above the treetops when the breeze pushes firmly against its nose",
            ["a kite against wind", "forged steel in fire", "roots gripped in gale"],
            [
                "Storms always pass eventually",
                "Comfort zones rarely produce new discoveries",
                "Hard puzzles sharpen wits that easy trivia leaves sleepy",
                "Failure is a teacher, not a verdict",
                "Overcoming one tough hill makes the next mountain look friendlier",
            ],
        ),
    ],
    "Humility": [
        (
            "Pride comes right before a hard fall",
            "A peacock strutting with eyes fixed on his own feathers never notices the open cellar door",
            ["a strutting peacock", "a tall snowman in April", "an overinflated balloon"],
            [
                "Admitting mistakes quickly earns more respect than defending them",
                "Boasting loudly usually hides an empty suitcase underneath",
                "Even the tallest cedar still depends on muddy unseen roots",
                "Modesty lets your craftsmanship introduce itself first",
                "Success tests your character far harder than struggle does",
            ],
        ),
        (
            "The wisest people know how little they know",
            "The bigger your campfire burns in the dark forest, the wider the ring of shadows around it",
            ["campfire shadows", "an ocean from a teacup", "a full stalk of wheat bowing"],
            [
                "Curiosity keeps your mind young long after diplomas yellow",
                "Listening teaches more than talking ever will",
                "Changing your mind when shown proof shows real strength",
                "Empty wagons rattle loudest along the cobblestone road",
                "Every person you meet knows something you have not learned yet",
            ],
        ),
        (
            "Admitting a mistake earns deeper respect",
            "Patching a leaky roof right away beats insisting your living room needed an indoor pond",
            ["patching a roof", "turning back on a wrong trail", "erasing a smudged note"],
            [
                "Pride comes right before a hard fall",
                "Excuses take twice as much energy as an honest apology",
                "Nobody trusts a pilot who claims the compass is always lying",
                "Failure is a teacher, not a verdict",
                "Owning your slip turns critics into teammates",
            ],
        ),
        (
            "Empty vessels make the loudest noise",
            "A tin can rolling hollow down an alley drowns out a gold coin resting silently in velvet",
            ["a hollow tin can", "a barking teacup dog", "thunder without rain"],
            [
                "The wisest people know how little they know",
                "Quiet confidence needs no megaphone to command a room",
                "Actions speak louder than polished promises",
                "Listening teaches more than talking ever will",
                "True skill looks effortless until you try holding the brush yourself",
            ],
        ),
        (
            "Everyone you meet can teach you something",
            "Even a tiny field mouse knows secret tunnels beneath the barn that the eagle overhead misses",
            ["a mouse and an eagle", "a pocket mirror", "a worn cookbook"],
            [
                "The wisest people know how little they know",
                "Respecting beginners keeps experts from growing blind",
                "Every stranger is fighting a hidden battle",
                "Curiosity unlocks rooms that arrogance walks straight past",
                "Listening closely uncovers treasure in ordinary chatter",
            ],
        ),
        (
            "Success tests character more than hardship does",
            "Anyone can wear wool mittens in snow, but carrying a full golden goblet without spilling takes poise",
            ["carrying a full goblet", "standing on a tall ladder", "a sudden spotlight"],
            [
                "Pride comes right before a hard fall",
                "Power reveals who you already were when nobody was watching",
                "Staying grateful at the summit keeps you grounded",
                "True friends show up when times get hard",
                "Money magnifies habits rather than magically fixing them",
            ],
        ),
        (
            "Changing your mind with new facts is wisdom",
            "A sharp captain redraws the chart when an island pops up instead of ramming the beach",
            ["redrawing a map", "updating a compass", "switching umbrellas"],
            [
                "Admitting a mistake earns deeper respect",
                "Stubbornness wears a crown made entirely of cardboard",
                "The wisest people know how little they know",
                "Listening teaches more than talking ever will",
                "Outgrowing yesterday's opinion proves your brain is still awake",
            ],
        ),
        (
            "Credit shared widely multiplies a team's spirit",
            "Passing the trophy down the bench turns one soloist into an unstoppable marching band",
            ["passing a torch", "a choir harmonizing", "a shared harvest"],
            [
                "True leaders take blame privately and give praise publicly",
                "A shared burden feels half as heavy",
                "Jealousy shrinks the room while applause stretches the ceiling",
                "Good leaders build more leaders instead of tiny mirrors",
                "Generosity returns in ways spreadsheets never predict",
            ],
        ),
        (
            "Never look down on anyone unless helping them up",
            "Standing on a taller hill only gives you a clearer view of where to toss the rope",
            ["tossing a rope", "a tall lantern", "a ladder shared"],
            [
                "Kindness to strangers costs nothing and echoes far",
                "Pride comes right before a hard fall",
                "Fortune spins like a weathervane faster than anyone expects",
                "Every stranger is fighting a hidden battle",
                "True greatness lifts everyone standing near it",
            ],
        ),
        (
            "Laughing at yourself keeps ego in check",
            "Letting a puff of air out of your own parade balloon keeps it from popping on the lamp post",
            ["deflating a parade balloon", "slipping on a banana peel", "a court jester"],
            [
                "Regret lasts longer than brief embarrassment",
                "Taking your work seriously works best when you take yourself lightly",
                "Pride comes right before a hard fall",
                "Humor disarms tense rooms faster than lectures",
                "Perfectionism makes life brittle while playfulness keeps it bouncy",
            ],
        ),
        (
            "True greatness shows up in tiny chores",
            "A master chef washes the cutting board just as lovingly as she plates the soufflé",
            ["washing a cutting board", "polishing unseen gears", "sweeping backstage"],
            [
                "Empty vessels make the loudest noise",
                "How you handle small promises predicts how you guard big ones",
                "Character is what your hands do when the curtains are closed",
                "Quality hides inside the corners most people skip",
                "Patience with dull tasks builds extraordinary mastery",
            ],
        ),
        (
            "Even the tallest tree started as a seed",
            "Every towering redwood in the canyon once fit comfortably inside a squirrel's cheek",
            ["a redwood in a squirrel's cheek", "an oak inside an acorn", "a river from a puddle"],
            [
                "Never mock clumsy beginnings before the story unfolds",
                "Good things take time to grow",
                "Pride comes right before a hard fall",
                "Respecting humble roots keeps success from going sour",
                "Small daily steps beat giant rare leaps",
            ],
        ),
    ],
    "Truth": [
        (
            "Not everything shiny is pure gold",
            "A painted tin coin glitters just as loudly in the gutter until the first rain washes it gray",
            ["painted tin coins", "fools gold", "frosted cardboard cake"],
            [
                "Appearances often hide the cracked engine underneath the hood",
                "Actions speak louder than polished speeches",
                "Real value proves itself quietly over long seasons",
                "Easy bargains usually conceal expensive surprises",
                "Slow judgment avoids being dazzled by clever packaging",
            ],
        ),
        (
            "Actions speak far louder than promises",
            "One warm loaf handed across the fence feeds more hunger than a dozen printed menus",
            ["bread vs a menu", "footprints vs a map", "rain vs a forecast"],
            [
                "Empty vessels make the loudest noise",
                "Trust takes years to build and seconds to shatter",
                "Watch what people do when convenience runs out",
                "Honesty without kindness still leaves bruises",
                "Small consistent deeds outweigh grand yearly speeches",
            ],
        ),
        (
            "One lie always demands ten more lies",
            "Covering one ink blot with fresh wallpaper forces you to redecorate the entire hallway",
            ["patching wallpaper", "juggling extra plates", "a snowball rolling downhill"],
            [
                "Telling the truth spares you from memorizing a complicated script",
                "Secrets grow heavier the longer you carry them in your coat",
                "Honesty builds sturdy bridges even when the planks sting",
                "Admitting a mistake earns deeper respect",
                "Deception eventually trips over its own shoelaces",
            ],
        ),
        (
            "Half-truths can mislead as much as lies",
            "Showing someone a postcard of sunny palm trees while hiding the hurricane behind the camera tricks the traveler",
            ["cropped postcards", "half a bridge", "a broken clock"],
            [
                "One lie always demands ten more lies",
                "Context changes the meaning of almost any quote",
                "Not everything shiny is pure gold",
                "Listening to both sides prevents foolish verdicts",
                "Selective facts paint convenient illusions",
            ],
        ),
        (
            "We see the world through our own lens",
            "Put rose-tinted spectacles on a carpenter and even plain pine boards start looking like cedar",
            ["tinted spectacles", "a funhouse mirror", "coloring a windowpane"],
            [
                "Two witnesses on opposite corners remember two different parades",
                "Changing your perspective turns dead ends into doorways",
                "Assumptions fill blanks with our own fears and wishes",
                "Everyone you meet can teach you something",
                "Questioning your first hunch prevents avoidable blunders",
            ],
        ),
        (
            "Honesty without kindness feels like cruelty",
            "Handing someone a winter coat works wonders, unless you toss it soaking wet at their face",
            ["throwing a coat", "a dull scalpel", "salt on a scratch"],
            [
                "Speaking the truth gently helps people actually hear it",
                "Words spoken in anger are impossible to take back",
                "Flattery feels sweeter in the moment than honest advice",
                "Tact builds a doorway where bluntness swings a hammer",
                "True friends tell you when spinach sits on your tooth",
            ],
        ),
        (
            "Listen to both sides before judging",
            "Tasting only the crust of a berry pie tells you nothing about whether the filling is sweet or sour",
            ["two sides of a coin", "hearing one violin", "tasting only pie crust"],
            [
                "Half-truths can mislead as much as lies",
                "Gossip shrinks every time somebody asks for evidence",
                "Jumping to conclusions is poor exercise for the mind",
                "We see the world through our own lens",
                "Calm curiosity beats instant outrage",
            ],
        ),
        (
            "A rumor travels faster than the truth",
            "Wildfire sprints across dry grass while the fire wagon is still strapping on its boots",
            ["wildfire vs boots", "dandelion seeds in gale", "a spilled jar of marbles"],
            [
                "Words spoken in anger are impossible to take back",
                "Listen to both sides before judging",
                "Repeating unverified gossip makes you an unpaid delivery truck",
                "Time reveals what haste tries to hide",
                "Quiet facts outlast noisy headlines in the long run",
            ],
        ),
        (
            "You cannot fool yourself for very long",
            "Taping a smiley sticker over the dashboard warning lamp never refills the motor oil",
            ["tape over a warning light", "hiding bills in a drawer", "painting over rust"],
            [
                "Facing uncomfortable realities early saves costly repairs later",
                "One lie always demands ten more lies",
                "Honesty with yourself is the hardest and most useful habit",
                "Ignoring small cracks lets the dam burst in spring",
                "Excuses comfort you briefly while keeping the cage locked",
            ],
        ),
        (
            "Flattery often hides a selfish motive",
            "When the fox compliments the crow's singing voice, he is really admiring her lunch",
            ["a fox and a crow", "sugar on a trap", "too-eager sales pitch"],
            [
                "Not everything shiny is pure gold",
                "Honest criticism from an ally beats sweet praise from a stranger",
                "Actions speak far louder than promises",
                "Beware gifts wrapped in urgent deadlines",
                "True friends care more about your growth than your applause",
            ],
        ),
        (
            "Character shows when nobody is watching",
            "Returning the stray shopping cart during a drizzle reveals far more than any trophy shelf",
            ["a stray shopping cart", "backstage manners", "a dark hallway"],
            [
                "Actions speak far louder than promises",
                "Small daily habits shape who you become in a crisis",
                "Reputation is what neighbors whisper while integrity is what you live",
                "Standing alone for right takes real grit",
                "True greatness shows up in tiny chores",
            ],
        ),
        (
            "Doubting everything blinds you just like gullibility",
            "Locking your shutters against thieves also keeps out every sunrise and fresh breeze",
            ["locked shutters", "a locked mailbox", "refusing every bridge"],
            [
                "Trust cautiously, yet leave a window cracked for goodness",
                "We see the world through our own lens",
                "Not everything shiny is pure gold",
                "Cynicism pretends to be wisdom while starving your joy",
                "Testing ideas gently beats rejecting them out of habit",
            ],
        ),
    ],
    "Growth": [
        (
            "Change is the only constant in life",
            "You never dip your bare toes into the exact same river water twice",
            ["a flowing river", "turning seasons", "shifting sand dunes"],
            [
                "Clinging tightly to yesterday bruises your hands against tomorrow",
                "Letting go of old chapters creates shelf space for better books",
                "Adaptable reeds survive hurricanes that snap rigid oaks",
                "Comfort zones rarely produce new discoveries",
                "Every ending secretly plants the doorway to a fresh start",
            ],
        ),
        (
            "Flexibility outlasts rigid stubbornness",
            "When the typhoon roars across the valley, slender bamboo bows low and stands again at dawn",
            ["bamboo in a gale", "a bending fishing rod", "water around boulders"],
            [
                "Change is the only constant in life",
                "Changing your mind with new facts is wisdom",
                "Compromise keeps sturdy partnerships from snapping in half",
                "Strength looks soft when it knows how to absorb a shock",
                "Fighting reality wastes energy better spent steering the boat",
            ],
        ),
        (
            "You cannot pour from an empty cup",
            "A dried-up garden hose cannot rescue thirsty daisies no matter how hard you turn the brass valve",
            ["a dry garden hose", "an uncharged flashlight", "an empty teapot"],
            [
                "Rest is necessary for lasting energy",
                "Caring for yourself enables you to care generously for others",
                "Saying no protects what truly matters",
                "Burnout sneaks in disguised as dedication",
                "Pacing your kindness keeps it sustainable for decades",
            ],
        ),
        (
            "Old habits fade only when replaced",
            "Pulling weeds out of a flowerbed leaves bare dirt unless you sow clover in the gap",
            ["weeding and sowing", "re-routing a creek", "swapping a melody"],
            [
                "Small daily steps beat giant rare leaps",
                "Environment shapes your choices more than raw willpower",
                "Change is the only constant in life",
                "Starting fresh works best when the new routine feels easy",
                "You become like the company you keep",
            ],
        ),
        (
            "Letting go makes room for better things",
            "A trapeze artist never catches the next flying bar while both fists stay glued to the old one",
            ["a trapeze artist", "autumn leaves falling", "cleaning an attic"],
            [
                "Clinging to past mistakes prevents you from writing new chapters",
                "Forgiveness unloads heavy bricks from your own backpack",
                "Change is the only constant in life",
                "Outgrowing a shell feels tight right before you stretch wider",
                "Saying goodbye gracefully honors what the season gave you",
            ],
        ),
        (
            "Experience is what you earn when plans fail",
            "Nobody learns how to patch a bicycle tire while coasting smoothly down a paved hill",
            ["patching a bike tire", "scuffed leather boots", "a sailor's knots"],
            [
                "Failure is a teacher, not a verdict",
                "Smooth seas never made a skilled sailor",
                "Learning from other people's scars costs less than earning your own",
                "Mistakes only turn tragic when you refuse to read the lesson",
                "Wisdom comes with receipts from awkward detours",
            ],
        ),
        (
            "Where attention goes, energy naturally flows",
            "Whichever patch of backyard soil you aim the sprinkler toward turns bright emerald by Friday",
            ["aiming a sprinkler", "a magnifying glass", "feeding a hearth fire"],
            [
                "Protecting your focus multiplies everything you create",
                "Chasing shiny distractions leaves five half-built treehouses",
                "Gratitude amplifies what already works well",
                "Worrying today never fixes tomorrow",
                "Small daily steps beat giant rare leaps",
            ],
        ),
        (
            "Every expert was once a clumsy beginner",
            "Before the concert pianist dazzled Carnegie Hall, she tortured her neighbors with crooked scales",
            ["crooked piano scales", "training wheels", "wobbly baby deer"],
            [
                "Failure is a teacher, not a verdict",
                "Comparing your first week to someone's tenth year steals heart",
                "Good things take time to grow",
                "Patience with awkward starts unlocks lifelong joy",
                "Curiosity matters more than early talent",
            ],
        ),
        (
            "Outgrowing old spaces is natural, not cruel",
            "A hermit crab does not hate its former shell; its legs simply stopped fitting inside the porch",
            ["a hermit crab's shell", "too-small sneakers", "a seedling in a cup"],
            [
                "Letting go makes room for better things",
                "Change is the only constant in life",
                "Distance reveals who truly cares about you",
                "Staying tiny to keep old rooms comfortable hurts everyone",
                "Moving forward honors the roots that raised you",
            ],
        ),
        (
            "Curiosity keeps the mind forever young",
            "A pond fed by a bubbling mountain spring stays crystal clear while stagnant puddles grow moss",
            ["a spring-fed pond", "turning fresh pages", "opening attic windows"],
            [
                "The wisest people know how little they know",
                "Asking good questions opens richer doors than reciting answers",
                "Boredom vanishes the second you look closer at ordinary things",
                "Everyone you meet can teach you something",
                "Learning never retires unless you hand in your library card",
            ],
        ),
        (
            "You cannot heal in the place that hurt you",
            "A sunburned traveler does not recover by laying out a fresh towel on the exact same dune",
            ["a sunburned beach towel", "moving a potted fern", "stepping away from smoke"],
            [
                "Changing your surroundings can reset your whole outlook",
                "Letting go makes room for better things",
                "You become like the company you keep",
                "Walking away from toxic rooms is wisdom rather than defeat",
                "Fresh air clears thoughts that stale hallways keep looping",
            ],
        ),
        (
            "Progress matters more than perfection",
            "A slightly lopsided handmade bridge still carries travelers across the creek while a flawless blueprint stays dry in a drawer",
            ["a handmade bridge", "a rough clay mug", "a finished race"],
            [
                "Starting is the hardest part of any quest",
                "Perfectionism disguises fear as high standards",
                "Small daily steps beat giant rare leaps",
                "Done and improving beats polished and imaginary",
                "Failure is a teacher, not a verdict",
            ],
        ),
    ],
    "Gratitude": [
        (
            "Comparison is the thief of joy",
            "A baker with warm cinnamon rolls turns sour the second he peeks at his neighbor's wedding cake",
            ["peeking at another bakery", "measuring lawns", "envying someone's umbrella"],
            [
                "Enough feels richer than chasing an endless finish line",
                "You only see other people's highlight reel, never their messy kitchen",
                "Gratitude turns modest blessings into abundance",
                "Running your own race lets you actually breathe",
                "Jealousy poisons the feast already sitting on your table",
            ],
        ),
        (
            "Wealth means needing less, not hoarding more",
            "A hiker with a light pack and cold creek water strolls past a merchant dragging three heavy trunks uphill",
            ["a light backpack", "a sparrow vs a dragon", "a cozy hearth"],
            [
                "Money buys convenience, never genuine peace of mind",
                "Comparison is the thief of joy",
                "Chasing possessions eventually lets your possessions own you",
                "Enough is a choice rather than a number on a ledger",
                "Simple pleasures outlast expensive novelty",
            ],
        ),
        (
            "You rarely miss water until the well runs dry",
            "Nobody applauds the kitchen refrigerator for humming softly until the morning the milk turns warm",
            ["a quiet fridge", "a reliable streetlight", "breathing through a clear nose"],
            [
                "Taking everyday blessings for granted dulls their magic",
                "Health feels invisible until a tiny splinter slows you down",
                "Appreciating people while they are here beats praising them later",
                "Gratitude wakes up ordinary Tuesday mornings",
                "Loss teaches the true price of things we treated as free",
            ],
        ),
        (
            "The grass looks greener because you ignore its weeds",
            "From across the street every meadow seems like velvet until you step barefoot onto the thistles",
            ["distant meadows", "a glossy brochure", "painted stage props"],
            [
                "Comparison is the thief of joy",
                "Watering your own yard beats staring over the wooden fence",
                "Not everything shiny is pure gold",
                "Every glamorous lifestyle carries hidden chores behind the curtain",
                "Contentment grows where you choose to tend it",
            ],
        ),
        (
            "Enjoy small joys before they become memories",
            "Catching fireflies in July only works while twilight is still warm around your ankles",
            ["summer fireflies", "warm toast", "a fleeting sunset"],
            [
                "Worrying today never fixes tomorrow",
                "Ordinary evenings often turn into the chapters you miss most",
                "Rushing toward milestones skips the scenery between them",
                "You rarely miss water until the well runs dry",
                "Presence is the rarest luxury in a noisy world",
            ],
        ),
        (
            "Greed ruins even a generous harvest",
            "A monkey who grabs a double fistful of chestnuts inside a narrow jar cannot pull his paw back out",
            ["a fist stuck in a jar", "popping a golden goose", "overloading a rowboat"],
            [
                "Wealth means needing less, not hoarding more",
                "Knowing when to stop protects everything you already won",
                "Chasing everything at once leaves your hands empty",
                "Sharing your luck keeps fortune friendly",
                "Short-term appetite can destroy long-term abundance",
            ],
        ),
        (
            "Gratitude turns an ordinary meal into a feast",
            "A bowl of warm tomato soup tastes like royalty when you remember the frost outside the window",
            ["soup in winter", "seasoning with salt", "sunlight on a kitchen table"],
            [
                "Enjoy small joys before they become memories",
                "Mood colors experience more than price tags ever do",
                "Comparison is the thief of joy",
                "Counting what works unlocks calm during tough weeks",
                "Wealth means needing less, not hoarding more",
            ],
        ),
        (
            "Health is a crown only the sick notice",
            "Nobody writes love poems to a pair of quiet knees until climbing three porch steps feels like Everest",
            ["quiet knees", "a tuned cello", "an invisible roof"],
            [
                "You rarely miss water until the well runs dry",
                "Rest is necessary for lasting energy",
                "Trading wellness for extra coin leaves a lopsided bargain",
                "Caring for your body today is a gift to your older self",
                "Simple mornings with steady breath beat grand luxuries",
            ],
        ),
        (
            "Possessions eventually end up owning you",
            "Collect twenty ornate silver teapots and suddenly your whole Saturday belongs to polishing rags",
            ["polishing twenty teapots", "a gilded cage", "towing too many suitcases"],
            [
                "Wealth means needing less, not hoarding more",
                "Clutter crowds out calm in both closets and calendars",
                "Experiences keep glowing long after gadgets gather dust",
                "Letting go makes room for better things",
                "Freedom grows whenever you lighten your cargo",
            ],
        ),
        (
            "Enough is a decision, not a number",
            "A leaky bucket never feels full even if you park it directly beneath Niagara Falls",
            ["a leaky bucket", "chasing the horizon", "a bottomless pocket"],
            [
                "Wealth means needing less, not hoarding more",
                "Comparison is the thief of joy",
                "Greed ruins even a generous harvest",
                "Satisfaction begins the moment you stop moving the goalpost",
                "Gratitude turns an ordinary meal into a feast",
            ],
        ),
        (
            "Memories outlast anything you can wrap",
            "Moths nibble cashmere sweaters, while a midnight campfire song stays crisp fifty winters later",
            ["campfire songs vs sweaters", "footprints vs souvenirs", "a family recipe"],
            [
                "Possessions eventually end up owning you",
                "Enjoy small joys before they become memories",
                "Time spent together beats expensive presents left on shelves",
                "Nobody can be happy alone",
                "Shared adventures compound in value every time you retell them",
            ],
        ),
        (
            "Water your own lawn before admiring your neighbor's",
            "Leaning on the picket fence to praise someone else's roses will not pull a single dandelion at home",
            ["leaning on a fence", "tending your teapot", "polishing your own bike"],
            [
                "The grass looks greener because you ignore its weeds",
                "Comparison is the thief of joy",
                "Where attention goes, energy naturally flows",
                "Small daily care transforms ordinary spaces",
                "Gratitude begins by noticing what sits right in front of you",
            ],
        ),
    ],
    "Trust": [
        (
            "Trust takes years to earn and seconds to break",
            "A porcelain teapot survives a hundred cozy afternoons yet shatters from one careless drop",
            ["a porcelain teapot", "a tall card tower", "a glass bridge"],
            [
                "Actions speak far louder than promises",
                "Rebuilding broken faith requires patience and steady proof",
                "Small kept promises lay the bricks of deep loyalty",
                "One lie always demands ten more lies",
                "Forgiving someone does not require handing them the house keys again",
            ],
        ),
        (
            "Once burned, you approach fire more carefully",
            "A cat who accidentally sits on a warm woodstove will not even nap on a cold iron lid afterward",
            ["a cat near a stove", "testing hot soup", "checking thin ice"],
            [
                "Experience is what you earn when plans fail",
                "Caution protects you, though fear should not lock every door",
                "Trust takes years to earn and seconds to break",
                "Past scars remind you what to watch without dictating your future",
                "Healthy boundaries grow naturally from old lessons",
            ],
        ),
        (
            "Beware anyone who gossips to you about friends",
            "A dog that fetches your neighbor's slippers onto your porch will soon carry your boots next door",
            ["a slipper-stealing dog", "a leaky megaphone", "a two-headed parrot"],
            [
                "A rumor travels faster than the truth",
                "Loyalty shows in how someone defends you when you leave the room",
                "Flattery often hides a selfish motive",
                "You become like the company you keep",
                "Keeping confidences proves you are worth trusting",
            ],
        ),
        (
            "Keep your promises, especially the small ones",
            "If the carpenter skips two nails on the porch step, nobody trusts his rafters in a blizzard",
            ["skipping porch nails", "a punctual pocket watch", "returning a borrowed pen"],
            [
                "Trust takes years to earn and seconds to break",
                "Actions speak far louder than promises",
                "True greatness shows up in tiny chores",
                "Under-promising and over-delivering builds quiet magic",
                "Reliability is one of the rarest superpowers",
            ],
        ),
        (
            "Forgiving someone does not mean forgetting the lesson",
            "You can wish the wasp a lovely afternoon without inviting it back inside your soda can",
            ["a wasp and a soda can", "mending a fence", "closing a gate kindly"],
            [
                "Once burned, you approach fire more carefully",
                "Holding a grudge hurts you far more than your rival",
                "Boundaries and kindness can live inside the same house",
                "Trust takes years to earn and seconds to break",
                "Peace arrives when you release bitterness yet keep your eyes open",
            ],
        ),
        (
            "Clear agreements keep good friendships strong",
            "Sturdy stone fences along the pasture stop warm neighbors from arguing over stray goats",
            ["stone pasture fences", "clear recipe cards", "marking lanes in a pool"],
            [
                "Unspoken expectations quietly breed avoidable resentment",
                "Saying no protects what truly matters",
                "Honesty upfront prevents messy surprises later",
                "Money and favors mix best when written plainly on paper",
                "Respecting limits makes teamwork effortless",
            ],
        ),
        (
            "Watch how someone treats people they do not need",
            "A royal crown means nothing if the wearer kicks the bakery delivery boy when the cameras blink off",
            ["the bakery delivery boy", "tipping a waiter", "manners toward strays"],
            [
                "Character shows when nobody is watching",
                "Flattery often hides a selfish motive",
                "Actions speak far louder than promises",
                "Never look down on anyone unless helping them up",
                "Politeness toward the powerful proves nothing about kindness",
            ],
        ),
        (
            "Secrets shared with three rarely stay secret",
            "Whispering a recipe into two leaky watering cans ensures the whole street smells your stew",
            ["leaky watering cans", "a whisper in a canyon", "writing in wet sand"],
            [
                "A rumor travels faster than the truth",
                "Beware anyone who gossips to you about friends",
                "Silence guards treasures that casual chatter loses",
                "Trust takes years to earn and seconds to break",
                "Only hand fragile stories to people with steady palms",
            ],
        ),
        (
            "Loyalty shows when someone defends your absence",
            "The best umbrella shields your picnic basket even after you stepped indoors for napkins",
            ["guarding an empty chair", "a watchman on a wall", "shutting down a rumor"],
            [
                "Beware anyone who gossips to you about friends",
                "True friends show up when times get hard",
                "Character shows when nobody is watching",
                "Quiet advocacy means twice as much as public flattery",
                "Trust takes years to earn and seconds to break",
            ],
        ),
        (
            "Trust your gut when something feels off",
            "When every songbird in the orchard suddenly goes quiet, check the fence before strolling out",
            ["quiet orchard birds", "a dog bristling", "smelling smoke early"],
            [
                "Not everything shiny is pure gold",
                "Flattery often hides a selfish motive",
                "Slow down whenever someone pushes you to sign before sundown",
                "Intuition is often quiet pattern-recognition speaking softly",
                "Once burned, you approach fire more carefully",
            ],
        ),
        (
            "Don't burn bridges you might someday cross",
            "Chopping up the wooden dock for firewood feels cozy tonight until the ferry returns in April",
            ["burning a dock for firewood", "slamming a tavern door", "tossing away a map"],
            [
                "Words spoken in anger are impossible to take back",
                "Leaving rooms gracefully keeps old doors friendly years later",
                "Small worlds have a habit of looping paths back together",
                "Wait until anger cools before deciding",
                "Polite goodbyes cost nothing and pay lifelong dividends",
            ],
        ),
        (
            "Give trust gradually rather than all at once",
            "You test a frozen creek with one careful boot heel before driving the hay wagon across",
            ["testing frozen ice", "tasting soup first", "handing over one key"],
            [
                "Trust takes years to earn and seconds to break",
                "Doubting everything blinds you just like gullibility",
                "Time reveals what haste tries to hide",
                "Small shared tests reveal reliability safely",
                "Actions speak far louder than promises",
            ],
        ),
    ],
    "Craft": [
        (
            "Measure twice so you only cut once",
            "Double-checking the blueprint prevents the carpenter from inventing an unplanned window",
            ["checking a blueprint", "re-reading a map", "tasting batter first"],
            [
                "Rushing a job usually means doing it twice",
                "Thoughtful preparation saves ten times its weight in repairs",
                "Shortcuts often end up taking longer",
                "Good tools still need steady hands behind them",
                "Slowing down at the start speeds up the finish",
            ],
        ),
        (
            "Chasing two rabbits leaves you catching neither",
            "A juggler who insists on frying pancakes while riding a unicycle ends up wearing syrup",
            ["pancakes on a unicycle", "two fishing poles tangled", "splitting one lantern"],
            [
                "Where attention goes, energy naturally flows",
                "Doing one craft deeply beats dabbling sloppily in twelve",
                "Saying no protects what truly matters",
                "Multitasking often just switches mistakes faster",
                "Finishing one small project beats starting five grand ones",
            ],
        ),
        (
            "Blaming your tools never fixes your craft",
            "Swapping to a gold-plated spatula will not rescue eggs left unattended on high heat",
            ["a golden spatula", "a crooked tennis racket", "expensive running shoes"],
            [
                "Skill turns humble supplies into magic",
                "Admitting a mistake earns deeper respect",
                "Practice improves results faster than shopping for shiny gear",
                "Failure is a teacher, not a verdict",
                "Every master once wrestled with stubborn materials",
            ],
        ),
        (
            "Simplicity is the highest form of skill",
            "A master sculptor reveals the marble horse simply by chipping away everything that looks unlike a horse",
            ["chipping marble", "three chords in a song", "packing one neat suitcase"],
            [
                "Adding extra bells rarely hides a shaky melody",
                "Clear explanations prove you truly understand a puzzle",
                "Editing out clutter lets the real message shine",
                "Progress matters more than perfection",
                "Less noise makes every remaining detail unforgettable",
            ],
        ),
        (
            "Practice quietly makes hard things look easy",
            "The audience applauds a three-minute ballet leap without seeing ten thousand dusty studio rehearsals",
            ["ten thousand rehearsals", "calloused guitar fingers", "hidden clockwork"],
            [
                "Small daily steps beat giant rare leaps",
                "Every expert was once a clumsy beginner",
                "Good things take time to grow",
                "Overnight breakthroughs usually take seven quiet years",
                "Talent opens the door while discipline builds the house",
            ],
        ),
        (
            "A good foundation holds up every storm",
            "Nobody takes photos of concrete footings buried in mud, yet the whole lighthouse relies on them",
            ["lighthouse footings", "deep oak roots", "tuning before a concert"],
            [
                "Shortcuts often end up taking longer",
                "Mastering the basics unlocks every advanced move later",
                "True greatness shows up in tiny chores",
                "Measure twice so you only cut once",
                "Rushing past fundamentals creates cracks on the fifth floor",
            ],
        ),
        (
            "Quality stays remembered long after price fades",
            "A pair of stitched leather boots stings your wallet at checkout yet thrills your feet ten winters later",
            ["stitched leather boots", "cast iron skillets", "a hand-carved chair"],
            [
                "Shortcuts often end up taking longer",
                "Not everything shiny is pure gold",
                "Doing work you are proud to sign outlives quick profits",
                "Craftsmanship rewards patience with decades of service",
                "Cheap fixes often charge rent every single month",
            ],
        ),
        (
            "Fresh eyes spot mistakes tired minds miss",
            "Stepping out onto the porch for ten minutes makes the missing puzzle piece wave right at you",
            ["a missing puzzle piece", "cooling a painting", "walking around the block"],
            [
                "Rest is necessary for lasting energy",
                "Asking a teammate to look over your draft catches blind spots",
                "Wait until anger cools before deciding",
                "Distance brings clarity to tangled knots",
                "Working longer hours rarely beats working with clear focus",
            ],
        ),
        (
            "Teach something to truly understand it",
            "You only notice the wobbly steps in your own attic stairs the afternoon you guide a guest upstairs",
            ["guiding a guest upstairs", "writing a recipe down", "explaining a board game"],
            [
                "Simplicity is the highest form of skill",
                "The wisest people know how little they know",
                "Sharing knowledge lights another candle without dimming yours",
                "Curiosity keeps the mind forever young",
                "Questions from beginners sharpen experts",
            ],
        ),
        (
            "Consistency beats occasional bursts of brilliance",
            "A bakery that bakes warm sourdough every dawn outsells a genius who opens twice a summer",
            ["daily sourdough", "a steady metronome", "a lighthouse beam"],
            [
                "Small daily steps beat giant rare leaps",
                "Practice quietly makes hard things look easy",
                "Showing up reliably builds trust and mastery together",
                "Good things take time to grow",
                "Discipline carries you on mornings when inspiration sleeps in",
            ],
        ),
        (
            "Fix small leaks before they sink the ship",
            "One loose button sewn tonight saves you from chasing your coat down a windy subway platform",
            ["one loose button", "a tiny crack in a dam", "oiling a squeaky hinge"],
            [
                "Ignoring uncomfortable realities early invites bigger repairs later",
                "Measure twice so you only cut once",
                "Preventive care feels boring until it rescues your weekend",
                "Big doors swing on very small hinges",
                "Tiny daily maintenance protects everything you built",
            ],
        ),
        (
            "Good questions are worth more than quick answers",
            "A compass pointing in the right direction beats sprinting ninety miles an hour down the wrong canyon",
            ["a compass vs sprinting", "a lantern in a cave", "asking the chef"],
            [
                "Curiosity keeps the mind forever young",
                "Listening teaches more than talking ever will",
                "Measure twice so you only cut once",
                "Solving the right puzzle matters more than solving fast",
                "The wisest people know how little they know",
            ],
        ),
    ],
    "Kindness": [
        (
            "Small acts of kindness travel further than you think",
            "Dropping one pebble into a quiet lake sends ripples all the way to shores you never visited",
            ["ripples on a lake", "dandelion seeds drifting", "a warm porch lamp"],
            [
                "Kindness costs nothing yet stays remembered for decades",
                "Helping someone else often brightens your own mood first",
                "Every stranger is fighting a hidden battle",
                "Generosity multiplies whenever you pass it along",
                "Gentle words disarm hard days faster than logic",
            ],
        ),
        (
            "Holding a grudge hurts you most of all",
            "Gripping a glowing coal to hurl at your enemy leaves only your own palm blistered",
            ["holding a hot coal", "drinking sour vinegar", "carrying a backpack of rocks"],
            [
                "Forgiving someone sets your own heart free before theirs",
                "Letting go makes room for better things",
                "Wait until anger cools before deciding",
                "Bitterness builds a tiny prison where you also hold the key",
                "Peace arrives sooner when you stop replaying old arguments",
            ],
        ),
        (
            "Lighting another candle never dims your own",
            "Sharing a spoonful of sourdough starter gives your neighbor fresh loaves without shrinking your own jar",
            ["sourdough starter", "sharing a campfire", "teaching a song"],
            [
                "Credit shared widely multiplies a team's spirit",
                "Celebrating someone else's win never steals from your trophy case",
                "Generosity expands the room for everyone inside it",
                "Comparison is the thief of joy",
                "Kindness returns in unexpected ways",
            ],
        ),
        (
            "Kindness is never wasted, even unreturned",
            "A street violinist still fills the plaza with warm music even if one hurrying commuter never glances up",
            ["a plaza violinist", "perfume on a rose", "rain on a meadow"],
            [
                "Small acts of kindness travel further than you think",
                "Doing good shapes your own heart regardless of the applause",
                "Character shows when nobody is watching",
                "Generosity works best without invisible strings attached",
                "Patience with grumpy strangers protects your own calm",
            ],
        ),
        (
            "Gentle words calm fierce storms",
            "A spoonful of cool rainwater settles a sizzling pan far better than tossing in dry twigs",
            ["cool rain on embers", "oil on squeaky hinges", "a soft lullaby"],
            [
                "Words spoken in anger are impossible to take back",
                "Honesty without kindness feels like cruelty",
                "Listening teaches more than talking ever will",
                "Wait until anger cools before deciding",
                "Kindness disarms pride where shouting only builds thicker armor",
            ],
        ),
        (
            "What you give out eventually returns to you",
            "Shouting cheerful echoes across an Alpine valley guarantees friendly voices bouncing right back",
            ["an Alpine echo", "a boomerang", "tossing seeds uphill"],
            [
                "Small acts of kindness travel further than you think",
                "Treating others fairly builds a safety net of goodwill",
                "Plant seeds today for shade tomorrow",
                "Generosity quietly compounds over a lifetime",
                "Cruelty leaves bills that always find your forwarding address",
            ],
        ),
        (
            "Helping others also heals your own heart",
            "Guiding a lost hiker up the ridge accidentally warms your own cold fingers along the way",
            ["warming cold fingers", "carrying a lantern for two", "baking for a friend"],
            [
                "Nobody can be happy alone",
                "Small acts of kindness travel further than you think",
                "Stepping outside your own worries shrinks them down to size",
                "Generosity lifts the giver just as high as the receiver",
                "Shared joy doubles while shared sorrow splits in half",
            ],
        ),
        (
            "Give without keeping a hidden scorecard",
            "A gift tied to an invisible invoice feels more like a rented umbrella than a birthday present",
            ["a rented umbrella", "a gift with price tags", "a toll booth"],
            [
                "Kindness is never wasted, even unreturned",
                "Clear agreements keep good friendships strong",
                "Flattery often hides a selfish motive",
                "True generosity frees both hands the moment it gives",
                "Expecting constant repayment turns favors into resentment",
            ],
        ),
        (
            "Patience is one of the quietest forms of love",
            "Holding the hallway door open without sighing while a toddler ties both sneakers speaks volumes",
            ["tying toddler sneakers", "waiting by a kettle", "walking at a grandparent's pace"],
            [
                "Love grows deeper through tiny daily habits",
                "You cannot hurry someone else's healing",
                "Listening teaches more than talking ever will",
                "Gentle words calm fierce storms",
                "Rushing people you love steals warmth from the moment",
            ],
        ),
        (
            "Celebrate other people's victories genuinely",
            "Cheering when another boat catches the harbor breeze never steals a single gust from your sails",
            ["cheering another sailboat", "applauding at a recital", "shared sunshine"],
            [
                "Lighting another candle never dims your own",
                "Comparison is the thief of joy",
                "Credit shared widely multiplies a team's spirit",
                "Envy locks you out of the party while joy hands you a slice of cake",
                "Warm communities grow where applause flows freely",
            ],
        ),
        (
            "A warm welcome turns strangers into friends",
            "Setting one extra ceramic mug beside the kettle shrinks an awkward hallway into a parlor",
            ["an extra teacup", "a glowing porch light", "pulling up a chair"],
            [
                "Nobody can be happy alone",
                "Small acts of kindness travel further than you think",
                "Hospitality lives in warmth rather than fancy silverware",
                "Every stranger is fighting a hidden battle",
                "Inclusion makes every gathering twice as lively",
            ],
        ),
        (
            "Leave every place slightly better than you found it",
            "Tucking one stray seashell and two soda caps into your pocket keeps the whole cove shining for tomorrow's campers",
            ["leaving a tidy campsite", "refilling the coffee pot", "planting a trail marker"],
            [
                "Plant seeds today for shade tomorrow",
                "Character shows when nobody is watching",
                "Small daily care protects the commons we all share",
                "True greatness shows up in tiny chores",
                "Gratitude expresses itself best through stewardship",
            ],
        ),
    ],
}


def main() -> int:
    entries = []
    seen_ids = set()
    seen_lessons = set()
    errors = []

    for theme, items in RAW_THEMES.items():
        for idx, (lesson, bot_metaphor, sparks, decoys) in enumerate(items, start=1):
            entry_id = f"{theme.lower()}_{idx:02d}"
            if entry_id in seen_ids:
                errors.append(f"Duplicate id: {entry_id}")
            seen_ids.add(entry_id)

            norm = lesson.strip().lower()
            if norm in seen_lessons:
                errors.append(f"Duplicate lesson: {lesson}")
            seen_lessons.add(norm)

            if len(decoys) < 5:
                errors.append(f"{entry_id}: needs >= 5 decoys, has {len(decoys)}")
            if any(d.strip().lower() == norm for d in decoys):
                errors.append(f"{entry_id}: decoy duplicates lesson")
            if len({d.strip().lower() for d in decoys}) != len(decoys):
                errors.append(f"{entry_id}: duplicate decoys")

            # Validate bot metaphor against anti-spoiler stem overlap & length
            if not (12 <= len(bot_metaphor) <= 180):
                errors.append(f"{entry_id}: botMetaphor length {len(bot_metaphor)} out of bounds")
            if len(re.findall(r"[A-Za-z]+", bot_metaphor)) < 3:
                errors.append(f"{entry_id}: botMetaphor has < 3 words")

            overlap = content_stems(lesson) & content_stems(bot_metaphor)
            if overlap:
                errors.append(
                    f"{entry_id}: botMetaphor overlaps lesson stems {sorted(overlap)} | "
                    f"lesson={lesson!r} | metaphor={bot_metaphor!r}"
                )

            entries.append(
                {
                    "id": entry_id,
                    "theme": theme,
                    "lesson": lesson,
                    "decoys": decoys,
                    "sparks": sparks,
                    "botMetaphor": bot_metaphor,
                }
            )

    if errors:
        print("VALIDATION ERRORS:", file=sys.stderr)
        for e in errors:
            print("  -", e, file=sys.stderr)
        return 1

    out_path = (
        pathlib.Path(__file__).resolve().parent.parent
        / "core"
        / "src"
        / "main"
        / "resources"
        / "lessons"
        / "wisdom_bank.json"
    )
    out_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {"version": 1, "count": len(entries), "lessons": entries}
    out_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(entries)} validated lessons across {len(RAW_THEMES)} themes to {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Upgrades core/src/main/resources/lessons/wisdom_bank.json to v2 (150 lessons):
- Audits & sharpens near-duplicate decoys in the 120 Classic lessons
- Appends 30 hilarious Modern & Everyday Comedy life lessons (3 per theme, pack="MODERN")
- Validates every single lesson, decoy uniqueness, and botMetaphor against the exact
  anti-spoiler morphological stemmer used by Kotlin MetaphorValidator.
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

# Import the base generator's stemmer, stop words, and classic raw themes
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import generate_wisdom_bank as base

ROOT_ALIASES = {
    "alone": "lone", "lonely": "lone", "loneliness": "lone",
    "happy": "happ", "happily": "happ", "happiness": "happ", "unhappy": "happ",
    "wise": "wis", "wisdom": "wis", "wisely": "wis", "wisest": "wis",
    "true": "tru", "truth": "tru", "truly": "tru",
    "strong": "strong", "strength": "strong",
    "brave": "brav", "bravery": "brav",
    "angry": "angr", "anger": "angr",
    "proud": "prid", "pride": "prid",
    "kind": "kind", "kindness": "kind", "kindly": "kind",
    "patient": "patienc", "patience": "patienc",
    "forgive": "forgiv", "forgiveness": "forgiv", "forgiving": "forgiv",
    "fail": "fail", "failure": "fail", "failing": "fail",
    "friend": "friend", "friendship": "friend", "friendly": "friend",
}


def kt_stem(raw: str) -> str:
    w = re.sub(r"[^a-z]", "", raw.lower())
    if w in ROOT_ALIASES:
        return ROOT_ALIASES[w]
    s = base.stem(w)
    return ROOT_ALIASES.get(s, s)


def kt_stems(text: str) -> set[str]:
    words = re.findall(r"[A-Za-z']+", text.lower())
    out = set()
    for w in words:
        clean = w.replace("'", "")
        if clean in base.STOP_WORDS or len(clean) < 3:
            continue
        out.add(kt_stem(clean))
    return out


# Sharpened replacements for overly close decoys spotted in playtest audit
DECOY_SHARPEN_MAP = {
    "Choosing your circle carefully protects your future self":
        "Shared laughter heals awkward silences faster than formal speeches",
    "Small consistent deeds outweigh grand yearly speeches":
        "Keeping a cool head turns tangled disputes into calm bargains",
    "Asking for help is a sign of strength rather than weakness":
        "Quiet consistency outperforms sudden bursts of frantic energy",
    "Saying no kindly protects the promises you already made":
        "A warm sense of humor opens doors that solemn lectures lock",
    "Caring for yourself enables you to care generously for others":
        "Fresh perspective arrives the moment you step outside for air",
}

# 30 brand-new hilarious Modern & Everyday Comedy life lessons (3 per each of the 10 themes!)
MODERN_THEMES: dict[str, list[tuple[str, str, list[str], list[str]]]] = {
    "Connection": [
        (
            "Never trust a coworker who says quick question",
            "A raccoon promising to borrow one tiny grape leaves with your entire picnic hamper",
            ["a raccoon at a picnic", "an innocent doorbell", "a bottomless suitcase"],
            [
                "Replying all on a massive email thread invites chaos into your week",
                "Meetings scheduled on Friday afternoon drain the sun out of the sky",
                "Leaving a group chat quietly requires ninja-level diplomacy",
                "Nobody finishes a five-minute hallway chat in five minutes",
                "Free donuts in the breakroom unite bitter office rivals instantly",
            ],
        ),
        (
            "Never split a dinner bill with someone ordering lobster",
            "Sharing one watering can fairly fails when your neighbor planted a thirsty redwood",
            ["a redwood vs daisies", "splitting a treasure chest", "a whale at a buffet"],
            [
                "Borrowing a friend's charger without returning it tests ancient loyalties",
                "The friend who says they are five minutes away has not put on shoes yet",
                "Picking a restaurant with six hungry people takes longer than cooking",
                "A good road-trip copilot controls the snacks and the playlist wisely",
                "Nobody remembers who paid last Tuesday until dessert arrives",
            ],
        ),
        (
            "The friend saying five minutes away just left the shower",
            "When the town clock swears noon is moments away, the sun is still lacing its boots",
            ["a sleepy rooster", "a slow train schedule", "a turtle yelling almost there"],
            [
                "Never split a dinner bill with someone ordering lobster",
                "Texting okay with a period makes everyone suspect an invisible feud",
                "Canceling plans at the last minute brings secret relief to both sofas",
                "Always confirm the address before driving across town for dinner",
                "Borrowing a jacket from a taller sibling guarantees floppy sleeves",
            ],
        ),
    ],
    "Patience": [
        (
            "A watched loading bar always freezes near the end",
            "Staring intensely at a shy garden snail convinces it to nap inside its porcelain porch",
            ["staring at a snail", "a shy toaster", "watching paint dry"],
            [
                "Restarting the Wi-Fi router fixes half of modern civilization",
                "The elevator arrives fastest the second you step toward the stairs",
                "Waiting for a package makes every passing delivery truck sound like yours",
                "Clicking the frozen button twelve times only wakes up twelve copies later",
                "Cooking pasta takes forever until you glance away and it boils over",
            ],
        ),
        (
            "Avocados ripen during the ten minutes you blink",
            "A rock-hard emerald dragon egg waits three weeks only to turn into mush while you tie your shoes",
            ["a sneaky dragon egg", "a fleeting rainbow", "a ticking soufflé"],
            [
                "A watched loading bar always freezes near the end",
                "Leftovers hidden behind the milk carton quietly invent new civilizations",
                "Toasting pine nuts goes from pale snow to charcoal in half a heartbeat",
                "Bananas bought green on Monday start plotting banana bread by Thursday",
                "Never microwave soup uncovered unless you enjoy scrubbing ceilings",
            ],
        ),
        (
            "The bus arrives the moment you decide to walk",
            "The stubborn ferry only blows its cheerful horn once your boots are wet crossing the bridge",
            ["a sneaky ferry", "an umbrella on a sunny day", "a shy elevator"],
            [
                "A watched loading bar always freezes near the end",
                "Switching into the faster checkout lane instantly summons a price check",
                "Every red traffic light waits specifically for people running late",
                "Washing your car is the most reliable rain dance ever invented",
                "Forgetting your umbrella guarantees a surprise tropical monsoon",
            ],
        ),
    ],
    "Courage": [
        (
            "Sending a risky text feels like defusing a bomb",
            "Tossing a glass bottle into stormy waves makes your pulse gallop faster than a startled pony",
            ["a message in a bottle", "stepping onto thin ice", "jumping off a high dive"],
            [
                "Calling customer service on the telephone requires heroic deep breaths",
                "Telling the barber you dislike the haircut takes nerves of pure titanium",
                "Checking your bank balance after vacation demands theatrical bravery",
                "Opening a mysterious container in the back of the fridge tests every instinct",
                "Walking past a barking lawn goose requires calm eye contact",
            ],
        ),
        (
            "Telling the barber you dislike the haircut takes titanium nerves",
            "Whispering to a proud sculptor that the marble bangs look lopsided demands a knight's shield",
            ["a knight and a sculptor", "returning soup politely", "speaking up at an auction"],
            [
                "Sending a risky text feels like defusing a bomb",
                "Saying I forgot your name after three meetings takes honest grit",
                "Refusing a third plate of casserole at grandma's table is nearly impossible",
                "Walking back inside after waving goodbye at the driveway feels surreal",
                "Asking someone to repeat themselves a fourth time tests all courage",
            ],
        ),
        (
            "Checking your bank balance after vacation takes real bravery",
            "Peeking inside the cookie jar after a festive weekend feels like inspecting a dragon's cave",
            ["an empty cookie jar", "an unopened envelope", "stepping onto a scale"],
            [
                "Sending a risky text feels like defusing a bomb",
                "Never go grocery shopping while completely starving",
                "Impulse buying at midnight produces baffling cardboard boxes by Thursday",
                "Canceling a forgotten free trial before billing day is an extreme sport",
                "Reading your own old diary entries builds instant humility",
            ],
        ),
    ],
    "Humility": [
        (
            "Reading your own old messages builds instant humility",
            "Unearthing a crayon portrait you proudly drew in third grade keeps your halo nicely level",
            ["a third-grade crayon drawing", "an old yearbook photo", "a squeaky karaoke tape"],
            [
                "Tripping on a completely flat sidewalk reminds your ego who owns gravity",
                "Waving enthusiastically at a stranger who waved at someone behind you stings",
                "Forgetting why you walked into the kitchen happens to the sharpest minds",
                "Hearing your own recorded voice humbles even opera singers",
                "Searching frantically for glasses already perched on your forehead cures pride",
            ],
        ),
        (
            "Waving back at someone waving behind you humbles anyone",
            "Bowing gracefully for an ovation aimed at the violinist standing over your shoulder melts all swagger",
            ["bowing at the wrong curtain call", "catching a stray salute", "a confused parade float"],
            [
                "Reading your own old messages builds instant humility",
                "Saying you too when the waiter says enjoy your meal lives forever",
                "Pushing a door labeled pull while three people watch sharpens character",
                "Accidentally liking a five-year-old vacation photo requires immediate retreat",
                "Laughing confidently at a joke you misheard teaches quiet caution",
            ],
        ),
        (
            "Looking for glasses perched on your own forehead cures arrogance",
            "Hunting across seven kingdoms for a royal crown currently warming your own ears keeps royalty grounded",
            ["a missing royal crown", "a flashlight hunting itself", "a hound sniffing its tail"],
            [
                "Reading your own old messages builds instant humility",
                "Using your phone flashlight to hunt for your phone builds character",
                "Walking confidently into the wrong aisle happens to every navigator",
                "Waving back at someone waving behind you humbles anyone",
                "Spelling a simple word wrong twice makes you doubt the whole alphabet",
            ],
        ),
    ],
    "Truth": [
        (
            "Nobody reads the terms of service before clicking agree",
            "Every eager traveler signs a thousand-page parchment treaty blindfolded just to cross the wooden bridge",
            ["signing a blindfolded scroll", "skipping a giant contract", "jumping past the fine print"],
            [
                "Online product photos always hide how tiny the actual lamp is",
                "A recipe claiming ten-minute prep secretly requires chopping for an hour",
                "Saying I will remember that without writing it down is pure fiction",
                "Packing five extra books for a two-day trip fools nobody",
                "Everyone turns down the car radio to see street signs better",
            ],
        ),
        (
            "A recipe claiming ten-minute prep is pure fiction",
            "Any travel brochure swearing the mountain summit sits five easy paces from parking sells fairy tales",
            ["a lying mountain trail sign", "a crooked stopwatch", "a magician's hourglass"],
            [
                "Nobody reads the terms of service before clicking agree",
                "Assembling flat-pack furniture always leaves one mysterious silver screw",
                "The person who says just eyeball the seasoning ruins unsuspecting dinners",
                "Telling yourself just one more episode at midnight never ends at one",
                "A restaurant with forty pages of dishes rarely masters every kitchen",
            ],
        ),
        (
            "Saying I will remember without writing it down is a trap",
            "Carving your grocery list onto a melting ice cube guarantees you return home without milk",
            ["writing on an ice cube", "whispering to a goldfish", "catching fog in a net"],
            [
                "Nobody reads the terms of service before clicking agree",
                "Hiding your spare key in an extra clever spot hides it from yourself too",
                "A password invented during a coffee rush vanishes by next Tuesday",
                "Leaving one sticky note on the fridge outperforms a thousand promises",
                "Screenshots saved for later quietly join a museum of forgotten ideas",
            ],
        ),
    ],
    "Growth": [
        (
            "Revenge bedtime scrolling steals tomorrow's energy",
            "Borrowing shiny silver coins from midnight forces the exhausted rooster to pay double at sunrise",
            ["borrowing from tomorrow", "a midnight owl raiding dawn", "burning both ends of a taper"],
            [
                "Drinking espresso at five in the evening negotiates poorly with midnight",
                "Setting six snooze alarms trains your brain to ignore trumpets",
                "Buying fancy gym shoes feels productive long before jogging begins",
                "Cleaning your entire desk magically feels urgent whenever hard work waits",
                "Stretching for five minutes saves a week of mysterious back squeaks",
            ],
        ),
        (
            "Buying fancy gear does not replace actually practicing",
            "Polishing a shiny velvet chef hat never teaches the omelet how to flip cleanly in the skillet",
            ["a fancy chef hat", "unused guitar picks", "dusty dumbbells"],
            [
                "Revenge bedtime scrolling steals tomorrow's energy",
                "Downloading seven productivity apps rarely finishes the laundry pile",
                "Reading a cookbook cover to cover never preheats the oven",
                "Starting messy beats rearranging your colorful sticky notes for three hours",
                "Bookmarking tutorials feels like studying until you close the laptop",
            ],
        ),
        (
            "Cleaning your desk suddenly feels urgent when hard work looms",
            "A nervous beaver starts alphabetizing river pebbles the exact minute the big dam needs timber",
            ["alphabetizing pebbles", "polishing spoons before taxes", "reorganizing a pantry"],
            [
                "Buying fancy gear does not replace actually practicing",
                "Revenge bedtime scrolling steals tomorrow's energy",
                "Writing a neat to-do list just to check off 'make a list' is sweet illusion",
                "Taking a tiny first bite shrinks the scariest homework dragon",
                "Closing forty browser tabs clears your mind faster than coffee",
            ],
        ),
    ],
    "Gratitude": [
        (
            "Never go grocery shopping while completely starving",
            "Sending a hungry grizzly through a pastry village before breakfast drains every copper coin",
            ["a bear in a bakery", "a pirate at a buffet", "an empty cart with wings"],
            [
                "Buying snacks for a road trip means eating them ten miles from home",
                "Free samples at the warehouse club turn errands into a tiny banquet",
                "Fresh warm bread straight from the oven beats every luxury gadget",
                "Checking your bank balance after vacation takes real bravery",
                "Finding a crisp twenty-dollar bill in last winter's coat feels like royalty",
            ],
        ),
        (
            "Finding forgotten cash in a winter coat feels like royalty",
            "Discovering an unexpected golden acorn tucked inside last autumn's scarf delights the whole forest",
            ["a golden acorn in a scarf", "a surprise treasure pocket", "rain turning to coins"],
            [
                "Never go grocery shopping while completely starving",
                "Fresh sheets straight from a warm dryer rival five-star resorts",
                "Hitting every green light on the drive home feels like hidden magic",
                "A surprise canceled meeting hands back an hour of pure sunshine",
                "Cold water at three in the morning tastes finer than vintage nectar",
            ],
        ),
        (
            "A surprise canceled meeting feels like pure sunshine",
            "Watching a sudden rainstorm wash away an awkward lawn chore gifts you an afternoon hammock",
            ["an afternoon hammock", "an unexpected snow day", "an open meadow"],
            [
                "Finding forgotten cash in a winter coat feels like royalty",
                "Never trust a coworker who says quick question",
                "Leaving work while the sky is still bright boosts the whole spirit",
                "Sleeping in on a rainy Saturday without an alarm is priceless luxury",
                "Drinking hot cocoa after shoveling snow multiplies every sip",
            ],
        ),
    ],
    "Trust": [
        (
            "Never lend a favorite book you expect to see again",
            "Handing a beloved songbird to a wandering sailor means wishing it lovely skies on new islands",
            ["releasing a songbird", "lending an umbrella", "a boomerang that forgets home"],
            [
                "Always save your work twice before a sudden thunderstorm flickers the lamp",
                "Never let a hungry dog guard a low coffee table full of tacos",
                "Clicking reply-all without double-checking invites awkward legends",
                "Backing up photos early avoids heartbreaking computer funerals",
                "Writing your name inside a shared lunch container barely slows office bandits",
            ],
        ),
        (
            "Never leave a dog alone beside low tacos",
            "Appointing a cheerful raccoon as treasurer of the marshmallow vault invites creative accounting",
            ["a raccoon guarding marshmallows", "a fox guarding a picnic", "a cat near yarn"],
            [
                "Never lend a favorite book you expect to see again",
                "Leaving fresh cookies cooling within reach tests everyone's willpower",
                "A quiet toddler in the next hallway is never simply reading",
                "Never trust a free pen that looks too smooth at the front desk",
                "Putting a sticky note saying do not eat only makes cupcakes glow brighter",
            ],
        ),
        (
            "Always save your work before the storm flickers the lights",
            "Locking the barn latch before thunder rolls saves you from chasing three hours of stray sheep",
            ["latching a barn in thunder", "anchoring a boat early", "backing up a treasure map"],
            [
                "Never lend a favorite book you expect to see again",
                "Testing your microphone before a big speech spares awkward pantomime",
                "Double-checking an attachment before hitting send saves a second apology",
                "Charging your phone to full before an airport sprint brings deep calm",
                "A suspiciously easy software update five minutes before a demo is a trap",
            ],
        ),
    ],
    "Craft": [
        (
            "Turning it off and back on fixes half of technology",
            "Letting a grumpy mule take one short nap beside the clover cures almost every stubborn hoof",
            ["a grumpy mule napping", "shaking an Etch A Sketch", "restarting a stubborn clock"],
            [
                "Assembling flat-pack furniture always leaves one mysterious spare bolt",
                "Reading the instructions after two failed attempts saves the third hour",
                "Labeling your tangle of power cables feels silly until moving day",
                "Always measure the doorway before buying a giant velvet couch",
                "Fixing one squeaky kitchen drawer magically reminds you of four more",
            ],
        ),
        (
            "Assembling flat-pack furniture always leaves a mystery screw",
            "Finishing a complex pirate ship kit only to find one extra brass peg makes every sailor squint",
            ["an extra brass ship peg", "leftover clockwork gears", "a puzzle with a bonus piece"],
            [
                "Turning it off and back on fixes half of technology",
                "A recipe claiming ten-minute prep is pure fiction",
                "Skipping page one of the manual guarantees building the bookshelf backward",
                "Putting the last kitchen bolt in finger-tight first prevents crooked legs",
                "Duct tape solves problems that engineers refuse to discuss publicly",
            ],
        ),
        (
            "Always measure the doorway before buying a giant sofa",
            "Inviting a proud giraffe to tea works far smoother after checking the height of your porch arch",
            ["a giraffe at a porch door", "a ship in a narrow bottle", "an elephant on stairs"],
            [
                "Assembling flat-pack furniture always leaves a mystery screw",
                "Packing light for a flight beats wrestling a stubborn overhead bin",
                "Labeling moving boxes clearly saves you from hunting coffee cups for a week",
                "Testing paint swatches in daylight prevents accidental neon living rooms",
                "Keeping one tiny screwdriver handy rescues half of household disasters",
            ],
        ),
    ],
    "Kindness": [
        (
            "Bringing warm snacks makes you welcome in any room",
            "Arriving at a chilly castle carrying freshly baked cinnamon buns lowers every drawbridge instantly",
            ["cinnamon buns at a castle", "a basket of peaches", "a warm thermos on a hike"],
            [
                "Returning your shopping cart in the rain earns quiet cosmic applause",
                "Letting a hurried driver merge kindly costs three seconds and brightens two commutes",
                "Refilling the office coffee pot after pouring the last cup makes you a legend",
                "Remembering a friend's favorite tea order feels like a warm blanket",
                "Holding the elevator door open turns grumpy Mondays gentler",
            ],
        ),
        (
            "Refilling the coffee pot after the last cup makes you a legend",
            "Planting a fresh pitcher beside the desert oasis earns silent songs from every weary camel behind you",
            ["refilling an oasis pitcher", "stacking firewood for the next camper", "oiling a shared bike"],
            [
                "Bringing warm snacks makes you welcome in any room",
                "Leaving a sticky note of thanks brightens a neighbor's whole evening",
                "Washing the pan right after breakfast gifts your future self pure peace",
                "Sharing your spare umbrella turns a rainy sidewalk into a tiny festival",
                "Returning a borrowed car with a full tank seals lifelong friendship",
            ],
        ),
        (
            "Holding the elevator door open brightens a hurried morning",
            "Pausing the departing harbor ferry for three extra seconds turns a frantic sprint into a grateful grin",
            ["pausing a harbor ferry", "keeping a lantern lit", "saving the last seat"],
            [
                "Bringing warm snacks makes you welcome in any room",
                "Refilling the coffee pot after the last cup makes you a legend",
                "Complimenting a stranger's cool jacket costs zero coins and echoes all afternoon",
                "Leaving a big tip during a thunderstorm spreads invisible warmth",
                "Returning a lost umbrella earns five stars from the universe",
            ],
        ),
    ],
}


def main() -> int:
    entries = []
    seen_ids = set()
    seen_lessons = set()
    errors = []

    def add_pack(themes_dict, pack_name: str, id_prefix: str = ""):
        for theme, items in themes_dict.items():
            for idx, (lesson, bot_metaphor, sparks, decoys) in enumerate(items, start=1):
                entry_id = f"{id_prefix}{theme.lower()}_{idx:02d}"
                if entry_id in seen_ids:
                    errors.append(f"Duplicate id: {entry_id}")
                seen_ids.add(entry_id)

                norm = lesson.strip().lower()
                if norm in seen_lessons:
                    errors.append(f"Duplicate lesson: {lesson}")
                seen_lessons.add(norm)

                sharpened = [DECOY_SHARPEN_MAP.get(d, d) for d in decoys]
                if len(sharpened) < 5:
                    errors.append(f"{entry_id}: needs >= 5 decoys")
                if any(d.strip().lower() == norm for d in sharpened):
                    errors.append(f"{entry_id}: decoy duplicates lesson")
                if len({d.strip().lower() for d in sharpened}) != len(sharpened):
                    errors.append(f"{entry_id}: duplicate decoys")

                if not (12 <= len(bot_metaphor) <= 180):
                    errors.append(f"{entry_id}: botMetaphor len {len(bot_metaphor)} out of bounds")
                if len(re.findall(r"[A-Za-z]+", bot_metaphor)) < 3:
                    errors.append(f"{entry_id}: botMetaphor < 3 words")

                overlap = kt_stems(lesson) & kt_stems(bot_metaphor)
                if overlap:
                    errors.append(
                        f"{entry_id}: stem overlap {sorted(overlap)} | lesson={lesson!r} | metaphor={bot_metaphor!r}"
                    )

                entries.append(
                    {
                        "id": entry_id,
                        "theme": theme,
                        "pack": pack_name,
                        "lesson": lesson,
                        "decoys": sharpened,
                        "sparks": sparks,
                        "botMetaphor": bot_metaphor,
                    }
                )

    add_pack(base.RAW_THEMES, "CLASSIC", "")
    add_pack(MODERN_THEMES, "MODERN", "modern_")

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
    payload = {"version": 2, "count": len(entries), "lessons": entries}
    out_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(entries)} validated lessons (120 CLASSIC + 30 MODERN) across {len(base.RAW_THEMES)} themes to {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

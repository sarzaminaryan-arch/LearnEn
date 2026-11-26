#!/usr/bin/env python3
"""Builds the catalogue decks published next to the app.

Every sentence here is original, written for this project and released under the
same licence as the app. Nothing is sampled from a film, a talk or a corpus,
which is why there is no per-card attribution: there is no third-party author to
credit. docs/CREDITS.csv records that.
"""

import json
import pathlib

OUT = pathlib.Path("catalog/decks")
OUT.mkdir(parents=True, exist_ok=True)


def deck(title, parts, prefix, difficulty_base=0.08):
    """Assembles a deck document from (part_index, [(en, fa, vocab, level), ...])."""
    total = sum(len(cards) for _, cards in parts)
    seen = 0
    out_parts = []
    for index, cards in parts:
        out_cards = []
        for en, fa, vocab, level in cards:
            seen += 1
            out_cards.append(
                {
                    "id": f"{prefix}-{seen:03d}",
                    "english": en,
                    "persian": fa,
                    "vocab": [{"word": w} for w in vocab],
                    "difficulty": round(
                        min(0.95, difficulty_base + (seen / total) * 0.5), 2
                    ),
                    "reviewed": True,
                    "source": "original",
                    "license": "Apache-2.0",
                    "note": level,
                }
            )
        out_parts.append({"index": index, "cards": out_cards})
    return {
        "version": 1,
        "title": title,
        "source_lang": "en",
        "target_lang": "fa",
        "license": "CC BY 4.0",
        "parts": out_parts,
    }


def write(name, document):
    path = OUT / f"{name}.json"
    path.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    cards = sum(len(p["cards"]) for p in document["parts"])
    print(f"{path}  parts={len(document['parts'])} cards={cards}")


# ---------------------------------------------------------------- 1. greetings
EVERYDAY = [
    (1, [
        ("Good morning, how did you sleep?", "صبح بخیر، چطور خوابیدی؟", ["morning", "sleep"], "A1"),
        ("I am a little tired today.", "امروز کمی خسته‌ام.", ["tired", "today"], "A1"),
        ("Would you like some tea?", "چای میل داری؟", ["tea"], "A1"),
        ("Yes please, with sugar.", "بله لطفاً، با شکر.", ["sugar"], "A1"),
        ("What time does the shop open?", "مغازه چه ساعتی باز می‌شود؟", ["shop", "open"], "A1"),
        ("It opens at nine in the morning.", "ساعت نه صبح باز می‌شود.", ["open"], "A1"),
        ("I have not seen you for a while.", "مدتی است تو را ندیده‌ام.", ["while"], "A2"),
        ("I was away for two weeks.", "دو هفته نبودم.", ["away", "week"], "A2"),
        ("How was your trip?", "سفرت چطور بود؟", ["trip"], "A2"),
        ("It was tiring but I enjoyed it.", "خسته‌کننده بود ولی لذت بردم.", ["tiring", "enjoy"], "A2"),
    ]),
    (2, [
        ("Sorry, I did not catch that.", "ببخشید، متوجه نشدم.", ["catch"], "A2"),
        ("Could you say it again more slowly?", "می‌شود آهسته‌تر دوباره بگویید؟", ["slowly", "again"], "A2"),
        ("I am still learning Persian.", "من هنوز فارسی یاد می‌گیرم.", ["learn"], "A1"),
        ("Your accent is very clear.", "لهجه‌ات خیلی واضح است.", ["accent", "clear"], "B1"),
        ("Thank you, that is kind of you.", "ممنون، لطف داری.", ["kind"], "A2"),
        ("Do you mind if I sit here?", "اشکالی ندارد اینجا بنشینم؟", ["sit", "mind"], "A2"),
        ("Not at all, please do.", "اصلاً، بفرمایید.", ["please"], "A1"),
        ("Is this seat taken?", "این صندلی گرفته است؟", ["seat", "taken"], "A2"),
        ("Someone was sitting there a moment ago.", "لحظه‌ای پیش یک نفر اینجا نشسته بود.", ["moment"], "B1"),
        ("I will find another one.", "یکی دیگر پیدا می‌کنم.", ["another", "find"], "A2"),
    ]),
    (3, [
        ("Could I borrow your pen for a moment?", "می‌توانم یک لحظه خودکارت را قرض بگیرم؟", ["borrow", "pen"], "A2"),
        ("Of course, here you are.", "البته، بفرمایید.", ["course"], "A1"),
        ("I will give it back before I leave.", "قبل از رفتن پسش می‌دهم.", ["leave", "give back"], "A2"),
        ("No hurry, I have another one.", "عجله‌ای نیست، یکی دیگر دارم.", ["hurry"], "A2"),
        ("Do you know where the station is?", "می‌دانی ایستگاه کجاست؟", ["station", "know"], "A1"),
        ("Go straight and turn left at the lights.", "مستقیم برو و سر چراغ‌ها به چپ بپیچ.", ["straight", "turn"], "A2"),
        ("How far is it on foot?", "پیاده چقدر دور است؟", ["far", "foot"], "A2"),
        ("About ten minutes, not more.", "حدود ده دقیقه، نه بیشتر.", ["minute"], "A1"),
        ("Thank you, you have been very helpful.", "ممنون، خیلی کمک کردید.", ["helpful"], "B1"),
        ("Good luck with your lesson.", "برای درست موفق باشی.", ["luck", "lesson"], "A2"),
    ]),
]

# ------------------------------------------------------------------- 2. market
MARKET = [
    (1, [
        ("How much is a kilo of these apples?", "یک کیلو از این سیب‌ها چند است؟", ["kilo", "apple"], "A1"),
        ("They are two hundred thousand a kilo.", "کیلویی دویست هزار است.", ["kilo"], "A1"),
        ("That is more than I expected.", "این بیشتر از آن بود که فکر می‌کردم.", ["expect"], "A2"),
        ("The ones over there are cheaper.", "آن‌هایی که آن طرف‌اند ارزان‌ترند.", ["cheaper", "there"], "A2"),
        ("Are they sweet or sour?", "شیرین‌اند یا ترش؟", ["sweet", "sour"], "A1"),
        ("They are a little sour, good for cooking.", "کمی ترش‌اند، برای پختن خوب‌اند.", ["cooking", "good"], "A2"),
        ("I will take two kilos, please.", "دو کیلو می‌خرم، لطفاً.", ["kilo"], "A1"),
        ("Anything else for you today?", "چیز دیگری هم می‌خواهید؟", ["anything", "else"], "A2"),
        ("Do you have fresh bread?", "نان تازه دارید؟", ["fresh", "bread"], "A1"),
        ("It came out of the oven an hour ago.", "یک ساعت پیش از تنور درآمد.", ["oven", "hour"], "B1"),
    ]),
    (2, [
        ("Can I pay by card?", "می‌توانم با کارت پرداخت کنم؟", ["pay", "card"], "A1"),
        ("The machine is not working, cash only.", "دستگاه کار نمی‌کند، فقط نقدی.", ["machine", "cash"], "A2"),
        ("Then I will need to find a cash machine.", "پس باید خودپرداز پیدا کنم.", ["need", "find"], "B1"),
        ("There is one just around the corner.", "یکی همین نزدیکی سر خیابان است.", ["corner"], "A2"),
        ("Could you keep this in the fridge for me?", "می‌شود این را برایم در یخچال بگذارید؟", ["fridge", "keep"], "A2"),
        ("Of course, come back before six.", "البته، قبل از شش برگرد.", ["before", "back"], "A2"),
        ("Do you sell eggs by the tray?", "تخم‌مرغ را شانه‌ای می‌فروشید؟", ["egg", "tray", "sell"], "B1"),
        ("Yes, thirty to a tray.", "بله، سی‌تا در هر شانه.", ["tray"], "A2"),
        ("I only need half a tray.", "من فقط نصف شانه لازم دارم.", ["half", "need"], "A2"),
        ("I can divide it for you.", "می‌توانم برایت تقسیم کنم.", ["divide"], "B1"),
    ]),
    (3, [
        ("This melon looks very ripe.", "این خربزه خیلی رسیده به نظر می‌رسد.", ["melon", "ripe"], "A2"),
        ("Smell it, you will know immediately.", "ببویش، فوراً می‌فهمی.", ["smell", "immediately"], "B1"),
        ("How long will it last at home?", "خانه چند روز می‌ماند؟", ["last", "home"], "A2"),
        ("Two or three days in a cool place.", "دو سه روز در جای خنک.", ["cool", "place"], "A2"),
        ("I will take it, and the grapes too.", "برش می‌دارم، انگورها را هم.", ["grape"], "A1"),
        ("The grapes are from this morning.", "انگورها از همین صبح است.", ["grape", "morning"], "A2"),
        ("Do you have change for a large note?", "برای اسکناس بزرگ خرد دارید؟", ["change", "note", "large"], "B1"),
        ("Let me see what I have.", "بگذار ببینم چه دارم.", ["let"], "A2"),
        ("Here is your change and the receipt.", "خردتان و رسید.", ["change", "receipt"], "A2"),
        ("Thank you, see you next week.", "ممنون، هفتهٔ بعد می‌بینمت.", ["next", "week"], "A1"),
    ]),
    (4, [
        ("Is this the queue for the vegetables?", "این صف سبزی‌هاست؟", ["queue", "vegetable"], "B1"),
        ("Yes, it moves quite fast.", "بله، خیلی سریع جلو می‌رود.", ["move", "fast", "quite"], "A2"),
        ("I have been waiting for ten minutes.", "ده دقیقه است منتظرم.", ["wait"], "A2"),
        ("The other stall is usually quicker.", "آن دکه معمولاً سریع‌تر است.", ["stall", "usually", "quicker"], "B1"),
        ("Do you want the herbs chopped or whole?", "سبزی‌ها را خرد می‌خواهید یا درسته؟", ["herb", "chop", "whole"], "B1"),
        ("Chopped, please, and washed.", "خردشده لطفاً، و شسته‌شده.", ["chop", "wash"], "A2"),
        ("I will wrap them in paper.", "در کاغذ می‌پیچمشان.", ["wrap", "paper"], "A2"),
        ("That is easier to carry.", "آن راحت‌تر حمل می‌شود.", ["easy", "carry"], "A2"),
        ("You have given me too much change.", "خرد زیادی به من دادید.", ["change", "too much"], "B1"),
        ("You are right, here is the rest.", "حق داری، بقیه‌اش.", ["rest", "right"], "A2"),
    ]),
]

# ------------------------------------------------------------------ 3. getting
GETTING = [
    (1, [
        ("Which bus goes to the old town?", "کدام اتوبوس به شهر قدیم می‌رود؟", ["bus", "old", "town"], "A1"),
        ("Take the number seven from that stop.", "شمارهٔ هفت را از آن ایستگاه بگیر.", ["stop", "number"], "A2"),
        ("How often does it come?", "هر چند وقت یک بار می‌آید؟", ["often", "come"], "A2"),
        ("Every fifteen minutes during the day.", "روزها هر پانزده دقیقه.", ["minute", "during"], "B1"),
        ("Does it stop near the museum?", "نزدیک موزه می‌ایستد؟", ["stop", "near", "museum"], "A1"),
        ("It stops right in front of it.", "درست جلوی آن می‌ایستد.", ["front", "right"], "A2"),
        ("How much is a single ticket?", "یک بلیت یک‌طرفه چند است؟", ["single", "ticket"], "A2"),
        ("A single is cheaper than a day pass.", "یک‌طرفه از بلیت روزانه ارزان‌تر است.", ["pass", "cheaper"], "B1"),
        ("Then I will buy a day pass.", "پس بلیت روزانه می‌خرم.", ["pass", "buy"], "A2"),
        ("You can pay the driver directly.", "می‌توانید مستقیم به راننده بپردازید.", ["driver", "directly"], "B1"),
    ]),
    (2, [
        ("Excuse me, am I going the right way?", "ببخشید، درست می‌روم؟", ["excuse", "right way"], "A1"),
        ("For the square, yes, keep going.", "برای میدان، بله، همین‌طور برو.", ["square", "keep going"], "A2"),
        ("You will pass a small park on your left.", "یک پارک کوچک سمت چپت می‌گذرد.", ["pass", "park", "left"], "A2"),
        ("How do I know when to turn?", "از کجا بفهمم کِی بپیچم؟", ["turn", "know"], "B1"),
        ("There is a fountain at the corner.", "سر خیابان یک فواره هست.", ["fountain", "corner"], "A2"),
        ("Turn right as soon as you see it.", "تا دیدیش به راست بپیچ.", ["soon", "see"], "A2"),
        ("Is it far enough to take a taxi?", "آن‌قدر دور است که تاکسی بگیرم؟", ["taxi", "take"], "B1"),
        ("No, it is a short walk.", "نه، پیاده‌روی کوتاهی است.", ["short", "walk"], "A1"),
        ("Thank you for explaining it so clearly.", "ممنون که این‌قدر واضح توضیح دادید.", ["explain", "clearly"], "B1"),
        ("I hope you enjoy the square.", "امیدوارم از میدان لذت ببری.", ["hope", "enjoy"], "A2"),
    ]),
    (3, [
        ("What time is the last train?", "قطار آخر چه ساعتی است؟", ["train", "last"], "A1"),
        ("The last one leaves at half past eleven.", "آخرین قطار یازده و نیم می‌رود.", ["leave", "half", "past"], "A2"),
        ("Do I need to change anywhere?", "باید جایی عوض کنم؟", ["change", "anywhere"], "A2"),
        ("Change once, at the second stop.", "یک بار عوض کن، در ایستگاه دوم.", ["once", "second"], "A2"),
        ("How long is the whole journey?", "کل سفر چقدر طول می‌کشد؟", ["whole", "journey", "long"], "B1"),
        ("About forty minutes if it runs on time.", "حدود چهل دقیقه اگر سر وقت باشد.", ["run", "time"], "B1"),
        ("Is there a place to sit?", "جای نشستن هست؟", ["place", "sit"], "A1"),
        ("Usually, unless it is very busy.", "معمولاً، مگر اینکه خیلی شلوغ باشد.", ["usually", "unless", "busy"], "B1"),
        ("I will leave a little earlier then.", "پس کمی زودتر راه می‌افتم.", ["earlier", "leave"], "A2"),
        ("That is the safer plan.", "این نقشهٔ مطمئن‌تری است.", ["safer", "plan"], "B1"),
    ]),
    (4, [
        ("My phone battery is almost empty.", "باتری گوشی‌ام تقریباً تمام شده.", ["battery", "empty", "almost"], "A2"),
        ("There is a socket behind the counter.", "پشت پیشخوان یک پریز هست.", ["socket", "behind", "counter"], "B1"),
        ("Could I charge it for a few minutes?", "می‌توانم چند دقیقه شارژش کنم؟", ["charge", "few"], "A2"),
        ("Take your time, the next one is late.", "راحت باش، بعدی دیر می‌رسد.", ["late", "next"], "A2"),
        ("I need the map to find the address.", "نقشه را لازم دارم تا آدرس را پیدا کنم.", ["map", "address", "find"], "A2"),
        ("Where exactly are you trying to go?", "دقیقاً کجا می‌خواهی بروی؟", ["exactly", "try"], "B1"),
        ("A small hotel near the river.", "یک هتل کوچک نزدیک رودخانه.", ["hotel", "river", "near"], "A1"),
        ("That is a ten minute walk from here.", "از اینجا ده دقیقه پیاده است.", ["walk", "here"], "A2"),
        ("Do you want me to write it down?", "می‌خواهی برایت بنویسم؟", ["write", "down"], "A2"),
        ("That would be very kind.", "خیلی لطف می‌کنی.", ["kind"], "A2"),
    ]),
]


write("everyday-conversation", deck("Everyday conversation", EVERYDAY, "evd"))
write("at-the-market", deck("At the market", MARKET, "mkt"))
write("getting-around", deck("Getting around", GETTING, "get"))

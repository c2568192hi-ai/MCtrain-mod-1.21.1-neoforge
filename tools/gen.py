#!/usr/bin/env python3
# 数値・素材はこのファイルだけ編集 → python tools/gen.py で再生成
# 生成物: skills.json / data/trainmod/recipe/*.json / data/trainmod/advancement/**.json
import json, os

NS = "trainmod"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")

CATS = {  # key: (名前, アイコン)
    "pow": ("力", "diamond_sword"), "grd": ("防御力", "shield"), "mob": ("足腰", "iron_boots"),
    "vit": ("体力", "golden_apple"), "utl": ("その他", "emerald"), "mag": ("魔法", "enchanted_book"),
}

# 素材表記: "a*3+b" = 全部必要(Lv1のみ) / "x|y" = どれか / "#ns:tag" = タグ
# id, 名前, kind, cat, attr, mode, scale, unit, 値(各Lv合計値), Lv1素材, [(from,to,素材A(template),素材B(addition))]
D = [
 ("atk","攻撃力","body","pow","generic.attack_damage","add",1,"",[1,2,4,5,5.5,6,7,7.5,8,8.5],"rotten_flesh*3",
  [(2,5,"iron_sword","iron_block"),(6,9,"diamond_sword","diamond_block"),(10,10,"netherite_sword","netherite_block")]),
 ("reach","リーチ","body","pow","player.entity_interaction_range|player.block_interaction_range","add",1,"",[0.3,0.5,0.7,1.0,1.3],"stick*5",
  [(2,3,"blaze_rod","breeze_rod"),(4,4,"fishing_rod","phantom_membrane"),(5,5,"trident","trident")]),
 ("kb","ノックバック","body","pow","generic.attack_knockback","add",1,"",[0.5,1.0,1.5],"shroomlight*2+glowstone*4",
  [(2,2,"diamond_block","netherite_ingot"),(3,3,"netherite_block","#minecraft:music_discs")]),
 ("crit","会心率","body","pow",None,"add",0.01,"%",[1,3,5,10,20],"amethyst_shard*3",
  [(2,3,"golden_apple","egg"),(4,4,"heart_of_the_sea","echo_shard"),(5,5,"heavy_core","dragon_breath")]),
 ("aspd","攻撃速度","body","pow","generic.attack_speed","add",1,"",[0.1,0.5,1.0],"iron_pickaxe",
  [(2,2,"iron_pickaxe","diamond_pickaxe"),(3,3,"diamond_pickaxe","netherite_pickaxe")]),

 ("def","防御力","body","grd","generic.armor","add",1,"",[0.5,1,1.5,2,3,3.5,4,4.5,5,6],"obsidian*3",
  [(2,5,"shield","diamond_chestplate"),(6,7,"diamond_chestplate","chainmail_chestplate"),
   (8,9,"netherite_boots","diamond_helmet"),(10,10,"totem_of_undying","netherite_chestplate")]),
 ("tough","靭性","body","grd","generic.armor_toughness","add",1,"",[0.5,2,2.5,3,3.5],"spectral_arrow*5",
  [(2,3,"golden_apple","cake"),(4,4,"diamond","emerald"),(5,5,"enchanted_golden_apple","netherite_scrap")]),
 ("kbres","ノックバック耐性","body","grd","generic.knockback_resistance","add",1,"",[0.1,0.3,0.7],"shield*4",
  [(2,2,"trial_key","iron_ingot"),(3,3,"ominous_trial_key","ancient_debris")]),

 ("speed","移動速度","body","mob","generic.movement_speed","mul",0.01,"%",[1,2,3,5,7,9,11,13,15,20],"sugar_cane*3",
  [(2,5,"twisting_vines","packed_ice"),(6,8,"blue_ice","diamond_boots"),(9,9,"crying_obsidian","raw_gold"),(10,10,"netherite_ingot","firework_rocket")]),
 ("jump","ジャンプ力","body","mob","generic.jump_strength","add",1,"m",[0.5,1,1.5,2,2.5],"ender_pearl",
  [(2,4,"rabbit_foot","ender_eye"),(5,5,"elytra","wind_charge")]),
 ("fall","落下耐性","body","mob","generic.safe_fall_distance","add",1,"m",[1,2,3,4,5,6,7,7.5,8,10],"carrot_on_a_stick*2",
  [(2,5,"oak_boat","minecart"),(6,8,"water_bucket","ochre_froglight|verdant_froglight|pearlescent_froglight"),
   (9,9,"heavy_core","feather"),(10,10,"leather_boots","elytra")]),

 ("regen","自然回復力","body","vit",None,"add",1,"HP/5秒",[0.1,0.2,0.3,0.4,0.5,0.8,1.0,1.2,1.3,1.4],"apple*8",
  [(2,5,"cake","gold_ingot"),(6,9,"turtle_helmet","nautilus_shell"),(10,10,"dragon_breath","end_crystal")]),
 ("sat","隠し満腹度維持","body","vit",None,"add",1,"",[1,1.5,2,2.5,3.5],"bread*7",
  [(2,3,"ladder","golden_carrot"),(4,4,"cake","honey_block"),(5,5,"enchanted_golden_apple","enchanted_golden_apple")]),
 ("absorb","衝撃吸収","body","vit","generic.max_absorption","add",1,"",[2,4,6,8,10,12,16,20,24,28],"cobweb*2",
  [(2,5,"melon","copper_ingot"),(6,8,"phantom_membrane","chainmail_chestplate"),
   (9,9,"wet_sponge","wind_charge"),(10,10,"enchanted_golden_apple","heavy_core")]),

 ("luck","幸運","body","utl","generic.luck","add",1,"",[1,2,3],"sunflower+poppy+tropical_fish",
  [(2,2,"ghast_tear","experience_bottle"),(3,3,"poisonous_potato","ominous_bottle")]),
 ("kyushi","九死に一生","body","utl",None,"add",1,"",[1],"totem_of_undying",[]),

 ("vamp","吸血","magic","mag",None,"add",0.01,"%",[3,4,6,8,10],"quartz*2+redstone",
  [(2,4,"redstone_block","fermented_spider_eye"),(5,5,"echo_shard","arrow")]),
 ("xpb","経験値ブースト","magic","mag",None,"add",1,"倍",[1.5,1.8,2.0],"emerald*8",
  [(2,2,"emerald_block","golden_apple"),(3,3,"experience_bottle","enchanting_table")]),
 ("sub","身代わり","magic","mag",None,"add",0.01,"%",[3,6,9],"armor_stand*2",
  [(2,2,"goat_horn","spyglass"),(3,3,"totem_of_undying","golden_apple")]),
]

TXT = {  # 効果文の特殊表記
    "vamp": "与ダメージの{v}%をHP回復", "xpb": "経験値 ×{v}", "sub": "被ダメージの{v}%を肩代わり(1部位あたり)",
    "kyushi": "致命傷でHP全回復+3秒無敵 (CT 1時間)",
}

NAMES = {
 "paper":"紙","rotten_flesh":"腐った肉","iron_sword":"鉄の剣","iron_block":"鉄ブロック","diamond_sword":"ダイヤモンドの剣",
 "diamond_block":"ダイヤモンドブロック","netherite_sword":"ネザライトの剣","netherite_block":"ネザライトブロック","stick":"棒",
 "blaze_rod":"ブレイズロッド","breeze_rod":"ブリーズロッド","fishing_rod":"釣り竿","phantom_membrane":"ファントムの皮膜",
 "trident":"トライデント","shroomlight":"シュルームライト","glowstone":"グロウストーン","netherite_ingot":"ネザライトインゴット",
 "#minecraft:music_discs":"音楽ディスク(何でも)","amethyst_shard":"アメジストの欠片","golden_apple":"金のリンゴ","egg":"卵",
 "heart_of_the_sea":"海洋の心","echo_shard":"残響の欠片","heavy_core":"ヘビーコア","dragon_breath":"ドラゴンブレス",
 "iron_pickaxe":"鉄のツルハシ","diamond_pickaxe":"ダイヤモンドのツルハシ","netherite_pickaxe":"ネザライトのツルハシ",
 "obsidian":"黒曜石","shield":"盾","diamond_chestplate":"ダイヤモンドのチェストプレート","chainmail_chestplate":"チェーンのチェストプレート",
 "netherite_boots":"ネザライトのブーツ","diamond_helmet":"ダイヤモンドのヘルメット","totem_of_undying":"不死のトーテム",
 "netherite_chestplate":"ネザライトのチェストプレート","spectral_arrow":"光の矢","cake":"ケーキ","diamond":"ダイヤモンド",
 "emerald":"エメラルド","enchanted_golden_apple":"エンチャントされた金のリンゴ","netherite_scrap":"ネザライトの欠片",
 "trial_key":"試練の鍵","ominous_trial_key":"不吉な試練の鍵","iron_ingot":"鉄インゴット","ancient_debris":"古代の残骸",
 "sugar_cane":"サトウキビ","twisting_vines":"ねじれたツタ","ice":"氷","packed_ice":"氷塊","blue_ice":"青氷","diamond_boots":"ダイヤモンドのブーツ",
 "crying_obsidian":"泣く黒曜石","raw_gold":"金の原石","firework_rocket":"ロケット花火","ender_pearl":"エンダーパール",
 "rabbit_foot":"ウサギの足","ender_eye":"エンダーアイ","elytra":"エリトラ","wind_charge":"ウィンドチャージ",
 "carrot_on_a_stick":"ニンジン付きの棒","oak_boat":"オークのボート","minecart":"トロッコ","water_bucket":"水入りバケツ",
 "ochre_froglight|verdant_froglight|pearlescent_froglight":"フロッグライト(何色でも)","feather":"羽根","leather_boots":"革のブーツ",
 "apple":"リンゴ","gold_ingot":"金インゴット","turtle_helmet":"カメの甲羅","nautilus_shell":"オウムガイの殻","end_crystal":"エンドクリスタル",
 "bread":"パン","ladder":"はしご","golden_carrot":"金のニンジン","honey_block":"ハチミツブロック","cobweb":"クモの巣",
 "melon":"スイカ(ブロック)","copper_ingot":"銅インゴット","wet_sponge":"濡れたスポンジ","sunflower":"ヒマワリ","poppy":"ポピー",
 "tropical_fish":"熱帯魚","ghast_tear":"ガストの涙","experience_bottle":"経験値瓶","poisonous_potato":"青くなったジャガイモ",
 "ominous_bottle":"不吉な瓶","quartz":"ネザークォーツ","redstone":"レッドストーン","redstone_block":"レッドストーンブロック",
 "fermented_spider_eye":"発酵したクモの目","arrow":"矢","emerald_block":"エメラルドブロック","enchanting_table":"エンチャントテーブル",
 "armor_stand":"防具立て","goat_horn":"ヤギの角笛","spyglass":"望遠鏡",
}


def jp(x): return NAMES.get(x, x)


def recipe_text(s, lv):
    """実績の説明用: Lv1=作業台 / Lv2以降=鍛冶台"""
    book = "強化書" if s[2] == "magic" else "訓練書"
    if lv == 1:
        parts = []
        for part in ("paper+" + s[9]).split("+"):
            n = 1
            if "*" in part: part, n = part.split("*"); n = int(n)
            parts.append(jp(part) + (f"×{n}" if n > 1 else ""))
        return "作業台: " + " + ".join(parts)
    for a, b, A, B in s[10]:
        if a <= lv <= b:
            return f"鍛冶台: {book}(Lv.{lv-1}) + {jp(A)} + {jp(B)}"
    return ""


def jd(rel, obj):
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))


def tj(o): return json.dumps(o, ensure_ascii=False, separators=(",", ":"))


def fmt(v): return "%g" % v


def ing(s):
    if s.startswith("#"): return {"tag": s[1:]}
    if "|" in s: return [{"item": "minecraft:" + x} for x in s.split("|")]
    return {"item": "minecraft:" + s}


def lv1(spec):
    out = []
    for part in spec.split("+"):
        n = 1
        if "*" in part: part, n = part.split("*"); n = int(n)
        out += [ing(part)] * n
    return out


def eff(s, lv):
    sid, name, unit, vals = s[0], s[1], s[7], s[8]
    v = fmt(vals[lv - 1])
    return TXT[sid].format(v=v) if sid in TXT else f"{name} +{v}{unit}"


def data_tag(sid, lv): return {"skill_type": sid, "level": lv}


def result(s, lv):
    magic = s[2] == "magic"
    return {"id": "minecraft:bedrock", "count": 1, "components": {
        "minecraft:custom_data": data_tag(s[0], lv),
        "minecraft:enchantment_glint_override": True,
        # 1.21.1 の item_name / lore は「JSON文字列を文字列として包む」形式(FLAT_CODEC)
        "minecraft:item_name": tj({"text": f"{'強化書' if magic else '訓練書'}: {s[1]} Lv.{lv}"}),
        "minecraft:lore": [tj({"text": eff(s, lv), "italic": False}),
                           tj({"text": "金床で武器/防具に付与" if magic else "Shift+右クリックで習得",
                               "italic": False, "color": "gray"})],
    }}


def adv(path, parent, icon, title, desc, frame="task", toast=False, trig="minecraft:impossible", bg=None, hidden=False):
    disp = {"icon": {"id": "minecraft:" + icon}, "title": title, "description": desc,
            "frame": frame, "show_toast": toast, "announce_to_chat": toast}
    if bg: disp["background"] = bg
    if hidden: disp["hidden"] = True
    d = {"display": disp, "criteria": {"done": {"trigger": trig}}}
    if parent: d["parent"] = f"{NS}:{parent}"
    jd(f"data/{NS}/advancement/{path}.json", d)


# ---- skills.json (Java が読む) ----
jd("skills.json", [{"id": s[0], "name": s[1], "kind": s[2], "attr": s[4], "mul": s[5] == "mul",
                    "scale": s[6], "vals": s[8]} for s in D])

# ---- recipes ----
for s in D:
    sid, n = s[0], len(s[8])
    jd(f"data/{NS}/recipe/{sid}_1.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:paper"}] + lv1(s[9]), "result": result(s, 1)})
    for a, b, A, B in s[10]:
        for lv in range(a, b + 1):
            # 前Lvの本はベース枠、素材2つは残りの枠。どちらの順でも入れられるよう両方向を生成
            for suffix, t, ad in (("", A, B), ("_r", B, A)):
                if suffix and A == B: continue
                jd(f"data/{NS}/recipe/{sid}_{lv}{suffix}.json", {"type": "minecraft:smithing_transform",
                    "template": ing(t),
                    "base": {"type": "neoforge:components", "items": "minecraft:bedrock",
                             "components": {"minecraft:custom_data": data_tag(sid, lv - 1)}},
                    "addition": ing(ad), "result": result(s, lv)})
    have = 1 + sum(b - a + 1 for a, b, _, _ in s[10])
    assert have == n, f"{sid}: レシピ{have}個 / Lv{n}"

# ---- advancements ----
adv("root", None, "bedrock", "訓練のはじまり", "身体を鍛え、装備に力を宿そう",
    trig="minecraft:tick", bg="minecraft:textures/gui/advancements/backgrounds/stone.png")
for k, (name, icon) in CATS.items():
    adv(f"cat/{k}", "root", icon, name, f"{name}の訓練", trig="minecraft:tick")
for s in D:
    sid, name, kind, cat, n = s[0], s[1], s[2], s[3], len(s[8])
    folder = "magic" if kind == "magic" else "body"
    for lv in range(1, n + 1):
        parent = f"cat/{cat}" if lv == 1 else f"{folder}/{sid}_{lv-1}"
        top = lv == n
        adv(f"{folder}/{sid}_{lv}", parent, "bedrock", f"{name} Lv.{lv}" + (" (MAX)" if top else ""),
            eff(s, lv) + "\n" + recipe_text(s, lv), frame="challenge" if top else "task", toast=top)
# 特別進捗: 条件を満たすまで進捗画面に出ない(hidden)。all は body の先に繋がる
adv("sp/body", "root", "netherite_chestplate", "身体強化の極み", "身体強化をすべて最大まで習得", "challenge", True, hidden=True)
adv("sp/magic", "root", "enchanted_book", "装備強化の極み", "装備強化(魔法)をすべて最大まで付与", "challenge", True, hidden=True)
adv("sp/all", "sp/body", "nether_star", "訓練の果てに", "すべての強化を全達成", "challenge", True, hidden=True)
print("done:", sum(len(s[8]) for s in D), "levels")

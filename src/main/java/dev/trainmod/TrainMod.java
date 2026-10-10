package dev.trainmod;

import com.google.gson.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * サーバーサイド限定: レジストリ登録・ネットワーク・クライアント側コードなし。
 * 数値/素材は skills.json (tools/gen.py が生成) から読む。
 */
@Mod(TrainMod.ID)
@EventBusSubscriber(modid = TrainMod.ID)
public class TrainMod {
    public static final String ID = "trainmod";
    static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    static final long KYUSHI_CD = 72000, INVUL_TICKS = 60;

    // ---------- スキル定義 ----------
    record Skill(String id, String name, boolean magic, String attr, boolean mul, double scale, double[] vals) {
        int max() { return vals.length; }
        double val(int lv) { return vals[lv - 1] * scale; }
    }

    static final Map<String, Skill> S = new LinkedHashMap<>();

    static {
        try (var r = new InputStreamReader(TrainMod.class.getResourceAsStream("/skills.json"), StandardCharsets.UTF_8)) {
            for (JsonElement el : JsonParser.parseReader(r).getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                JsonArray va = o.getAsJsonArray("vals");
                double[] v = new double[va.size()];
                for (int i = 0; i < v.length; i++) v[i] = va.get(i).getAsDouble();
                String id = o.get("id").getAsString();
                S.put(id, new Skill(id, o.get("name").getAsString(), o.get("kind").getAsString().equals("magic"),
                        o.get("attr").isJsonNull() ? null : o.get("attr").getAsString(),
                        o.get("mul").getAsBoolean(), o.get("scale").getAsDouble(), v));
            }
        } catch (Exception ex) {
            throw new RuntimeException("skills.json の読み込みに失敗", ex);
        }
    }

    // ---------- プレイヤーデータ (persistentData) ----------
    static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(ID)) root.put(ID, new CompoundTag());
        return root.getCompound(ID);
    }

    static int lv(Player p, String id) { return data(p).getInt(id); }

    static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }

    static void msg(Player p, String s) { p.displayClientMessage(Component.literal(s), true); }

    static void grant(ServerPlayer p, String path) {
        var h = p.server.getAdvancements().get(rl(path));
        if (h != null) p.getAdvancements().award(h, "done");
    }

    // ---------- 訓練書 (bedrock + custom_data) ----------
    record Book(String id, int lv) {
        static Book of(ItemStack st) {
            if (!st.is(Items.BEDROCK)) return null;
            CustomData cd = st.get(DataComponents.CUSTOM_DATA);
            if (cd == null) return null;
            CompoundTag t = cd.getUnsafe();
            return t.contains("skill_type") ? new Book(t.getString("skill_type"), t.getInt("level")) : null;
        }
    }

    // ---------- 属性の適用 (ログイン/習得/リスポーン時) ----------
    static double amount(Skill s, int l) {
        double v = s.id().equals("jump") ? jumpStrength(s.val(l)) : s.val(l);
        return s.id().equals("absorb") ? Math.min(v, 40) : v;
    }

    /** 習得済みスキルを属性に反映。毎秒呼ぶので、変身など他MODの処理で消えても自動で復旧する */
    static void apply(ServerPlayer p) {
        for (Skill s : S.values()) {
            if (s.attr() == null) continue;
            var holder = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.withDefaultNamespace(s.attr()));
            if (holder.isEmpty()) continue;
            AttributeInstance a = p.getAttribute(holder.get());
            if (a == null) continue;
            int l = lv(p, s.id());
            ResourceLocation id = rl(s.id());
            if (l <= 0) { if (a.hasModifier(id)) a.removeModifier(id); continue; }
            double v = amount(s, l);
            AttributeModifier cur = a.getModifier(id);
            if (cur != null && Math.abs(cur.amount() - v) < 1e-9) continue; // すでに正しい
            a.addOrReplacePermanentModifier(new AttributeModifier(id, v,
                    s.mul() ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** 「+Nブロック高く跳べる」を jump_strength の加算値に換算 (基準0.42) */
    static double jumpStrength(double extraBlocks) {
        double target = height(0.42) + extraBlocks, lo = 0.42, hi = 3;
        for (int i = 0; i < 30; i++) {
            double m = (lo + hi) / 2;
            if (height(m) < target) lo = m; else hi = m;
        }
        return lo - 0.42;
    }

    static double height(double v) {
        double y = 0;
        while (v > 0) { y += v; v = (v - 0.08) * 0.98; }
        return y;
    }

    // ---------- 習得 (スニーク+右クリック) ----------
    static void learn(ServerPlayer p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        Book b = Book.of(st);
        Skill s = b == null ? null : S.get(b.id());
        if (s == null || b.lv() < 1 || b.lv() > s.max()) return;
        if (s.magic()) { msg(p, "§7この強化書は金床で武器/防具に付与します"); return; }
        int cur = lv(p, s.id());
        if (cur >= b.lv()) { msg(p, "§7すでに習得済みです"); return; }
        if (cur != b.lv() - 1) { msg(p, "§c前のレベルを習得していません"); return; } // 消費しない
        data(p).putInt(s.id(), b.lv());
        st.shrink(1);
        apply(p);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
        msg(p, "§a" + s.name() + " Lv." + b.lv() + " を習得");
        grant(p, "body/" + s.id() + "_" + b.lv());
        checkSpecials(p);
    }

    static void checkSpecials(ServerPlayer p) {
        var d = data(p);
        boolean body = true, mag = true;
        for (Skill s : S.values()) {
            if (s.magic()) mag &= d.getInt("m_" + s.id()) >= s.max();
            else body &= d.getInt(s.id()) >= s.max();
        }
        if (body) grant(p, "sp/body");
        if (mag) grant(p, "sp/magic");
        if (body && mag) grant(p, "sp/all");
    }

    @SubscribeEvent
    static void onRightItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getEntity() instanceof ServerPlayer p && p.isShiftKeyDown()) learn(p, e.getHand());
    }

    @SubscribeEvent
    static void onRightBlock(PlayerInteractEvent.RightClickBlock e) {
        if (Book.of(e.getItemStack()) == null) return;
        e.setUseItem(TriState.FALSE); // 岩盤として設置されないように
        if (e.getEntity() instanceof ServerPlayer p && p.isShiftKeyDown()) learn(p, e.getHand());
    }

    // ---------- 装備の魔法 (custom_data の tm_<id>) ----------
    static int magic(ItemStack st, String id) {
        CustomData c = st.get(DataComponents.CUSTOM_DATA);
        return c == null ? 0 : c.getUnsafe().getInt("tm_" + id);
    }

    static void setMagic(ItemStack st, String id, int l) {
        CustomData.update(DataComponents.CUSTOM_DATA, st, t -> t.putInt("tm_" + id, l));
        List<Component> lore = new ArrayList<>();
        for (Skill s : S.values())
            if (s.magic() && magic(st, s.id()) > 0)
                lore.add(Component.literal(s.name() + " Lv." + magic(st, s.id())).withStyle(ChatFormatting.AQUA));
        st.set(DataComponents.LORE, new ItemLore(lore)); // 既存loreは上書き
        st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    // ---------- Dragon Survival の爪スロット (入っていなければ何もしない) ----------
    static java.lang.reflect.Method DS_GET, DS_CONTAINER;
    static boolean dsTried;

    static List<ItemStack> clawItems(Player p) {
        try {
            if (!dsTried) {
                dsTried = true;
                Class<?> c = Class.forName("by.dragonsurvivalteam.dragonsurvival.registry.attachments.ClawInventoryData");
                DS_GET = c.getMethod("getData", Player.class);
                DS_CONTAINER = c.getMethod("getContainer");
            }
            if (DS_GET == null) return List.of();
            var cont = (SimpleContainer) DS_CONTAINER.invoke(DS_GET.invoke(null, p));
            List<ItemStack> out = new ArrayList<>();
            for (int i = 0; i < cont.getContainerSize(); i++) out.add(cont.getItem(i));
            return out;
        } catch (Throwable t) {
            DS_GET = null;
            return List.of();
        }
    }

    /** メインハンド + 爪スロットの最大レベル (swordOnly=爪スロットは剣だけ見る) */
    static int bestMagic(Player p, String id, boolean swordOnly) {
        int l = magic(p.getMainHandItem(), id);
        for (ItemStack st : clawItems(p))
            if (!swordOnly || st.is(ItemTags.SWORDS)) l = Math.max(l, magic(st, id));
        return l;
    }

    static boolean canApply(String id, ItemStack st) {
        Item i = st.getItem();
        return switch (id) {
            case "vamp" -> st.is(ItemTags.SWORDS) || st.is(ItemTags.AXES) || i == Items.MACE || i == Items.TRIDENT;
            case "xpb" -> i instanceof TieredItem || i instanceof ProjectileWeaponItem || i instanceof FishingRodItem
                    || i == Items.MACE || i == Items.TRIDENT;
            case "sub" -> i instanceof ArmorItem;
            default -> false;
        };
    }

    @SubscribeEvent
    static void onAnvil(AnvilUpdateEvent e) {
        Book b = Book.of(e.getRight());
        Skill s = b == null ? null : S.get(b.id());
        if (s == null || !s.magic() || b.lv() < 1 || b.lv() > s.max()) return;
        ItemStack gear = e.getLeft();
        if (!canApply(s.id(), gear) || magic(gear, s.id()) >= b.lv()) return;
        ItemStack out = gear.copy();
        setMagic(out, s.id(), b.lv());
        e.setOutput(out);
        e.setCost(b.lv() * 3L);
        e.setMaterialCost(1);
    }

    @SubscribeEvent
    static void onAnvilTaken(AnvilRepairEvent e) {
        Book b = Book.of(e.getRight());
        Skill s = b == null ? null : S.get(b.id());
        if (s == null || !s.magic() || !(e.getEntity() instanceof ServerPlayer p)) return;
        var d = data(p);
        d.putInt("m_" + s.id(), Math.max(d.getInt("m_" + s.id()), b.lv()));
        grant(p, "magic/" + s.id() + "_" + b.lv());
        checkSpecials(p);
    }

    // ---------- ダメージ ----------
    @SubscribeEvent
    static void onIncoming(LivingIncomingDamageEvent e) {
        var src = e.getSource();
        // 会心 (近接のみ)
        if (src.getEntity() instanceof ServerPlayer a && src.getDirectEntity() == a) {
            int c = lv(a, "crit");
            if (c > 0 && a.getRandom().nextFloat() < S.get("crit").val(c)) {
                e.setAmount(e.getAmount() * 1.5f);
                if (a.level() instanceof ServerLevel sl)
                    sl.sendParticles(ParticleTypes.CRIT, e.getEntity().getX(), e.getEntity().getY(0.5), e.getEntity().getZ(), 8, .2, .2, .2, .1);
            }
        }
        if (!(e.getEntity() instanceof ServerPlayer v)) return;
        // 九死に一生の無敵時間
        if (v.level().getGameTime() < data(v).getLong("invul")) { e.setCanceled(true); return; }
        // 身代わり (防具ごとに軽減率を合算、最大HPの70%まで)
        double r = 0; int n = 0;
        for (EquipmentSlot sl : ARMOR) {
            int l = magic(v.getItemBySlot(sl), "sub");
            if (l > 0) { r += S.get("sub").val(l); n++; }
        }
        if (n > 0) {
            float red = (float) Math.min(e.getAmount() * r, v.getMaxHealth() * 0.7);
            e.setAmount(e.getAmount() - red);
            int cost = Math.max(1, (int) Math.ceil(red / n));
            for (EquipmentSlot sl : ARMOR) {
                ItemStack st = v.getItemBySlot(sl);
                if (magic(st, "sub") > 0) st.hurtAndBreak(cost, v, sl);
            }
        }
    }

    /** 九死に一生: 防具軽減後の最終ダメージが致死量のとき発動 */
    @SubscribeEvent
    static void onPre(LivingDamageEvent.Pre e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || lv(p, "kyushi") < 1) return;
        var d = data(p);
        long now = p.level().getGameTime();
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || now < d.getLong("kcd")) return;
        if (e.getNewDamage() < p.getHealth() + p.getAbsorptionAmount()) return;
        e.setNewDamage(0);
        p.setHealth(p.getMaxHealth());
        d.putLong("kcd", now + KYUSHI_CD);
        d.putLong("invul", now + INVUL_TICKS);
        p.level().broadcastEntityEvent(p, (byte) 35); // トーテム演出
        msg(p, "§6九死に一生が発動！");
    }

    /** 吸血: 実際に与えたダメージ基準 */
    @SubscribeEvent
    static void onPost(LivingDamageEvent.Post e) {
        var src = e.getSource();
        if (src.getEntity() instanceof ServerPlayer a && src.getDirectEntity() == a) {
            int l = bestMagic(a, "vamp", true);
            if (l > 0) a.heal((float) (e.getNewDamage() * S.get("vamp").val(l)));
        }
    }

    // ---------- 経験値ブースト ----------
    @SubscribeEvent
    static void onXp(PlayerXpEvent.XpChange e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() <= 0) return;
        int l = bestMagic(p, "xpb", false);
        if (l > 0) e.setAmount((int) Math.round(e.getAmount() * S.get("xpb").val(l)));
    }

    // ---------- 毎秒処理 (自然回復/満腹度/衝撃吸収) ----------
    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 0 || !p.isAlive()) return;
        apply(p); // 属性の自動復旧
        var d = data(p);
        int t = p.tickCount;
        int r = d.getInt("regen");
        if (t % 100 == 0 && r > 0 && p.getHealth() < p.getMaxHealth()) p.heal((float) S.get("regen").val(r));
        int s = d.getInt("sat");
        if (t % 100 == 0 && s > 0) { // 仮仕様: 5秒ごとに 値×0.1 の隠し満腹度を補充
            var f = p.getFoodData();
            f.setSaturation(Math.min(f.getFoodLevel(), f.getSaturationLevel() + (float) (S.get("sat").val(s) * 0.1)));
        }
        int a = d.getInt("absorb");
        if (t % 40 == 0 && a > 0) { // 仮仕様: 2秒ごとに+1、上限=習得値
            float cap = (float) Math.min(40, S.get("absorb").val(a));
            if (p.getAbsorptionAmount() < cap) p.setAbsorptionAmount(Math.min(cap, p.getAbsorptionAmount() + 1));
        }
    }

    // ---------- ログイン/リスポーン ----------
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) apply(p); }

    @SubscribeEvent
    static void onClone(PlayerEvent.Clone e) { e.getEntity().getPersistentData().put(ID, data(e.getOriginal()).copy()); }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) apply(p); }
}

package com.tankbattle.server.system;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.model.Direction;
import com.tankbattle.server.TestContexts;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.entity.Base;
import com.tankbattle.server.entity.BrickWall;
import com.tankbattle.server.entity.Bullet;
import com.tankbattle.server.entity.EnemyTank;
import com.tankbattle.server.entity.EnemyType;
import com.tankbattle.server.entity.PlayerTank;
import com.tankbattle.server.entity.SteelWall;
import com.tankbattle.server.entity.Tank;
import com.tankbattle.server.entity.Vec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 命中规则管线（规则表 + "命中即消失"终止语义）。
 * <p>
 * 规则矩阵：玩家弹×{砖墙/钢墙/玩家弹/敌弹/玩家/敌坦克/基地}、敌弹×{...}。
 * 另含：护甲墙未破时一发子弹不得同帧触达基地（护甲护基地）。
 */
class CombatSystemTest {

    // ------------------------------------------------------------------
    // 装配辅助
    // ------------------------------------------------------------------

    private static GameContext world() {
        GameContext ctx = TestContexts.create();
        ctx.installPlayers(new PlayerTank(1), new PlayerTank(2));
        return ctx;
    }

    private static Bullet fire(GameContext ctx, Tank owner, int x, int y, Direction dir, int power) {
        Bullet bullet = new Bullet(new Vec(x, y), dir, 4, power, owner);
        ctx.addActor(bullet);
        return bullet;
    }

    /**
     * 按真实几何装配基地与 4 面护甲（与 {@code LevelLoader} 一致）；可覆写下左护甲的初始位图。
     */
    private static Base installBase(GameContext ctx, int leftLowerInitialBits) {
        ctx.actors().clear();
        ctx.decor().clear();
        ctx.grid().clear();
        ctx.addActor(new BrickWall(GameConfig.ARMOR_X2, GameConfig.ARMOR_Y2,
                BrickWall.bitsForOrientation(GameConfig.ARMOR_O2)));
        ctx.addActor(new BrickWall(GameConfig.ARMOR_X3, GameConfig.ARMOR_Y3,
                BrickWall.bitsForOrientation(GameConfig.ARMOR_O3)));
        ctx.addActor(new BrickWall(GameConfig.ARMOR_X0, GameConfig.ARMOR_Y0, leftLowerInitialBits));
        ctx.addActor(new BrickWall(GameConfig.ARMOR_X1, GameConfig.ARMOR_Y1,
                BrickWall.bitsForOrientation(GameConfig.ARMOR_O1)));
        Base base = new Base();
        ctx.installBase(base);
        ctx.addActor(base);
        ctx.rebuildStaticIndex();
        return base;
    }

    // ------------------------------------------------------------------
    // 墙体规则
    // ------------------------------------------------------------------

    @Test
    void playerBulletBreaksBrickAndDies() {
        GameContext ctx = world();
        BrickWall wall = new BrickWall(100, 100, BrickWall.FULL_BITS);
        ctx.addActor(wall);
        // 只压到象限 0 顶面（不触守卫子块 b），与 BrickWallTest 几何一致
        Bullet bullet = fire(ctx, ctx.p1(), 93, 90, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, bullet);

        assertEquals(0b0011, wall.shapeBits(), "下行首击毁象限 0 顶面 a,b");
        assertTrue(bullet.destroyed(), "命中砖墙子弹消失");
        assertFalse(wall.destroyed(), "墙体本身未从舞台移除");
    }

    @Test
    void enemyBulletBreaksBrickAndDies() {
        GameContext ctx = world();
        BrickWall wall = new BrickWall(100, 100, BrickWall.FULL_BITS);
        ctx.addActor(wall);
        EnemyTank enemy = new EnemyTank(EnemyType.BASIC, false, 100, 60, new java.util.Random(1));
        Bullet bullet = fire(ctx, enemy, 93, 90, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, bullet);

        assertEquals(0b0011, wall.shapeBits());
        assertTrue(bullet.destroyed());
    }

    @Test
    void normalBulletStoppedBySteelWithoutDamage() {
        GameContext ctx = world();
        SteelWall steel = new SteelWall(100, 100, SteelWall.FULL_BITS);
        ctx.addActor(steel);
        Bullet bullet = fire(ctx, ctx.p1(), 93, 90, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, bullet);

        assertEquals(SteelWall.FULL_BITS, steel.shapeBits(), "普通弹对钢墙无伤");
        assertTrue(bullet.destroyed());
    }

    @Test
    void superBulletBreaksSteelQuadrants() {
        GameContext ctx = world();
        SteelWall steel = new SteelWall(100, 100, SteelWall.FULL_BITS);
        ctx.addActor(steel);
        // 压到象限 0+1（横向跨列），威力 2 逐象限摧毁
        Bullet bullet = fire(ctx, ctx.p1(), 100, 90, Direction.DOWN, 2);

        CombatSystem.resolve(ctx, bullet);

        assertTrue(steel.shapeBits() != SteelWall.FULL_BITS, "超级弹摧毁命中象限");
        assertTrue(steel.shapeBits() == 0b0011 || steel.shapeBits() == 0b1111,
                "位图只能是被命中象限的置位组合，实际=" + steel.shapeBits());
        assertTrue(bullet.destroyed());
    }

    @Test
    void bulletHitsOnlyFirstWallWhenTwoOverlap() {
        GameContext ctx = world();
        BrickWall first = new BrickWall(100, 100, BrickWall.FULL_BITS);
        BrickWall second = new BrickWall(100, 125, BrickWall.FULL_BITS);
        ctx.addActor(first);
        ctx.addActor(second);
        // 纵向长弹盒同时覆盖上墙底象限与下墙顶象限
        Bullet bullet = fire(ctx, ctx.p1(), 93, 118, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, bullet);

        assertTrue(bullet.destroyed());
        assertTrue(first.shapeBits() != BrickWall.FULL_BITS || second.shapeBits() != BrickWall.FULL_BITS,
                "至少命中一面墙");
        assertTrue(first.shapeBits() == BrickWall.FULL_BITS || second.shapeBits() == BrickWall.FULL_BITS,
                "命中即终止：同帧只结算一面墙");
    }

    // ------------------------------------------------------------------
    // 子弹 × 子弹
    // ------------------------------------------------------------------

    @Test
    void playerBulletsCancelEachOther() {
        GameContext ctx = world();
        Bullet a = fire(ctx, ctx.p1(), 200, 300, Direction.DOWN, 1);
        Bullet b = fire(ctx, ctx.p2(), 200, 300, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, a);

        assertTrue(a.destroyed());
        assertTrue(b.destroyed(), "P1/P2 玩家弹互消（E7）");
    }

    @Test
    void enemyBulletCancelsPlayerBullet() {
        GameContext ctx = world();
        EnemyTank enemy = new EnemyTank(EnemyType.BASIC, false, 100, 60, new java.util.Random(1));
        Bullet player = fire(ctx, ctx.p1(), 200, 300, Direction.DOWN, 1);
        Bullet hostile = fire(ctx, enemy, 200, 300, Direction.DOWN, 1);

        // 相消判定只看"对方是否玩家弹"，由敌弹的结算完成同归
        CombatSystem.resolve(ctx, hostile);

        assertTrue(hostile.destroyed());
        assertTrue(player.destroyed(), "玩家弹×敌弹同归");
    }

    @Test
    void playerBulletAdvancesPastEnemyBulletThenPairCancels() {
        GameContext ctx = world();
        EnemyTank enemy = new EnemyTank(EnemyType.BASIC, false, 100, 60, new java.util.Random(1));
        Bullet player = fire(ctx, ctx.p1(), 200, 300, Direction.DOWN, 1);
        Bullet hostile = fire(ctx, enemy, 200, 300, Direction.DOWN, 1);

        // 帧序：玩家弹先结算时不与敌弹交互，推进后仍与敌弹重叠；
        // 敌弹结算时看到玩家弹 → 同归。
        CombatSystem.resolve(ctx, player);
        assertFalse(player.destroyed(), "玩家弹结算不触发与敌弹的相消");
        assertFalse(hostile.destroyed());

        CombatSystem.resolve(ctx, hostile);
        assertTrue(player.destroyed() || !player.border().intersects(hostile.border()),
                "敌弹结算时相消，或弹已错开");
        assertTrue(hostile.destroyed() || !hostile.border().intersects(player.border()));
    }

    @Test
    void enemyBulletsPassThroughEachOther() {
        GameContext ctx = world();
        EnemyTank shooterA = new EnemyTank(EnemyType.BASIC, false, 100, 60, new java.util.Random(1));
        EnemyTank shooterB = new EnemyTank(EnemyType.BASIC, false, 300, 60, new java.util.Random(2));
        Bullet a = fire(ctx, shooterA, 200, 300, Direction.DOWN, 1);
        Bullet b = fire(ctx, shooterB, 200, 300, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, a);

        assertFalse(a.destroyed(), "敌弹互穿（E7）：不消失");
        assertFalse(b.destroyed());
        assertTrue(a.pos().y() > 300, "未命中则正常推进");
    }

    // ------------------------------------------------------------------
    // 坦克规则
    // ------------------------------------------------------------------

    @Test
    void enemyBulletWoundsPlayer() {
        GameContext ctx = world();
        PlayerTank p1 = ctx.p1();
        ctx.addActor(p1);
        p1.grantShield(0);
        EnemyTank enemy = new EnemyTank(EnemyType.BASIC, false, 100, 60, new java.util.Random(1));
        Bullet bullet = fire(ctx, enemy, 198, 495, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, bullet);

        assertEquals(2, p1.life(), "敌弹扣命");
        assertTrue(bullet.destroyed());
    }

    @Test
    void playerBulletAbsorbedByTeammateWithoutDamage() {
        GameContext ctx = world();
        PlayerTank p1 = ctx.p1();
        ctx.addActor(p1);
        p1.grantShield(0);
        Bullet bullet = fire(ctx, ctx.p2(), 198, 495, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, bullet);

        assertEquals(3, p1.life(), "玩家弹打玩家（含队友）不造成伤害");
        assertTrue(bullet.destroyed());
    }

    @Test
    void playerBulletKillsEnemyAndScores() {
        GameContext ctx = world();
        EnemyTank enemy = new EnemyTank(EnemyType.BASIC, false, 100, 100, new java.util.Random(1));
        ctx.addActor(enemy);
        Bullet bullet = fire(ctx, ctx.p1(), 93, 100, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, bullet);

        assertTrue(enemy.destroyed(), "1 型一击必死");
        assertEquals(100, ctx.p1().score(), "击杀计分 = 型号×100（B4）");
        assertTrue(bullet.destroyed());
        assertEquals(1, ctx.level().deathCount());
    }

    @Test
    void enemyBulletPassesThroughEnemyTank() {
        GameContext ctx = world();
        EnemyTank victim = new EnemyTank(EnemyType.BASIC, false, 100, 100, new java.util.Random(1));
        ctx.addActor(victim);
        EnemyTank shooter = new EnemyTank(EnemyType.BASIC, false, 100, 60, new java.util.Random(2));
        Bullet bullet = fire(ctx, shooter, 93, 100, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, bullet);

        assertFalse(bullet.destroyed(), "敌弹穿过敌坦克（E7）");
        assertFalse(victim.destroyed());
        assertTrue(bullet.pos().y() > 100);
    }

    @Test
    void wallStopsBulletBeforeTank() {
        GameContext ctx = world();
        BrickWall wall = new BrickWall(100, 100, BrickWall.FULL_BITS);
        ctx.addActor(wall);
        EnemyTank enemy = new EnemyTank(EnemyType.BASIC, false, 100, 110, new java.util.Random(1));
        ctx.addActor(enemy);
        Bullet bullet = fire(ctx, ctx.p1(), 93, 100, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, bullet);

        assertTrue(bullet.destroyed());
        assertTrue(wall.shapeBits() != BrickWall.FULL_BITS, "墙先结算");
        assertFalse(enemy.destroyed(), "命中即终止：同帧不穿墙杀坦克");
    }

    // ------------------------------------------------------------------
    // 基地与护甲（护甲护基地 / 命中即终止）
    // ------------------------------------------------------------------

    @Test
    void intactArmorColumnStopsBulletBeforeBase() {
        GameContext ctx = world();
        // 下左护甲用完整位图：其右列象限（与基地 border 重叠）存活
        Base base = installBase(ctx, BrickWall.FULL_BITS);
        // 坦克列 x=248 的下行弹：弹盒 [246,251)×[487,500)，同时与护甲存活象限、基地重叠
        Bullet bullet = fire(ctx, ctx.p1(), 248, 492, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, bullet);

        assertTrue(bullet.destroyed(), "护甲墙命中后子弹消失");
        assertFalse(base.killed(), "命中即终止：护甲未破时基地不得同帧被毁");
        assertFalse(ctx.consumeGameOverRequest(), "不得误触发 GameOver");
        assertTrue(ctx.base().armor().isEmpty() || baseWallAfter(ctx) != null,
                "护甲墙仍在舞台");
    }

    @Test
    void wornOutArmorColumnExposesBase() {
        GameContext ctx = world();
        // 下左护甲被完全打穿（16 位全毁 → isDestroyed → 不阻挡）；
        // 注意半墙只打穿上列时，存活的下列象限（q2）仍会拦住子弹
        Base base = installBase(ctx, 0xFFFF);
        Bullet bullet = fire(ctx, ctx.p1(), 248, 492, Direction.DOWN, 1);

        CombatSystem.resolve(ctx, bullet);

        assertTrue(base.killed(), "护甲完全打穿后子弹直达基地");
        assertTrue(ctx.consumeGameOverRequest(), "基地被毁触发 GameOver");
        assertTrue(bullet.destroyed());
    }

    @Test
    void defaultArmorGeometryMatchesLegacyHalfWalls() {
        GameContext ctx = world();
        Base base = installBase(ctx, BrickWall.bitsForOrientation(GameConfig.ARMOR_O0));
        // 默认护甲为半墙：下左（248,498）朝向 2 = 左列象限存活、右列（邻基地）已毁
        BrickWall leftLower = baseArmorWall(ctx, GameConfig.ARMOR_X0, GameConfig.ARMOR_Y0);
        assertEquals(BrickWall.BITS_LEFT_HALF, leftLower.shapeBits(), "ARMOR_O0=2 → 左半砖位图");
        assertFalse(base.killed());
    }

    private static BrickWall baseArmorWall(GameContext ctx, int x, int y) {
        List<com.tankbattle.server.entity.Actor> actors = ctx.actors();
        for (com.tankbattle.server.entity.Actor actor : actors) {
            if (actor instanceof BrickWall wall && wall.pos().x() == x && wall.pos().y() == y) {
                return wall;
            }
        }
        return null;
    }

    private static BrickWall baseWallAfter(GameContext ctx) {
        return baseArmorWall(ctx, GameConfig.ARMOR_X0, GameConfig.ARMOR_Y0);
    }
}

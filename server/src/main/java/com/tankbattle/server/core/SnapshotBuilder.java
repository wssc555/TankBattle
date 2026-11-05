package com.tankbattle.server.core;

import com.tankbattle.server.entity.Actor;
import com.tankbattle.server.entity.Base;
import com.tankbattle.server.entity.Bomb;
import com.tankbattle.server.entity.BrickWall;
import com.tankbattle.server.entity.Bullet;
import com.tankbattle.server.entity.EnemyTank;
import com.tankbattle.server.entity.Grass;
import com.tankbattle.server.entity.PlayerTank;
import com.tankbattle.server.entity.PowerUp;
import com.tankbattle.server.entity.River;
import com.tankbattle.server.entity.SteelWall;
import com.tankbattle.server.entity.Wall;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@link GameContext} 抽取 {@link Snapshot}（主循环第 6 步）。
 */
public final class SnapshotBuilder {

    private SnapshotBuilder() {
    }

    public static Snapshot build(GameContext ctx) {
        List<Snapshot.TankView> tanks = new ArrayList<>(16);
        List<Snapshot.BulletView> bullets = new ArrayList<>(32);
        List<Snapshot.EffectView> effects = new ArrayList<>(16);
        List<Snapshot.WallView> walls = new ArrayList<>(256);
        List<Snapshot.RiverView> rivers = new ArrayList<>(32);
        List<Snapshot.PickupView> pickups = new ArrayList<>(8);

        int flow = ctx.gameFlow();
        for (Actor actor : ctx.actors()) {
            if (actor.destroyed()) {
                continue;
            }
            switch (actor) {
                case PlayerTank tank -> tanks.add(new Snapshot.TankView(
                        tank.pos().x(), tank.pos().y(), tank.textureIndex(flow), tank.invulnerable(), true));
                case EnemyTank tank -> tanks.add(new Snapshot.TankView(
                        tank.pos().x(), tank.pos().y(), tank.textureIndex(flow), false, false));
                case Bullet bullet -> bullets.add(new Snapshot.BulletView(
                        bullet.pos().x(), bullet.pos().y(), bullet.direction().code()));
                case Bomb bomb -> effects.add(new Snapshot.EffectView(
                        bomb.pos().x(), bomb.pos().y(), bomb.size()));
                case BrickWall wall -> walls.add(new Snapshot.WallView(
                        wall.pos().x(), wall.pos().y(), true, wall.shapeBits()));
                case SteelWall wall -> walls.add(new Snapshot.WallView(
                        wall.pos().x(), wall.pos().y(), false, wall.shapeBits()));
                case River river -> rivers.add(new Snapshot.RiverView(
                        river.pos().x(), river.pos().y()));
                case PowerUp pickup -> pickups.add(new Snapshot.PickupView(
                        pickup.pos().x(), pickup.pos().y(), pickup.textureIndex()));
                default -> {
                    // 基地单独处理
                }
            }
        }

        List<Snapshot.GrassView> decor = new ArrayList<>(ctx.decor().size());
        for (Grass grass : ctx.decor()) {
            decor.add(new Snapshot.GrassView(grass.pos().x(), grass.pos().y()));
        }

        Snapshot.BaseView baseView = null;
        Base base = ctx.base();
        if (base != null) {
            baseView = new Snapshot.BaseView(base.pos().x(), base.pos().y(), base.killed());
        }

        PlayerTank p1 = ctx.p1();
        PlayerTank p2 = ctx.p2();

        return new Snapshot(
                flow,
                ctx.phase(),
                ctx.level().currentLevel(),
                ctx.level().enemyLeft(),
                ctx.level().winningCount(),
                tanks,
                bullets,
                effects,
                walls,
                rivers,
                pickups,
                decor,
                baseView,
                ctx.messages().view(),
                p1 == null ? 0 : p1.life(),
                p1 == null ? 0 : p1.score(),
                p2 == null ? 0 : p2.life(),
                p2 == null ? 0 : p2.score(),
                ctx.phase() == Phase.GAME_OVER_VOTE);
    }

    /**
     * 墙体是否为砖墙（供渲染与同步共用）。
     */
    public static boolean isBrick(Wall wall) {
        return wall instanceof BrickWall;
    }
}

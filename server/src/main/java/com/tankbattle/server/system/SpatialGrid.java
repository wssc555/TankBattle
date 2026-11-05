package com.tankbattle.server.system;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.entity.Actor;
import com.tankbattle.server.entity.Wall;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 20×20 空间哈希网格。碰撞只检查本格与邻格。
 * <p>
 * 墙体进静态索引（关卡加载/护甲切换时重建），动态实体进动态索引并<b>增量维护</b>：
 * 坦克或子弹每次位移后调用 {@link #refresh(Actor)}，因此索引始终与真实坐标一致，
 * 不会因为"帧内移动"漏判碰撞（行为等价于原全表扫描）。
 */
public final class SpatialGrid {

    private static final int COLS = ServerConfig.SPATIAL_COLS;
    private static final int ROWS = ServerConfig.SPATIAL_ROWS;

    private final List<List<Actor>> staticCells = createCells();
    private final List<List<Actor>> dynamicCells = createCells();

    private final Map<Actor, int[]> tracked = new IdentityHashMap<>();

    private static List<List<Actor>> createCells() {
        List<List<Actor>> cells = new ArrayList<>(COLS * ROWS);
        for (int i = 0; i < COLS * ROWS; i++) {
            cells.add(new ArrayList<>(4));
        }
        return cells;
    }

    /**
     * 重建静态索引（墙体）。
     */
    public void rebuildStatic(List<Actor> actors) {
        for (List<Actor> cell : staticCells) {
            cell.clear();
        }
        for (Actor actor : actors) {
            if (actor instanceof Wall && !actor.destroyed()) {
                index(staticCells, actor);
            }
        }
    }

    /**
     * 重建动态索引（坦克、子弹、道具、爆炸）。
     */
    public void rebuildDynamic(List<Actor> actors) {
        clearDynamic();
        for (Actor actor : actors) {
            if (!(actor instanceof Wall) && !actor.destroyed()) {
                index(dynamicCells, actor);
            }
        }
    }

    private void clearDynamic() {
        for (List<Actor> cell : dynamicCells) {
            cell.clear();
        }
        tracked.clear();
    }

    /**
     * 新实体入索引。
     */
    public void track(Actor actor) {
        if (actor.destroyed()) {
            return;
        }
        if (actor instanceof Wall) {
            index(staticCells, actor);
        } else {
            index(dynamicCells, actor);
        }
    }

    /**
     * 实体位置变化后刷新所属格。
     */
    public void refresh(Actor actor) {
        if (actor instanceof Wall) {
            return;
        }
        int[] previous = tracked.remove(actor);
        if (previous != null) {
            for (int cell : previous) {
                dynamicCells.get(cell).remove(actor);
            }
        }
        if (!actor.destroyed()) {
            index(dynamicCells, actor);
        }
    }

    /**
     * 实体移除。
     */
    public void untrack(Actor actor) {
        int[] previous = tracked.remove(actor);
        if (previous != null) {
            for (int cell : previous) {
                dynamicCells.get(cell).remove(actor);
            }
            return;
        }
        if (actor instanceof Wall) {
            for (List<Actor> cell : staticCells) {
                cell.remove(actor);
            }
        }
    }

    public void clear() {
        for (List<Actor> cell : staticCells) {
            cell.clear();
        }
        clearDynamic();
    }

    private void index(List<List<Actor>> cells, Actor actor) {
        Rectangle box = actor.border();
        if (box.width <= 0 || box.height <= 0) {
            return;
        }
        int c0 = colOf(box.x);
        int c1 = colOf(box.x + box.width - 1);
        int r0 = rowOf(box.y);
        int r1 = rowOf(box.y + box.height - 1);
        int[] ids = new int[(c1 - c0 + 1) * (r1 - r0 + 1)];
        int k = 0;
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                int id = r * COLS + c;
                cells.get(id).add(actor);
                ids[k++] = id;
            }
        }
        if (cells == dynamicCells) {
            tracked.put(actor, ids);
        }
    }

    /**
     * 查询与给定矩形可能相交的实体（静态 + 动态），去重并按格序稳定返回。
     *
     * @param area    查询区域
     * @param exclude 需要排除的实体（通常是自身），可为 {@code null}
     */
    public List<Actor> query(Rectangle area, Actor exclude) {
        List<Actor> out = new ArrayList<>(16);
        if (area.width <= 0 || area.height <= 0) {
            return out;
        }
        int c0 = colOf(area.x);
        int c1 = colOf(area.x + area.width - 1);
        int r0 = rowOf(area.y);
        int r1 = rowOf(area.y + area.height - 1);
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                int idx = r * COLS + c;
                collect(staticCells.get(idx), exclude, out);
                collect(dynamicCells.get(idx), exclude, out);
            }
        }
        return out;
    }

    private static void collect(List<Actor> cell, Actor exclude, List<Actor> out) {
        for (Actor actor : cell) {
            if (actor == exclude || actor.destroyed()) {
                continue;
            }
            if (!out.contains(actor)) {
                out.add(actor);
            }
        }
    }

    /**
     * 列钳制：越界坐标收敛到首/末列（行列数一旦分拆也不会互相越界）。
     */
    private static int colOf(int value) {
        int cell = Math.floorDiv(value - GameConfig.MAP_ORIGIN, GameConfig.GRID);
        if (cell < 0) {
            return 0;
        }
        if (cell >= COLS) {
            return COLS - 1;
        }
        return cell;
    }

    /**
     * 行钳制：与列钳制对称，各自使用 ROWS/COLS。
     */
    private static int rowOf(int value) {
        int cell = Math.floorDiv(value - GameConfig.MAP_ORIGIN, GameConfig.GRID);
        if (cell < 0) {
            return 0;
        }
        if (cell >= ROWS) {
            return ROWS - 1;
        }
        return cell;
    }

    public static int rows() {
        return ROWS;
    }

    public static int cols() {
        return COLS;
    }
}
